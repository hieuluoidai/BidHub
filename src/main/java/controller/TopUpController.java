package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import model.manager.AppState;
import model.user.User;

/**
 * Điều khiển dialog Nạp tiền.
 *
 * Workflow:
 *   1. User nhập số tiền → click "Nạp ngay"
 *   2. Validate amount (số dương, không quá lớn)
 *   3. Gửi "TOPUP:<userId>:<amount>" lên server
 *   4. Hiện ProgressIndicator + disable nút trong khi chờ
 *   5. Server xử lý 1.5s rồi trả về "TOPUP_OK:<newBalance>" hoặc "TOPUP_FAILED:<reason>"
 *   6. Cập nhật balance trong currentUser → callback về dashboard để refresh hiển thị
 */
public class TopUpController {

    @FXML private Label             lblCurrentBalance;
    @FXML private TextField         textAmount;
    @FXML private Label             lblError;
    @FXML private Button            btnTopUp;
    @FXML private Button            btnCancel;
    @FXML private ProgressIndicator progressIndicator;

    /** Callback cho dashboard cập nhật label balance sau khi nạp xong. */
    private Runnable onTopUpSuccess;

    @FXML
    public void initialize() {
        User user = AppState.getInstance().getCurrentUser();
        if (user != null) {
            lblCurrentBalance.setText(String.format("$%,.2f", user.getBalance()));
        }
        if (progressIndicator != null) {
            progressIndicator.setVisible(false);
            progressIndicator.setManaged(false);
        }

        // Quick-fill: gõ Enter để nạp ngay
        textAmount.setOnAction(e -> handleTopUp());
    }

    public void setOnTopUpSuccess(Runnable callback) {
        this.onTopUpSuccess = callback;
    }

    @FXML
    void handleTopUp() {
        clearError();
        String input = textAmount.getText() == null ? "" : textAmount.getText().trim();

        if (input.isEmpty()) {
            showError("Vui lòng nhập số tiền cần nạp");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ");
            return;
        }

        if (amount <= 0) {
            showError("Số tiền phải lớn hơn 0");
            return;
        }
        if (amount > 1_000_000_000) {
            showError("Số tiền vượt quá giới hạn ($1B)");
            return;
        }

        User user = AppState.getInstance().getCurrentUser();
        if (user == null) {
            showError("Phiên đăng nhập đã hết hạn");
            return;
        }

        // Bắt đầu loading state
        setLoading(true);

        // Đăng ký callback nhận response từ server
        AppState.getInstance().getClient().setStringMessageCallback(this::handleServerResponse);

        // Gửi lệnh
        String command = "TOPUP:" + user.getUserId() + ":" + amount;
        AppState.getInstance().getClient().send(command);
    }

    /**
     * Xử lý response từ server (đã ở trên FX thread sẵn).
     */
    private void handleServerResponse(String msg) {
        // Bỏ qua các message không phải của topup
        if (!msg.startsWith("TOPUP_OK") && !msg.startsWith("TOPUP_FAILED")) {
            return;
        }

        // Hủy đăng ký để không nhận thêm
        AppState.getInstance().getClient().setStringMessageCallback(null);
        setLoading(false);

        if (msg.startsWith("TOPUP_OK:")) {
            // Format: TOPUP_OK:<newBalance>
            try {
                double newBalance = Double.parseDouble(msg.substring("TOPUP_OK:".length()));
                User user = AppState.getInstance().getCurrentUser();
                if (user != null) user.setBalance(newBalance);

                // Notify dashboard refresh
                if (onTopUpSuccess != null) onTopUpSuccess.run();

                // Đóng dialog
                close();

                // Popup thông báo (Dùng AlertHelper thay vì Alert mặc định)
                utils.AlertHelper.show(
                        utils.AlertHelper.Type.SUCCESS, 
                        "Nạp tiền thành công", 
                        String.format("Bạn đã nạp tiền thành công.\nSố dư hiện tại: $%,.2f", newBalance)
                );
            } catch (NumberFormatException e) {
                showError("Server trả về dữ liệu không hợp lệ");
            }
        } else {
            // TOPUP_FAILED:<reason>
            String reason = msg.substring("TOPUP_FAILED:".length()).trim();
            showError("Nạp thất bại: " + reason);
        }
    }

    @FXML
    void handleCancel() {
        AppState.getInstance().getClient().setStringMessageCallback(null);
        close();
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            btnTopUp.setDisable(loading);
            btnCancel.setDisable(loading);
            textAmount.setDisable(loading);
            if (progressIndicator != null) {
                progressIndicator.setVisible(loading);
                progressIndicator.setManaged(loading);
            }
        });
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setTextFill(Color.web("#EF4444"));
    }

    private void clearError() {
        lblError.setText("");
    }

    private void close() {
        Platform.runLater(() -> {
            if (btnCancel != null && btnCancel.getScene() != null) {
                Stage stage = (Stage) btnCancel.getScene().getWindow();
                stage.close();
            }
        });
    }
}