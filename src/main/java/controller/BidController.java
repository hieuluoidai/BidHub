package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.auction.Auction;
import model.auction.BidResult;
import model.manager.AppState;
import model.user.Bidder;
import model.user.User;
import utils.AlertHelper;

/**
 * Điều khiển cửa sổ Bid.
 */
public class BidController {

    @FXML private Label labelItemName;
    @FXML private Label labelCurrentPrice;
    @FXML private Label labelError;
    @FXML private TextField textBidAmount;
    @FXML private Button cancelButton;
    @FXML private Button confirmButton;

    private Auction currentAuction;

    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        labelItemName.setText("Sản phẩm: " + auction.getItemName());
        labelCurrentPrice.setText(String.format("Giá hiện tại: $%.2f", auction.getCurrentPrice()));
    }

    @FXML
    void handleConfirm() {
        labelError.setText("");

        try {
            String amountStr = textBidAmount.getText().trim();
            if (amountStr.isEmpty()) {
                showError("Vui lòng nhập số tiền!");
                return;
            }

            double amount = Double.parseDouble(amountStr);

            if (amount <= 0) {
                showError("Số tiền phải lớn hơn 0!");
                return;
            }
            if (amount <= currentAuction.getCurrentPrice()) {
                showError(String.format("Giá đặt phải cao hơn giá hiện tại ($%.2f)!",
                        currentAuction.getCurrentPrice()));
                return;
            }

            User currentUser = AppState.getInstance().getCurrentUser();
            if (!(currentUser instanceof Bidder)) {
                showError("Chỉ người mua (Bidder) mới có quyền đặt giá!");
                return;
            }

            // Disable nút trong lúc chờ phản hồi để tránh double-click
            setLoading(true);

            // Đăng ký callback nhận kết quả từ server
            AppState.getInstance().getClient().setOnBidResult(this::handleBidResult);

            // Gửi lệnh BID
            String command = "BID:"
                    + currentAuction.getAuctionId() + ":"
                    + amount + ":"
                    + currentUser.getUserId();
            AppState.getInstance().getClient().send(command);

        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ (phải là số)!");
        }
    }

    /**
     * Callback xử lý BidResult từ server (đã ở trên FX thread sẵn rồi).
     */
    private void handleBidResult(BidResult result) {
        // Bỏ qua nếu kết quả không phải của phiên này (an toàn nếu user mở nhiều bid window)
        if (currentAuction == null || !currentAuction.getAuctionId().equals(result.getAuctionId())) {
            return;
        }

        // Gỡ callback để không nhận thêm
        AppState.getInstance().getClient().setOnBidResult(null);
        setLoading(false);

        switch (result.getStatus()) {
            case SUCCESS -> {
                AlertHelper.show(AlertHelper.Type.SUCCESS,
                        "Đặt giá thành công", result.getMessage());
                closeStage();
            }
            case OUTBID -> {
                // Cập nhật giá hiển thị + giữ cửa sổ để user bid lại
                labelCurrentPrice.setText(String.format("Giá hiện tại: $%.2f",
                        result.getCurrentPrice()));
                showError(result.getMessage());
            }
            case FAILURE -> {
                AlertHelper.show(AlertHelper.Type.ERROR,
                        "Đặt giá thất bại", result.getMessage());
                closeStage();
            }
        }
    }

    @FXML
    void handleCancel() {
        // Gỡ callback nếu user hủy giữa chừng
        AppState.getInstance().getClient().setOnBidResult(null);
        Stage currentStage = (Stage) cancelButton.getScene().getWindow();
        currentStage.close();
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            if (confirmButton != null) confirmButton.setDisable(loading);
            if (cancelButton  != null) cancelButton.setDisable(loading);
        });
    }

    private void showError(String msg) {
        labelError.setText(msg);
    }

    private void closeStage() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}
