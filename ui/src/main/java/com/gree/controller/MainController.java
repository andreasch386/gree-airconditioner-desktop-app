package com.gree.controller;

import com.gree.airconditioner.dto.api.ApiResponse;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import com.gree.airconditioner.dto.api.DeviceStatusDto;
import com.gree.assist.GetDevicesAssist;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Controller
public class MainController {

    private final GetDevicesAssist getDevicesAssist;

    @FXML
    private VBox rootContainer;

    @FXML
    private ProgressIndicator loading;

    public MainController(GetDevicesAssist getDevicesAssist) {
        this.getDevicesAssist = getDevicesAssist;
    }

    @FXML
    private void initialize() {
        loading.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        loading.setVisible(true);

        // Step 1: Discover devices in background
        CompletableFuture
                .supplyAsync(() -> getDevicesAssist.discoverDevicesInLan())
                .thenAccept(devices -> {
                    devices.forEach(device -> {
                        // Step 2: Add UI node immediately on FX thread
                        Platform.runLater(() -> {
                            AcUnitController controller = addDevice(device);

                            // Step 3: Fill device info asynchronously (with retries)
                            fillBasicDeviceInfo(controller, device)
                                    .thenAccept(enriched -> {
                                        // Step 4: Update UI with enriched info on FX thread
                                        Platform.runLater(() -> {
                                            controller.mapStatusInfoToUnit(enriched);
                                        });
                                    })
                                    .exceptionally(ex -> {
                                        ex.printStackTrace();
                                        return null;
                                    });
                        });
                    });
                })
                .whenComplete((res, ex) -> {
                    Platform.runLater(() -> {
                        loading.setVisible(false);
                        if (ex != null) {
                            ex.printStackTrace();
                        }
                    });
                });
    }

    private CompletableFuture<DeviceStatusDto> fillBasicDeviceInfo(AcUnitController acUnitController,
                                                                   DeviceInfoDto deviceInfo) {
        return attemptConnection(deviceInfo, 0);
    }


    private CompletableFuture<DeviceStatusDto> attemptConnection(DeviceInfoDto deviceInfo, int attempt) {
        int maxRetries = 1; // or Integer.MAX_VALUE for infinite loop
        Duration delay = Duration.ofSeconds(2);

        return getDevicesAssist.connect(deviceInfo)
                .thenCompose(connectResp -> getDevicesAssist.getDeviceStatus(deviceInfo))
                .thenCompose(statusResp -> {
                    if (statusResp.getStatusCode().is2xxSuccessful()) {
                        // ✅ Got OK status
                        DeviceStatusDto status = (DeviceStatusDto) statusResp.getBody().getData();
                        return CompletableFuture.completedFuture(status);
                    } else if (attempt < maxRetries) {
                        // 🔁 Retry after delay
                        CompletableFuture<DeviceStatusDto> retryFuture = new CompletableFuture<>();
                        CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
                                .execute(() -> {
                                    attemptConnection(deviceInfo, attempt + 1)
                                            .whenComplete((res, ex) -> {
                                                if (ex != null) retryFuture.completeExceptionally(ex);
                                                else retryFuture.complete(res);
                                            });
                                });
                        return retryFuture;
                    } else {
                        return CompletableFuture.failedFuture(
                                new RuntimeException("Max retries reached for device " + deviceInfo.getMacAddress())
                        );
                    }
                });
    }


    private AcUnitController addDevice(DeviceInfoDto device) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/ac-unit.fxml"));
            VBox groupBox = loader.load();

            AcUnitController controller = loader.getController();

            controller.mapBasicInfoToUnit(device);

            rootContainer.getChildren().add(groupBox);

            return controller;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
