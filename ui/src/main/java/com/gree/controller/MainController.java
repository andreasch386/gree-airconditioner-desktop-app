package com.gree.controller;

import com.gree.airconditioner.controller.GreeDeviceController;
import com.gree.airconditioner.dto.api.ApiResponse;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javafx.concurrent.Task;

@Controller
public class MainController {

    private final GreeDeviceController greeDeviceController;

    @FXML private VBox rootContainer;

    @FXML private ProgressIndicator loading;

    public MainController(GreeDeviceController greeDeviceController) {
        this.greeDeviceController = greeDeviceController;
    }

    @FXML
    private void initialize() {
        loading.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<List<DeviceInfoDto>> task = new Task<>() {
            @Override
            protected List<DeviceInfoDto> call() {
                return getDevices();
            }
        };

        loading.visibleProperty().bind(task.runningProperty());

        task.setOnSucceeded(e -> {
            List<DeviceInfoDto> devices = task.getValue();
            devices.forEach(this::addDevice);
            loading.visibleProperty().unbind();
            loading.setVisible(false);
        });

        task.setOnFailed(e -> {
            loading.visibleProperty().unbind();
            loading.setVisible(false);
            Throwable ex = task.getException();
            if (ex != null) {
                ex.printStackTrace();
            }
        });

        Thread loaderThread = new Thread(task, "device-loader");
        loaderThread.setDaemon(true);
        loaderThread.start();
    }

    private void addDevice(DeviceInfoDto device) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/ac-unit.fxml"));
            VBox groupBox = loader.load();

            AcUnitController controller = loader.getController();
            controller.setTitle(device.getId());

            rootContainer.getChildren().add(groupBox);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<DeviceInfoDto> getDevices() {
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
}
