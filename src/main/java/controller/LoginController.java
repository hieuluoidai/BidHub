package controller;

import database.UserDAO;
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

    @FXML
    void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        UserDAO userDAO   = new UserDAO();
        // login() sẽ verify bằng BCrypt bên trong
        User    foundUser = userDAO.login(username, password);

        if (foundUser != null) {
            try {
                AppState.getInstance().getClient().connect("localhost", 1234);
                AppState.getInstance().setCurrentUser(foundUser);

                if (foundUser instanceof Admin) {
                    AppState.getInstance().getSceneManager().showAdminDashboard();
                } else {
                    AppState.getInstance().getSceneManager().showDashboard();
                }

            } catch (Exception e) {
                showError("Lỗi: Không thể kết nối tới Server!");
                e.printStackTrace();
            }
        } else {
            showError("Tên đăng nhập hoặc mật khẩu không chính xác!");
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
