package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ConfirmPayController {
    @FXML private Label titleLabel;
    @FXML private Label amountDueLabel;
    @FXML private Label currentBalanceLabel;
    @FXML private Label resultStatusLabel;
    @FXML private Label resultAmountLabel;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private boolean isConfirmed = false;

    public void setPaymentData(double finalPrice, double currentBalance) {
        amountDueLabel.setText(String.format("$%,.2f", finalPrice));
        currentBalanceLabel.setText(String.format("$%,.2f", currentBalance));

        boolean hasEnoughBalance = currentBalance >= finalPrice;

        if (hasEnoughBalance) {
            double remainingBalance = currentBalance - finalPrice;
            resultStatusLabel.setText("Sau thanh toán, số dư còn:");
            resultAmountLabel.setText(String.format("$%,.2f", remainingBalance));
            resultAmountLabel.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;"); // Màu xanh lá
        } else {
            double missingAmount = finalPrice - currentBalance;
            titleLabel.setText("Số dư không đủ!");
            titleLabel.setStyle("-fx-text-fill: #EF4444;"); // Chữ đỏ cảnh báo
            
            resultStatusLabel.setText("Bạn cần nạp thêm:");
            resultAmountLabel.setText(String.format("$%,.2f", missingAmount));
            resultAmountLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;"); // Màu đỏ
            
            // Ẩn nút xác nhận, chỉ cho phép đóng cửa sổ
            confirmButton.setVisible(false);
            confirmButton.setManaged(false);
            cancelButton.setText("Đóng");
        }
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }

    @FXML
    void handleConfirm() {
        isConfirmed = true;
        closeWindow();
    }

    @FXML
    void handleCancel() {
        isConfirmed = false;
        closeWindow();
    }

    private void closeWindow() {
        Stage currentStage = (Stage) confirmButton.getScene().getWindow();
        currentStage.close();
    }
}