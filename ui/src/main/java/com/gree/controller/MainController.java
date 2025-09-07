package com.gree.controller;

import com.gree.airconditioner.controller.GreeDeviceController;
import com.gree.airconditioner.dto.api.ApiResponse;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Controller
public class MainController {
    private final GreeDeviceController greeDeviceController;

    public MainController(GreeDeviceController greeDeviceController) {
        this.greeDeviceController = greeDeviceController;
    }

    @FXML
    private Label messageLabel;

    @FXML
    public void onButtonClick(ActionEvent event) {

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

            String deviceIdList = devices.stream().map(DeviceInfoDto::getId).collect(Collectors.joining("\n"));

            System.out.println("Discovered " + devices.size() + " devices");
            messageLabel.setText(deviceIdList);
            messageLabel.setStyle("-fx-text-fill: #4CAF50;");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

    }
}
