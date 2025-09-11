package com.gree.service;

import com.gree.airconditioner.dto.api.DeviceStatusDto;
import com.gree.controller.AcUnitController;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class DeviceStatusUpdateService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusUpdateService.class);

    // Map of device IP/ID to their UI controllers
    private final Map<String, AcUnitController> deviceControllers = new ConcurrentHashMap<>();
    
    // Map of device IP/ID to their status update listeners
    private final Map<String, Consumer<DeviceStatusDto>> statusListeners = new ConcurrentHashMap<>();
    
    /**
     * Register a UI controller for a specific device
     */
    public void registerDeviceController(String deviceId, AcUnitController controller) {
        log.info("Registering UI controller for device: {}", deviceId);
        deviceControllers.put(deviceId, controller);
        
        // Create a status update listener for this device
        Consumer<DeviceStatusDto> listener = status -> {
            log.debug("Received status update for device {}: Power={}, Temp={}", 
                     deviceId, status.isPower(), status.getTemperature());
            
            // Update UI on JavaFX Application Thread
            Platform.runLater(() -> {
                try {
                    controller.mapStatusInfoToUnit(status);
                    log.debug("UI updated for device: {}", deviceId);
                } catch (Exception e) {
                    log.error("Error updating UI for device {}: {}", deviceId, e.getMessage());
                }
            });
        };
        
        statusListeners.put(deviceId, listener);
    }
    
    /**
     * Unregister a device controller
     */
    public void unregisterDeviceController(String deviceId) {
        log.info("Unregistering UI controller for device: {}", deviceId);
        deviceControllers.remove(deviceId);
        statusListeners.remove(deviceId);
    }
    
    /**
     * Get the status update listener for a device
     */
    public Consumer<DeviceStatusDto> getStatusListener(String deviceId) {
        return statusListeners.get(deviceId);
    }
    
    /**
     * Manually update device status (for testing or manual refresh)
     */
    public void updateDeviceStatus(String deviceId, DeviceStatusDto status) {
        Consumer<DeviceStatusDto> listener = statusListeners.get(deviceId);
        if (listener != null) {
            listener.accept(status);
        } else {
            log.warn("No listener found for device: {}", deviceId);
        }
    }
    
    /**
     * Get all registered device IDs
     */
    public java.util.Set<String> getRegisteredDevices() {
        return deviceControllers.keySet();
    }
    
    /**
     * Check if a device is registered
     */
    public boolean isDeviceRegistered(String deviceId) {
        return deviceControllers.containsKey(deviceId);
    }
}
