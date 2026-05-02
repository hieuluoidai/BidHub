package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.auction.Auction;
import model.manager.AppState;
import model.user.Bidder;
import model.user.User;

/**
 * Điều khiển cửa sổ Bid.
 */
public class BidController {

    @FXML private Label labelItemName;
    @FXML private Label labelCurrentPrice;
    @FXML private Label labelError;
    @FXML private TextField textBidAmount;
    @FXML private Button cancelButton;

    private Auction currentAuction;

    /**
     * Callback nhận BidResult khi bid xong (SUCCESS/OUTBID/FAILURE).
     * ItemDetailsController đăng ký để cập nhật UI ngay lập tức từ kết quả,
     * không cần chờ server broadcast.
     */
    private java.util.function.Consumer<model.auction.BidResult> onBidDoneCallback;

    public void setOnBidDoneCallback(java.util.function.Consumer<model.auction.BidResult> callback) {
        this.onBidDoneCallback = callback;
    }

    /** Giữ lại để không break code cũ */
    public void setOnBidSuccessCallback(Runnable callback) {}

    /**
     * Nhận dữ liệu phiên đấu giá từ màn hình Dashboard truyền sang để hiển thị.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        labelItemName.setText("Sản phẩm: " + auction.getItemName());
        // Dùng format %.2f để hiển thị giá đúng 2 chữ số thập phân thay vì toString mặc định
        labelCurrentPrice.setText(String.format("Giá hiện tại: $%.2f", auction.getCurrentPrice()));
    }

    /**
     * Xử lý khi người dùng nhấn nút "Xác nhận" đặt giá.
     */
    @FXML
    void handleConfirm() {
        labelError.setText("");

        try {
            String amountStr = textBidAmount.getText().trim();
            if (amountStr.isEmpty()) { showError("Vui lòng nhập số tiền!"); return; }

            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) { showError("Số tiền phải lớn hơn 0!"); return; }

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

            // Đăng ký callback nhận BidResult trước khi gửi lệnh
            AppState.getInstance().getClient().setBidResultCallback(result -> {
                handleBidResult(result);
                AppState.getInstance().getClient().setBidResultCallback(null);
            });

            String command = "BID:"
                    + currentAuction.getAuctionId() + ":"
                    + amount + ":"
                    + currentUser.getUserId();

            AppState.getInstance().getClient().send(command);
            setConfirmButtonEnabled(false); // Tránh double-click khi đang chờ

        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ (phải là số)!");
        }
    }

    private void handleBidResult(model.auction.BidResult result) {
        setConfirmButtonEnabled(true);

        switch (result.getStatus()) {
            case SUCCESS:
                closeStage();
                break;
            case OUTBID:
                labelCurrentPrice.setText(String.format(
                        "Giá hiện tại: $%.2f", result.getCurrentPrice()));
                showError("Bị vượt giá! " + result.getMessage());
                break;
            case FAILURE:
                showError(result.getMessage());
                break;
        }

        // Báo cho ItemDetailsController biết kết quả để update UI ngay
        if (onBidDoneCallback != null) {
            onBidDoneCallback.accept(result);
        }
    }

    private void setConfirmButtonEnabled(boolean enabled) {
        javafx.scene.Node confirmBtn = cancelButton.getScene().lookup("#confirmButton");
        if (confirmBtn instanceof javafx.scene.control.Button btn) {
            btn.setDisable(!enabled);
        }
    }
    /**
     * Xử lý khi nhấn nút "Hủy" -> Đóng cửa sổ.
     */
    @FXML
    void handleCancel() {
        Stage currentStage = (Stage) cancelButton.getScene().getWindow();
        currentStage.close();
    }

    /**
     * Tiện ích: Hiển thị thông báo lỗi màu đỏ lên giao diện.
     */
    private void showError(String msg) {
        labelError.setText(msg);
    }

    /**
     * Tiện ích: Đóng cửa sổ bid hiện tại.
     */
    private void closeStage() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}