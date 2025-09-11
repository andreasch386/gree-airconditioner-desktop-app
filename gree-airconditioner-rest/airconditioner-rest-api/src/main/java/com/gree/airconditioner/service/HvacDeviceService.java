package com.gree.airconditioner.service;

import com.gree.airconditioner.dto.api.DeviceControlDto;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import com.gree.airconditioner.dto.api.DeviceStatusDto;
import com.gree.airconditioner.exceptions.HvacDeviceException;
import com.gree.hvac.GreeHvac;
import com.gree.hvac.client.HvacClient;
import com.gree.hvac.client.HvacClientOptions;
import com.gree.hvac.dto.DeviceControl;
import com.gree.hvac.dto.DeviceInfo;
import com.gree.hvac.dto.DeviceStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Service layer that bridges REST API DTOs with GREE HVAC library */
@Slf4j
@Service
public class HvacDeviceService {

  private final Map<String, HvacClient> connectedClients = new ConcurrentHashMap<>();
  private final Map<String, DeviceInfo> discoveredDevices = new ConcurrentHashMap<>();

  // Configuration constants for retry logic
  private static final int MAX_STATUS_RETRIES = 3;
  private static final int MAX_RECONNECT_RETRIES = 2;
  private static final long RETRY_DELAY_MS = 500;
  private static final long RECONNECT_DELAY_MS = 1000;
  private static final long CONNECTION_STABILIZATION_DELAY_MS = 1500;

  /** Discover GREE devices on the network */
  public CompletableFuture<List<DeviceInfoDto>> discoverDevices() {
    return GreeHvac.discoverDevices()
        .thenApply(
            devices -> {
              // Cache discovered devices
              devices.forEach(device -> discoveredDevices.put(device.getId(), device));

              // Convert to API DTOs
              return devices.stream().map(this::convertToApiDto).collect(Collectors.toList());
            });
  }

  /** Get all discovered devices */
  public List<DeviceInfoDto> getDevices() {
    return discoveredDevices.values().stream()
        .map(this::convertToApiDto)
        .collect(Collectors.toList());
  }

  /** Connect to a specific device with improved reliability settings */
  public CompletableFuture<Boolean> connectToDevice(String deviceId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            if (connectedClients.containsKey(deviceId)) {
              HvacClient existingClient = connectedClients.get(deviceId);
              if (existingClient.isConnected()) {
                log.info("Device {} is already connected", deviceId);
                return true;
              } else {
                // Remove stale client
                log.info("Removing stale connection for device {}", deviceId);
                connectedClients.remove(deviceId);
                try {
                  existingClient.shutdown();
                } catch (Exception e) {
                  log.warn(
                      "Error shutting down stale client for device {}: {}",
                      deviceId,
                      e.getMessage());
                }
              }
            }

            DeviceInfo deviceInfo = discoveredDevices.get(deviceId);
            if (deviceInfo == null) {
              log.error("Device {} not found in discovered devices", deviceId);
              return false;
            }

            log.info("Connecting to device: {}", deviceId);

            // Create client with improved timeout settings for reliability
            HvacClient client =
                GreeHvac.createClient(
                    new HvacClientOptions(deviceInfo.getIpAddress())
                        .setAutoConnect(false)
                        .setPoll(
                            false) // Disable auto-polling to prevent conflicts with manual status
                        // requests
                        .setPollingTimeout(10000) // Increase timeout to 10 seconds
                        .setConnectTimeout(8000)); // Increase connect timeout

            // Setup event listeners
            client.onConnect(
                () -> {
                  log.info("Successfully connected to device: {}", deviceId);
                  deviceInfo.setConnected(true);
                  deviceInfo.setStatus("Connected");
                });

            client.onDisconnect(
                () -> {
                  log.info("Disconnected from device: {}", deviceId);
                  deviceInfo.setConnected(false);
                  deviceInfo.setStatus("Disconnected");
                  connectedClients.remove(deviceId);
                });

            client.onError(
                error -> log.error("Error from device {}: {}", deviceId, error.getMessage()));

            client.onNoResponse(
                () -> {
                  log.warn("No response from device {}, connection may be unstable", deviceId);
                  // Don't immediately remove the client, just log the issue
                });

            // Connect to the device
            client.connect().get();
            connectedClients.put(deviceId, client);

            log.info("Device {} connected successfully", deviceId);
            return true;

          } catch (Exception e) {
            log.error("Failed to connect to device {}: {}", deviceId, e.getMessage());
            return false;
          }
        });
  }

  /** Disconnect from a specific device */
  public CompletableFuture<Boolean> disconnectFromDevice(String deviceId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            HvacClient client = connectedClients.get(deviceId);
            if (client == null) {
              log.warn("Device {} is not connected", deviceId);
              return false;
            }

            log.info("Disconnecting from device: {}", deviceId);
            client.disconnect().get();
            client.shutdown();
            connectedClients.remove(deviceId);

            DeviceInfo deviceInfo = discoveredDevices.get(deviceId);
            if (deviceInfo != null) {
              deviceInfo.setConnected(false);
              deviceInfo.setStatus("Disconnected");
            }

            return true;

          } catch (Exception e) {
            log.error("Failed to disconnect from device {}: {}", deviceId, e.getMessage());
            return false;
          }
        });
  }

  /** Ensure device connection is healthy before performing operations */
  private CompletableFuture<HvacClient> ensureHealthyConnection(String deviceId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            HvacClient client = connectedClients.get(deviceId);
            if (client == null) {
              throw new HvacDeviceException("Device " + deviceId + " is not connected");
            }

            // Check if client is still connected
            if (!client.isConnected()) {
              log.info("Device {} connection lost, attempting to reconnect", deviceId);

              int attempts = 0;
              while (attempts < MAX_RECONNECT_RETRIES) {
                try {
                  // Try to reconnect
                  client.connect().get();

                  // Wait for the connection to stabilize
                  Thread.sleep(CONNECTION_STABILIZATION_DELAY_MS);

                  if (client.isConnected()) {
                    log.info("Successfully reconnected to device {}", deviceId);
                    return client;
                  }
                } catch (Exception e) {
                  log.warn(
                      "Reconnection attempt {} failed for device {}: {}",
                      attempts + 1,
                      deviceId,
                      e.getMessage());
                }

                attempts++;
                if (attempts < MAX_RECONNECT_RETRIES) {
                  try {
                    Thread.sleep(RECONNECT_DELAY_MS * attempts); // Exponential backoff
                  } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new HvacDeviceException("Connection health check interrupted", ie);
                  }
                }
              }

              throw new HvacDeviceException(
                  "Failed to reconnect to device "
                      + deviceId
                      + " after "
                      + MAX_RECONNECT_RETRIES
                      + " attempts");
            }

            return client;

          } catch (Exception e) {
            log.error(
                "Failed to ensure healthy connection for device {}: {}", deviceId, e.getMessage());
            throw new HvacDeviceException("Connection health check failed: " + e.getMessage(), e);
          }
        });
  }

  /** Check if status has meaningful data */
  private boolean isStatusValid(DeviceStatus status) {
    return status != null
        && (status.getPower() != null
            || status.getTemperature() != null
            || status.getMode() != null
            || status.getCurrentTemperature() != null);
  }

  /** Get current status of a device with robust retry logic and connection health checks */
  public CompletableFuture<DeviceStatusDto> getDeviceStatus(String deviceId) {
    return ensureHealthyConnection(deviceId)
        .thenCompose(
            client ->
                CompletableFuture.supplyAsync(
                    () -> {
                      int attempt = 0;
                      Exception lastException = null;

                      while (attempt < MAX_STATUS_RETRIES) {
                        try {
                          log.debug(
                              "Getting status for device {} (attempt {})", deviceId, attempt + 1);

                          DeviceStatus status = client.getStatus();

                          if (isStatusValid(status)) {
                            log.debug(
                                "Successfully retrieved valid status for device {} on attempt {}",
                                deviceId,
                                attempt + 1);
                            return convertToApiDto(status);
                          }

                          log.warn(
                              "Received invalid/empty status for device {}, attempt {}",
                              deviceId,
                              attempt + 1);

                        } catch (Exception e) {
                          lastException = e;
                          log.warn(
                              "Status request failed for device {} (attempt {}): {}",
                              deviceId,
                              attempt + 1,
                              e.getMessage());
                        }

                        attempt++;

                        if (attempt < MAX_STATUS_RETRIES) {
                          try {
                            // Exponential backoff with jitter
                            long delay =
                                RETRY_DELAY_MS * (1L << (attempt - 1))
                                    + (long) (Math.random() * 100);
                            Thread.sleep(delay);
                          } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new HvacDeviceException("Status request interrupted", ie);
                          }
                        }
                      }

                      // If all attempts failed, throw the last exception
                      String errorMessage =
                          "Failed to get device status after " + MAX_STATUS_RETRIES + " attempts";
                      if (lastException != null) {
                        throw new HvacDeviceException(
                            errorMessage + ": " + lastException.getMessage(), lastException);
                      } else {
                        throw new HvacDeviceException(errorMessage + ": Invalid status received");
                      }
                    }));
  }

  /** Control device properties with connection health check */
  public CompletableFuture<Boolean> controlDevice(String deviceId, DeviceControlDto controlDto) {
    return ensureHealthyConnection(deviceId)
        .thenCompose(
            client ->
                CompletableFuture.supplyAsync(
                    () -> {
                      try {
                        log.info("Controlling device {}: {}", deviceId, controlDto);

                        DeviceControl control = convertFromApiDto(controlDto);
                        client.control(control).get();

                        log.info("Successfully controlled device: {}", deviceId);
                        return true;

                      } catch (Exception e) {
                        log.error("Failed to control device {}: {}", deviceId, e.getMessage());
                        throw new HvacDeviceException(
                            "Failed to control device: " + e.getMessage(), e);
                      }
                    }));
  }

  /** Cleanup - disconnect all devices */
  public void shutdown() {
    log.info("Shutting down HVAC device service...");
    connectedClients
        .values()
        .forEach(
            client -> {
              try {
                client.disconnect().get();
                client.shutdown();
              } catch (Exception e) {
                log.error("Error disconnecting client during shutdown", e);
              }
            });
    connectedClients.clear();
    discoveredDevices.clear();
  }

  // Conversion methods between library DTOs and API DTOs
  private DeviceInfoDto convertToApiDto(DeviceInfo deviceInfo) {
    DeviceInfoDto dto = new DeviceInfoDto();
    dto.setId(deviceInfo.getId());
    dto.setName(deviceInfo.getName());
    dto.setBrand(deviceInfo.getBrand());
    dto.setModel(deviceInfo.getModel());
    dto.setVersion(deviceInfo.getVersion());
    dto.setMacAddress(deviceInfo.getMacAddress());
    dto.setIpAddress(deviceInfo.getIpAddress());
    dto.setConnected(deviceInfo.isConnected());
    dto.setStatus(deviceInfo.getStatus());
    return dto;
  }

  private DeviceStatusDto convertToApiDto(DeviceStatus status) {
    DeviceStatusDto dto = new DeviceStatusDto();
    dto.setDeviceId(status.getDeviceId());
    dto.setPower(status.getPower());
    dto.setTemperature(status.getTemperature());
    dto.setCurrentTemperature(status.getCurrentTemperature());
    dto.setMode(status.getMode());
    dto.setFanSpeed(status.getFanSpeed());
    dto.setSwingHorizontal(status.getSwingHorizontal());
    dto.setSwingVertical(status.getSwingVertical());
    dto.setLights(status.getLights());
    dto.setTurbo(status.getTurbo());
    dto.setQuiet(status.getQuiet());
    dto.setHealth(status.getHealth());
    dto.setPowerSave(status.getPowerSave());
    dto.setSleep(status.getSleep());
    return dto;
  }

  private DeviceControl convertFromApiDto(DeviceControlDto dto) {
    DeviceControl control = new DeviceControl();
    control.setPower(dto.getPower());
    control.setTemperature(dto.getTemperature());
    control.setMode(dto.getMode());
    control.setFanSpeed(dto.getFanSpeed());
    control.setSwingHorizontal(dto.getSwingHorizontal());
    control.setSwingVertical(dto.getSwingVertical());
    control.setLights(dto.getLights());
    control.setTurbo(dto.getTurbo());
    control.setQuiet(dto.getQuiet());
    control.setHealth(dto.getHealth());
    control.setPowerSave(dto.getPowerSave());
    control.setSleep(dto.getSleep());
    return control;
  }
}
