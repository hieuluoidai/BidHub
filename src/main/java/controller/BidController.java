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
import java.util.function.Consumer;

/**
 * Điều khiển cửa sổ đặt giá (Bid Window).
 */
public class BidController {

    @FXML private Label labelItemName;
    @FXML private Label labelCurrentPrice;
    @FXML private Label labelError;
    @FXML private TextField textBidAmount;
    @FXML private Button cancelButton;
    @FXML private Button confirmButton;

    private Auction currentAuction;

    /**
     * Callback nhận BidResult để báo về cho màn hình cha (ItemDetailsController)
     * cập nhật UI ngay mà không cần chờ Server broadcast toàn bộ list.
     */
    private Consumer<BidResult> onBidDoneCallback;

    public void setOnBidDoneCallback(Consumer<BidResult> callback) {
        this.onBidDoneCallback = callback;
    }

    /**
     * Nhận dữ liệu phiên đấu giá từ màn hình Dashboard/Details truyền sang.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        labelItemName.setText("Sản phẩm: " + auction.getItem().getItemName());
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

            // 1. Vô hiệu hóa nút bấm để tránh gửi trùng (Race Condition phía Client)
            setLoading(true);

            // 2. Đăng ký nhận kết quả trả về từ ConcurrentBidManager
            AppState.getInstance().getClient().setBidResultCallback(this::handleBidResult);

            // 3. Gửi lệnh BID theo giao thức đồng bộ: BID:<auctionId>:<amount>:<bidderId>
            String command = String.format("BID:%s:%.2f:%s",
                    currentAuction.getAuctionId(),
                    amount,
                    currentUser.getUserId());
            
            AppState.getInstance().getClient().send(command);

        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ (phải là định dạng số)!");
        }
    }

    /**
     * Xử lý kết quả trả về từ Server (đã được bọc trong Platform.runLater từ AuctionClient).
     */
    private void handleBidResult(BidResult result) {
        // Bỏ qua nếu kết quả thuộc về phiên khác (trong trường hợp user mở nhiều cửa sổ bid)
        if (currentAuction == null || !currentAuction.getAuctionId().equals(result.getAuctionId())) {
            return;
        }

        // Gỡ callback sau khi đã nhận được phản hồi
        AppState.getInstance().getClient().setBidResultCallback(null);
        setLoading(false);

        // Báo kết quả cho các controller khác đang lắng nghe
        if (onBidDoneCallback != null) {
            onBidDoneCallback.accept(result);
        }

        switch (result.getStatus()) {
            case SUCCESS -> {
                AlertHelper.show(AlertHelper.Type.SUCCESS, "Thành công", result.getMessage());
                closeStage();
            }
            case OUTBID -> {
                // Cập nhật giá mới nhất để user biết đường bid lại ngay
                labelCurrentPrice.setText(String.format("Giá hiện tại: $%.2f", result.getCurrentPrice()));
                showError(result.getMessage());
            }
            case FAILURE -> {
                AlertHelper.show(AlertHelper.Type.ERROR, "Thất bại", result.getMessage());
                closeStage();
            }
        }
    }

    @FXML
    void handleCancel() {
        // Hủy đăng ký callback nếu thoát ngang
        AppState.getInstance().getClient().setBidResultCallback(null);
        closeStage();
    }

    private void setLoading(boolean loading) {
        if (confirmButton != null) confirmButton.setDisable(loading);
        if (cancelButton != null) cancelButton.setDisable(loading);
    }

    private void showError(String msg) {
        labelError.setText(msg);
    }

    private void closeStage() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}