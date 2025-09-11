package com.gree.controller;

import ch.qos.logback.core.util.StringUtil;
import com.gree.airconditioner.dto.api.DeviceInfoDto;
import com.gree.airconditioner.dto.api.DeviceStatusDto;
import com.gree.config.Config;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AcUnitController {

    private static final Logger log = LoggerFactory.getLogger(AcUnitController.class);

    Config config;

    @Getter
    @Setter
    private String macAddress;

    @Getter
    @Setter
    private String ip;

    @FXML
    private Label unitName;
    @FXML
    private Button renameButton;
    @FXML
    private Label tempLabel;
    @FXML
    private Button decreaseTempButton;
    @FXML
    private Button increaseTempButton;
    @FXML
    private CheckBox healthMode;

    @FXML
    private RadioButton modeAuto;
    @FXML
    private RadioButton modeCool;
    @FXML
    private RadioButton modeHeat;
    @FXML
    private RadioButton modeDry;
    @FXML
    private RadioButton modeFanOnly;

    @FXML
    private RadioButton fanAuto;
    @FXML
    private RadioButton fanLow;
    @FXML
    private RadioButton fanMediumLow;
    @FXML
    private RadioButton fanMedium;
    @FXML
    private RadioButton fanMediumHigh;
    @FXML
    private RadioButton fanHigh;
    @FXML
    private Button powerButton;

    private ToggleGroup modeGroup;
    private ToggleGroup fanGroup;

    @FXML
    private void initialize() throws IOException {
        config = new Config();

        modeGroup = new ToggleGroup();
        modeAuto.setToggleGroup(modeGroup);
        modeCool.setToggleGroup(modeGroup);
        modeHeat.setToggleGroup(modeGroup);
        modeDry.setToggleGroup(modeGroup);
        modeFanOnly.setToggleGroup(modeGroup);

        fanGroup = new ToggleGroup();
        fanAuto.setToggleGroup(fanGroup);
        fanLow.setToggleGroup(fanGroup);
        fanMediumLow.setToggleGroup(fanGroup);
        fanMedium.setToggleGroup(fanGroup);
        fanMediumHigh.setToggleGroup(fanGroup);
        fanHigh.setToggleGroup(fanGroup);
    }

    public void setUnitName(String title) {
        this.unitName.setText(title);
    }


    @FXML
    public void onRenameButtonClicked(MouseEvent mouseEvent) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Rename AC Unit");

        TextField nameField = new TextField();
        nameField.setText(unitName.getText());

        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");

        saveButton.setOnAction(e -> {
            String newName = nameField.getText();
            if (!newName.isEmpty()) {
                setUnitName(newName);
                updateDeviceNameInConfig();
            }
            dialog.close();
        });

        cancelButton.setOnAction(e -> dialog.close());

        VBox dialogContent = new VBox(10);
        dialogContent.setPadding(new Insets(10));
        dialogContent.getChildren().addAll(
                new Label("Enter new name:"),
                nameField,
                new HBox(10, saveButton, cancelButton)
        );

        Scene scene = new Scene(dialogContent);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    public void updateDeviceNameInConfig() {
        config.setDeviceName(macAddress, unitName.getText());
        try {
            config.save();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Configuration Error");
            alert.setHeaderText("Failed to save configuration");
            alert.setContentText("The application was unable to save your changes. Please try again.");
            alert.showAndWait();
        }
    }

    /**
     * Map the device info to the UI controls.
     *
     * @param device {@link DeviceInfoDto}
     */
    public void mapBasicInfoToUnit(DeviceInfoDto device) {

        String deviceName = config.getDeviceName(device.getMacAddress());
        if (StringUtil.isNullOrEmpty(deviceName)) {
            deviceName = device.getName();
        }

        this.macAddress = device.getMacAddress();
        this.ip = device.getIpAddress();
        setUnitName(deviceName);
    }

    public void mapStatusInfoToUnit(DeviceStatusDto deviceStatus) {
        if (deviceStatus == null) {
            log.warn("Received null device status");
            return;
        }

        try {
            log.debug("Updating UI with status: Power={}, Temp={}, Mode={}",
                    deviceStatus.isPower(), deviceStatus.getTemperature(), deviceStatus.getMode());

            // Update temperature
            if (deviceStatus.getTemperature() != null) {
                this.tempLabel.setText(deviceStatus.getTemperature().toString() + "°C");
            }

            // Update power button

            this.powerButton.setText(deviceStatus.isPower() ? "ON" : "OFF");
            // Optional: Change button style based on power state
            if (deviceStatus.isPower()) {
                this.powerButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            } else {
                this.powerButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
            }


            // Update health mode
            this.healthMode.setSelected(deviceStatus.isHealth());

            // Update mode selection
            if (deviceStatus.getMode() != null) {
                // Clear all selections first
                modeGroup.selectToggle(null);

                switch (deviceStatus.getMode().toLowerCase()) {
                    case "auto":
                        modeAuto.setSelected(true);
                        break;
                    case "cool":
                        modeCool.setSelected(true);
                        break;
                    case "heat":
                        modeHeat.setSelected(true);
                        break;
                    case "dry":
                        modeDry.setSelected(true);
                        break;
                    case "fanonly":
                        modeFanOnly.setSelected(true);
                        break;
                    default:
                        log.warn("Unknown mode: {}", deviceStatus.getMode());
                }
            }

            // Update fan speed selection
            if (deviceStatus.getFanSpeed() != null) {
                // Clear all selections first
                fanGroup.selectToggle(null);

                switch (deviceStatus.getFanSpeed().toLowerCase()) {
                    case "auto":
                        fanAuto.setSelected(true);
                        break;
                    case "low":
                        fanLow.setSelected(true);
                        break;
                    case "mediumlow":
                        fanMediumLow.setSelected(true);
                        break;
                    case "medium":
                        fanMedium.setSelected(true);
                        break;
                    case "midiumhigh":
                        fanMediumHigh.setSelected(true);
                        break;
                    case "high":
                        fanHigh.setSelected(true);
                        break;
                    default:
                        log.warn("Unknown fan speed: {}", deviceStatus.getFanSpeed());
                }
            }

            log.debug("UI update completed successfully");

        } catch (Exception e) {
            log.error("Error updating UI with device status: {}", e.getMessage());
        }
    }
}
