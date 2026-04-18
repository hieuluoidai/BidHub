package controller;

import java.io.IOException;
import database.UserDAO;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import model.manager.AppState;
import model.user.User;
import utils.SceneSwitcher;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    @FXML
    void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Thay mockUsers bằng query thật từ DB
        UserDAO userDAO = new UserDAO();
        User foundUser = userDAO.login(username, password);

        if (foundUser != null) {
            try {
                AppState.getInstance().getClient().connect("localhost", 1234);
                AppState.getInstance().setCurrentUser(foundUser);
                System.out.println("Logged in as: " + foundUser.getClass().getSimpleName());
                SceneSwitcher.switchScene(event, "/view/dashboard.fxml");
            } catch (Exception e) {
                messageLabel.setText("Lỗi: Không thể kết nối tới Server!");
                messageLabel.setTextFill(Color.RED);
                e.printStackTrace();
            }
        } else {
            messageLabel.setText("Invalid username or password!");
            messageLabel.setTextFill(Color.RED);
        }
    }
}