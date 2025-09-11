package com.gree.controller;

import com.gree.airconditioner.dto.api.ApiResponse;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import com.gree.airconditioner.dto.api.DeviceStatusDto;
import com.gree.assist.GetDevicesAssist;
import com.gree.service.DeviceStatusUpdateService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Controller
@Slf4j
public class MainController {

    private final GetDevicesAssist getDevicesAssist;
    private final DeviceStatusUpdateService statusUpdateService;

    @FXML
    private VBox rootContainer;

    @FXML
    private ProgressIndicator loading;

    public MainController(GetDevicesAssist getDevicesAssist, 
                         DeviceStatusUpdateService statusUpdateService) {
        this.getDevicesAssist = getDevicesAssist;
        this.statusUpdateService = statusUpdateService;
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
                            
                            // Step 2.5: Register controller for real-time updates
                            statusUpdateService.registerDeviceController(device.getIpAddress(), controller);

                            // Step 3: Fill device info asynchronously (with retries)
                            fillBasicDeviceInfo(controller, device)
                                    .thenAccept(enriched -> {
                                        // Step 4: Update UI with enriched info on FX thread
                                        Platform.runLater(() -> {
                                            controller.mapStatusInfoToUnit(enriched);
                                            log.info("Initial status loaded for device: {}", device.getName());
                                        });
                                    })
                                    .exceptionally(ex -> {
                                        log.error("Failed to load initial status for device {}: {}", 
                                                device.getName(), ex.getMessage());
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
                        } else {
                            log.info("Device discovery and registration completed");
                        }
                    });
                });
    }

    private CompletableFuture<DeviceStatusDto> fillBasicDeviceInfo(AcUnitController acUnitController,
                                                                   DeviceInfoDto deviceInfo) {
        return attemptConnection(deviceInfo, 0);
    }

    private CompletableFuture<DeviceStatusDto> attemptConnection(DeviceInfoDto deviceInfo, int attempt) {
        int maxRetries = 1;
        Duration delay = Duration.ofSeconds(2);

        return getDevicesAssist.connect(deviceInfo)
                .thenCompose(connectResp -> {
                    log.debug("Connection response for {}: {}", deviceInfo.getName(), connectResp.getStatusCode());
                    return getDevicesAssist.getDeviceStatus(deviceInfo);
                })
                .thenCompose(statusResp -> {
                    if (statusResp.getStatusCode().is2xxSuccessful()) {
                        // ✅ Got OK status
                        DeviceStatusDto status = (DeviceStatusDto) statusResp.getBody().getData();
                        log.info("Successfully got initial status for device: {}", deviceInfo.getName());
                        return CompletableFuture.completedFuture(status);
                    } else if (attempt < maxRetries) {
                        // 🔁 Retry after delay
                        log.warn("Status request failed for device {}, retrying...", deviceInfo.getName());
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
            
            log.info("Added UI component for device: {}", device.getName());
            return controller;
            
        } catch (Exception e) {
            log.error("Failed to add device UI component: {}", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
