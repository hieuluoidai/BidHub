package controller;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import model.manager.AppState;
import utils.AlertHelper;

public class ChangePasswordController {

    @FXML private PasswordField txtOldPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;

    @FXML
    void handleCancel() {
        closeWindow();
    }

    @FXML
    void handleChangePassword() {
        String oldPass = txtOldPassword.getText();
        String newPass = txtNewPassword.getText();
        String confirmPass = txtConfirmPassword.getText();

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi", "Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi", "Mật khẩu mới không khớp.");
            return;
        }

        if (newPass.length() < 6) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi", "Mật khẩu mới phải có ít nhất 6 ký tự.");
            return;
        }

        // Gửi lệnh đổi mật khẩu lên Server
        String userId = AppState.getInstance().getCurrentUser().getUserId();
        String cmd = String.format("CHANGE_PASSWORD:%s:%s:%s", userId, oldPass, newPass);
        
        AppState.getInstance().getClient().setStringMessageCallback(msg -> {
            javafx.application.Platform.runLater(() -> {
                if (msg.equals("CHANGE_PASSWORD_OK")) {
                    AlertHelper.show(AlertHelper.Type.SUCCESS, "Thành công", "Đã đổi mật khẩu thành công!");
                    closeWindow();
                } else {
                    String reason = msg.startsWith("CHANGE_PASSWORD_FAILED:") ? msg.substring(23) : msg;
                    AlertHelper.show(AlertHelper.Type.ERROR, "Thất bại", "Không thể đổi mật khẩu: " + reason);
                }
            });
        });
        
        AppState.getInstance().getClient().send(cmd);
    }

    private void closeWindow() {
        Stage stage = (Stage) txtOldPassword.getScene().getWindow();
        stage.close();
    }
}
