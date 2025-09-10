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

import java.io.IOException;

public class AcUnitController {

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
    private RadioButton fanMedium;
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
        fanMedium.setToggleGroup(fanGroup);
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
        if (deviceStatus == null) return;
        this.tempLabel.setText(deviceStatus.getTemperature().toString());
        this.powerButton.setText(deviceStatus.isPower() ? "On" : "Off");
        this.healthMode.setSelected(deviceStatus.isHealth());

        if (deviceStatus.getMode() != null) {
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
            }
        }

        if (deviceStatus.getFanSpeed() != null) {
            switch (deviceStatus.getFanSpeed().toLowerCase()) {
                case "auto":
                    fanAuto.setSelected(true);
                    break;
                case "low":
                    fanLow.setSelected(true);
                    break;
                case "medium":
                    fanMedium.setSelected(true);
                    break;
                case "high":
                    fanHigh.setSelected(true);
                    break;
            }
        }


    }
}
