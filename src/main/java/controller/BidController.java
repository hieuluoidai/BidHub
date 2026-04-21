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
        labelCurrentPrice.setText("Giá hiện tại: $" + auction.getCurrentPrice());
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
            User currentUser = AppState.getInstance().getCurrentUser();

            if (!(currentUser instanceof Bidder)) {
                showError("Chỉ người mua (Bidder) mới có quyền đặt giá!");
                return;
            }

            // Chỉ gửi lệnh dạng String lên Server, KHÔNG tự validate ở đây
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
        // Lấy Stage (cửa sổ hiện tại) trực tiếp từ biến cancelButton
        Stage currentStage = (Stage) cancelButton.getScene().getWindow();
        
        // Đóng cửa sổ
        currentStage.close();
    }

    /**
     * Tiện ích: Hiển thị thông báo lỗi màu đỏ lên giao diện.
     */
    private void showError(String msg) {
        labelError.setText(msg);
        labelError.setTextFill(javafx.scene.paint.Color.RED);
    }

    /**
     * Tiện ích: Tìm Stage hiện tại từ sự kiện nhấn nút và đóng nó lại.
     */
    private void closeStage() {
        // Tìm Stage thông qua ô nhập giá tiền
        Stage stage = (Stage) textBidAmount.getScene().getWindow();
        stage.close();
    }
}