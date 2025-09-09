package com.gree.controller;

import com.gree.airconditioner.dto.api.DeviceInfoDto;
import com.gree.airconditioner.dto.api.DeviceStatusDto;
import com.gree.assist.GetDevicesAssist;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.List;

import javafx.concurrent.Task;

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
    private void initialize() throws IOException {
        loading.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<List<DeviceInfoDto>> loadingTask = new Task<>() {
            @Override
            protected List<DeviceInfoDto> call() {
                return getDevicesAssist.discoverDevicesInLan();
            }
        };

        loading.visibleProperty().bind(loadingTask.runningProperty());

        loadingTask.setOnSucceeded(e -> {
            List<DeviceInfoDto> devices = loadingTask.getValue();
            devices.forEach(x -> {
                AcUnitController acUnitController = addDevice(x);

                if (x.isConnected()) {
                    getDevicesAssist.disconnect(x);
                }
                getDevicesAssist.connect(x);

                fillBasicDeviceInfo(acUnitController, x);
            });
            loading.visibleProperty().unbind();
            loading.setVisible(false);
        });

        loadingTask.setOnFailed(e -> {
            loading.visibleProperty().unbind();
            loading.setVisible(false);
            Throwable ex = loadingTask.getException();
            if (ex != null) {
                ex.printStackTrace();
            }
        });

        Thread loaderThread = new Thread(loadingTask, "device-loader");
        loaderThread.setDaemon(true);
        loaderThread.start();
    }

    private void fillBasicDeviceInfo(AcUnitController acUnitController, DeviceInfoDto deviceInfo) {
        DeviceStatusDto deviceStatus = getDevicesAssist.getDeviceStatus(deviceInfo.getIpAddress());
        acUnitController.mapStatusInfoToUnit(deviceStatus);
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
