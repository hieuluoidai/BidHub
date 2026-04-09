package controller;

import java.io.IOException;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import utils.SceneSwitcher;

public class LoginController {
	@FXML private TextField usernameField;
	@FXML private PasswordField passwordField;
	@FXML private Label messageLabel;
	
	@FXML 
	void handleLogin(ActionEvent event) {
	    String username = usernameField.getText();
	    String password = passwordField.getText();
	    
	    if (username.equals("admin") && password.equals("uet123")) {
	        try {
	        	SceneSwitcher.changeScene(event, "/view/dashboard.fxml");
	            
	        } catch (IOException e) {
	            messageLabel.setText("Error: Couldn't find file dashboard.fxml");
	            e.printStackTrace();
	        }
	    } else {
	        messageLabel.setText("Incorrect Username or Password!");
	        messageLabel.setTextFill(Color.RED);
	    }
	}	
}
