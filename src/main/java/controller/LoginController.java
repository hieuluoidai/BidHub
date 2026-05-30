package controller;

import exception.AuthenticationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import model.manager.AppState;
import model.user.Admin;
import model.user.User;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    private static final String SERVER_HOST;
    private static final int SERVER_PORT;

    static {
        Properties props = new Properties();
        try (InputStream in = LoginController.class.getResourceAsStream("/server.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }
        SERVER_HOST = props.getProperty("server.host", "localhost");
        SERVER_PORT = Integer.parseInt(props.getProperty("server.port", "1234"));
    }

    @FXML
    void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        try {
            User foundUser = AppState.getInstance()
                    .getClient()
                    .authenticate(SERVER_HOST, SERVER_PORT, username, password);
            AppState.getInstance().setCurrentUser(foundUser);

            if (foundUser instanceof Admin) {
                AppState.getInstance().getSceneManager().showAdminDashboard();
            } else {
                AppState.getInstance().getSceneManager().showDashboard();
            }
        } catch (AuthenticationException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            showError("Loi: Khong the ket noi toi Server!");
            e.printStackTrace();
        }
    }

    @FXML
    void handleRegister() {
        AppState.getInstance().getSceneManager().showRegister();
    }

    private void showError(String msg) {
        messageLabel.setText(msg);
        messageLabel.setTextFill(Color.RED);
    }
}
