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
        labelError.setText(""); // Xóa thông báo lỗi cũ
        
        try {
            String amountStr = textBidAmount.getText().trim();
            
            // 1. Kiểm tra xem có để trống ô nhập không
            if (amountStr.isEmpty()) {
                showError("Vui lòng nhập số tiền!");
                return;
            }
            
            double amount = Double.parseDouble(amountStr);
            User currentUser = AppState.getInstance().getCurrentUser();

            // 2. Chỉ cho phép người dùng có vai trò là Bidder đặt giá
            if (currentUser instanceof Bidder) {
                Bidder currentBidder = (Bidder) currentUser;

                // 3. Cập nhật mức giá mới vào đối tượng Auction.
                currentAuction.placeBid(currentBidder, amount);

                // 4. Gửi cả gói Auction đã có giá mới này lên Server
                // Server sẽ nhận được, lưu DB và notify cho tất cả người dùng khác.
                AppState.getInstance().getClient().send(currentAuction);

                closeStage(); // Đóng cửa sổ sau khi bid thành công
                System.out.println(">>> Đã đặt giá thành công bởi: " + currentBidder.getUsername());
            } else {
                showError("Chỉ người mua (Bidder) mới có quyền đặt giá!");
            }

        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ (phải là số)!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Hiển thị lỗi logic
            showError(e.getMessage());
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