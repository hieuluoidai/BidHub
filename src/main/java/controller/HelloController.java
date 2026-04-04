package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HelloController {
    @FXML
    private Label messageLabel;

    @FXML
    public void handleButtonClick(ActionEvent event) {
        messageLabel.setText("Excellent!");
    }
}