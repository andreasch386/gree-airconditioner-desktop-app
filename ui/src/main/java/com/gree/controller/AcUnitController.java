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

    @FXML private Label unitName;
    @FXML private Button renameButton;
    @FXML private Label tempLabel;
    @FXML private Button decreaseTempButton;
    @FXML private Button increaseTempButton;
    @FXML private CheckBox featureA;
    @FXML private CheckBox featureB;
    @FXML private RadioButton option1;
    @FXML private RadioButton option2;
    @FXML private RadioButton option3;
    @FXML private RadioButton option4;
    @FXML private Button powerButton;

    private ToggleGroup modeGroup;

    @FXML
    private void initialize() throws IOException {
        config = new Config();

        // Make the 4 radios mutually exclusive
        modeGroup = new ToggleGroup();
        option1.setToggleGroup(modeGroup);
        option2.setToggleGroup(modeGroup);
        option3.setToggleGroup(modeGroup);
        option4.setToggleGroup(modeGroup);
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
        this.tempLabel.setText(deviceStatus.getTemperature().toString());

    }
}
