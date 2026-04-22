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
                showError(String.format("Giá đặt phải cao hơn giá hiện tại ($%.2f)!", currentAuction.getCurrentPrice()));
                return;
            }

            User currentUser = AppState.getInstance().getCurrentUser();

            if (!(currentUser instanceof Bidder)) {
                showError("Chỉ người mua (Bidder) mới có quyền đặt giá!");
                return;
            }

            // Gửi lệnh dạng String lên Server
            // Format: "BID:<auctionId>:<amount>:<bidderId>"
            String command = "BID:"
                + currentAuction.getAuctionId() + ":"
                + amount + ":"
                + currentUser.getUserId();

            AppState.getInstance().getClient().send(command);
            closeStage();

        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ (phải là số)!");
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