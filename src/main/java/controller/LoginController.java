package controller;

import java.io.IOException;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import model.manager.SessionManager;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import utils.SceneSwitcher;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    
    private List<User> mockUsers = List.of(
    	    new Admin("AD01", "admin", "admin@vnu.edu.vn", "uet123"),
    	    new Bidder("B01", "bidder", "hieu@vnu.edu.vn", "uet456"),
    	    new Seller("S01", "seller", "seller@vnu.edu.vn", "uet789")
    	);
    
    @FXML 
    void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        User foundUser = null;
        for (User u : mockUsers) {
            if (u.getUsername().equals(username) && u.getPassword().equals(password)) {
                foundUser = u;
                break;
            }
        }

        if (foundUser != null) {
            SessionManager.getInstance().setCurrentUser(foundUser);

            System.out.println("Logged in as: " + foundUser.getClass().getSimpleName());

            SceneSwitcher.switchScene(event, "/view/dashboard.fxml");

        } else {
            messageLabel.setText("Invalid username or password!");
            messageLabel.setTextFill(Color.RED);
        }
    }
}