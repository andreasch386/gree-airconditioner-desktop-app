package com.gree.assist;

import com.gree.airconditioner.controller.GreeDeviceController;
import com.gree.airconditioner.dto.api.ApiResponse;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import com.gree.airconditioner.dto.api.DeviceStatusDto;
import com.gree.service.DeviceStatusUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

@Component
@Slf4j
public class GetDevicesAssist {

    private final GreeDeviceController greeDeviceController;
    private final DeviceStatusUpdateService statusUpdateService;

    public GetDevicesAssist(GreeDeviceController greeDeviceController, 
                           DeviceStatusUpdateService statusUpdateService) {
        this.greeDeviceController = greeDeviceController;
        this.statusUpdateService = statusUpdateService;
    }

    /**
     * This method will discover all devices in the local network. It needs to be run everytime the application starts.
     * After the app starts, the {@link #getAllDiscoveredDevices()} method can be used to get the list of devices.
     *
     * @return List of discovered devices
     */
    public List<DeviceInfoDto> discoverDevicesInLan() {
        CompletableFuture<ResponseEntity<ApiResponse<List<DeviceInfoDto>>>> discoveredDevices = greeDeviceController.discoverDevices();

        try {
            ResponseEntity<ApiResponse<List<DeviceInfoDto>>> response = discoveredDevices.get();

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to discover devices");
            }

            List<DeviceInfoDto> devices = response.getBody().getData();
            if (devices.isEmpty()) {
                throw new RuntimeException("No devices discovered");
            }

            System.out.println("Discovered " + devices.size() + " devices");

            return devices;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get all discovered devices. This method will return an empty list if no devices are discovered.
     *
     * @return List of discovered devices
     */
    public List<DeviceInfoDto> getAllDiscoveredDevices() {
        ResponseEntity<ApiResponse<List<DeviceInfoDto>>> response = greeDeviceController.getAllDevices();

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to get all devices");
        }

        if (response.getBody() == null) {
            return Collections.emptyList();
        }

        List<DeviceInfoDto> devices = response.getBody().getData();
        if (devices.isEmpty()) {
            return Collections.emptyList();
        }

        return devices;
    }

    /**
     * get device status by ip address
     *
     * @param device
     * @return
     */
    public CompletableFuture<ResponseEntity<ApiResponse<DeviceStatusDto>>> getDeviceStatus(DeviceInfoDto device) {
        return greeDeviceController.getDeviceStatus(device.getIpAddress());
    }

    /**
     * Connect to device and setup real-time status updates
     */
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> connect(DeviceInfoDto device) {
        return greeDeviceController.connectToDevice(device.getIpAddress())
            .thenCompose(response -> {
                // If connection successful, setup status update listener
                if (response.getStatusCode().is2xxSuccessful()) {
                    setupStatusUpdateListener(device);
                }
                return CompletableFuture.completedFuture(response);
            });
    }

    /**
     * Setup real-time status update listener for a device
     */
    private void setupStatusUpdateListener(DeviceInfoDto device) {
        try {
            // Get the status update listener from our service
            Consumer<DeviceStatusDto> listener = statusUpdateService.getStatusListener(device.getIpAddress());
            
            if (listener != null) {
                log.info("Setting up real-time status updates for device: {}", device.getIpAddress());
                
                // Here we would ideally have a way to register the listener with the HvacClient
                // For now, we'll use polling as a fallback
                setupStatusPolling(device, listener);
            }
        } catch (Exception e) {
            log.error("Failed to setup status listener for device {}: {}", device.getIpAddress(), e.getMessage());
        }
    }
    
    /**
     * Setup periodic status polling as a fallback for real-time updates
     */
    private void setupStatusPolling(DeviceInfoDto device, Consumer<DeviceStatusDto> listener) {
        // Poll device status every 10 seconds and notify listener of changes
        CompletableFuture.runAsync(() -> {
            DeviceStatusDto lastStatus = null;
            
            while (statusUpdateService.isDeviceRegistered(device.getIpAddress())) {
                try {
                    ResponseEntity<ApiResponse<DeviceStatusDto>> response = 
                        getDeviceStatus(device).get();
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        DeviceStatusDto currentStatus = response.getBody().getData();
                        
                        // Only notify if status changed
                        if (lastStatus == null || !statusEquals(lastStatus, currentStatus)) {
                            listener.accept(currentStatus);
                            lastStatus = currentStatus;
                        }
                    }
                    
                    Thread.sleep(10000); // Poll every 10 seconds
                    
                } catch (Exception e) {
                    log.warn("Error polling status for device {}: {}", device.getIpAddress(), e.getMessage());
                    try {
                        Thread.sleep(5000); // Wait 5 seconds before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * Compare two device statuses for equality
     */
    private boolean statusEquals(DeviceStatusDto status1, DeviceStatusDto status2) {
        if (status1 == null || status2 == null) return false;
        
        return java.util.Objects.equals(status1.isPower(), status2.isPower()) &&
               java.util.Objects.equals(status1.getTemperature(), status2.getTemperature()) &&
               java.util.Objects.equals(status1.getMode(), status2.getMode()) &&
               java.util.Objects.equals(status1.getFanSpeed(), status2.getFanSpeed()) &&
               java.util.Objects.equals(status1.isHealth(), status2.isHealth());
    }

    public CompletableFuture<ResponseEntity<ApiResponse<String>>> disconnect(DeviceInfoDto device) {
        // Unregister status updates when disconnecting
        statusUpdateService.unregisterDeviceController(device.getIpAddress());
        return greeDeviceController.disconnectFromDevice(device.getId());
    }
}
