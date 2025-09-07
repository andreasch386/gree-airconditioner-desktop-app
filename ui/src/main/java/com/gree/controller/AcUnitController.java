package com.gree.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AcUnitController {

    @FXML private Label title;
    @FXML private Label actionLabel;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private CheckBox featureA;
    @FXML private CheckBox featureB;
    @FXML private RadioButton option1;
    @FXML private RadioButton option2;
    @FXML private RadioButton option3;
    @FXML private RadioButton option4;
    @FXML private Button applyButton;

    private ToggleGroup modeGroup;

    @FXML
    private void initialize() {
        // Make the 4 radios mutually exclusive
        modeGroup = new ToggleGroup();
        option1.setToggleGroup(modeGroup);
        option2.setToggleGroup(modeGroup);
        option3.setToggleGroup(modeGroup);
        option4.setToggleGroup(modeGroup);
    }

    public void setTitle(String name) {
        title.setText("IP: " + name);
    }
}
