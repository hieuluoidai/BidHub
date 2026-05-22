package controller;

import exception.AuthenticationException;
import service.AuthService;
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

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         messageLabel;

    private static final String SERVER_HOST;
    private static final int    SERVER_PORT;

    static {
        Properties props = new Properties();
        try (InputStream in = LoginController.class
                .getResourceAsStream("/server.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // fallback to defaults below
        }
        SERVER_HOST = props.getProperty("server.host", "localhost");
        SERVER_PORT = Integer.parseInt(props.getProperty("server.port", "1234"));
    }

    @FXML
    void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        try {
            AuthService authService = new AuthService();
            User foundUser = authService.login(username, password);

            AppState.getInstance().getClient().connect(SERVER_HOST, SERVER_PORT);
            AppState.getInstance().setCurrentUser(foundUser);

            // Gửi lệnh IDENTIFY để Server gắn ID người dùng vào connection này (cực kỳ quan trọng cho real-time push)
            AppState.getInstance().getClient().send("IDENTIFY:" + foundUser.getUserId());

            if (foundUser instanceof Admin) {
                AppState.getInstance().getSceneManager().showAdminDashboard();
            } else {
                AppState.getInstance().getSceneManager().showDashboard();
            }

        } catch (AuthenticationException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            showError("Lỗi: Không thể kết nối tới Server!");
            e.printStackTrace();
        }
    }

    /**
     * Chuyển sang màn hình đăng ký tài khoản mới.
     */
    @FXML
    void handleRegister() {
        AppState.getInstance().getSceneManager().showRegister();
    }

    private void showError(String msg) {
        messageLabel.setText(msg);
        messageLabel.setTextFill(Color.RED);
    }
}