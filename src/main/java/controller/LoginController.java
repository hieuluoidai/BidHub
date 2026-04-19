package controller;

import database.UserDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import model.manager.AppState;
import model.user.User;

/**
 * Xử lý logic đăng nhập và kết nối hệ thống.
 */
public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    @FXML
    void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Xác thực thông tin từ Database MySQL
        UserDAO userDAO = new UserDAO();
        User foundUser = userDAO.login(username, password);

        if (foundUser != null) {
            try {
                // Thiết lập kết nối Socket tới Server
                AppState.getInstance().getClient().connect("localhost", 1234);
                
                // Lưu thông tin người dùng vào trạng thái toàn cục
                AppState.getInstance().setCurrentUser(foundUser);
                
                // Chuyển sang màn hình Dashboard thông qua SceneManager
                AppState.getInstance().getSceneManager().showDashboard();
                
            } catch (Exception e) {
                showError("Lỗi: Không thể kết nối tới Server!");
                e.printStackTrace();
            }
        } else {
            showError("Tên đăng nhập hoặc mật khẩu không chính xác!");
        }
    }

    private void showError(String msg) {
        messageLabel.setText(msg);
        messageLabel.setTextFill(Color.RED);
    }
}