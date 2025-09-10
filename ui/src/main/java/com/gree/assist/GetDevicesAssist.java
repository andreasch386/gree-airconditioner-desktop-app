package com.gree.assist;

import com.gree.airconditioner.controller.GreeDeviceController;
import com.gree.airconditioner.dto.api.ApiResponse;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import com.gree.airconditioner.dto.api.DeviceStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class GetDevicesAssist {

    private final GreeDeviceController greeDeviceController;

    public GetDevicesAssist(GreeDeviceController greeDeviceController) {
        this.greeDeviceController = greeDeviceController;
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

    public CompletableFuture<ResponseEntity<ApiResponse<String>>> connect(DeviceInfoDto device) {
        return greeDeviceController.connectToDevice(device.getIpAddress());
    }


    public CompletableFuture<ResponseEntity<ApiResponse<String>>> disconnect(DeviceInfoDto device) {
        return greeDeviceController.disconnectFromDevice(device.getId());
    }
}
