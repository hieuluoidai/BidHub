package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.auction.Auction;
import model.auction.BidResult;
import model.manager.AppState;
import model.user.User;
import utils.AlertHelper;
import java.util.function.Consumer;

import service.AuctionService;
import exception.ValidationException;

/**
 * Điều khiển cửa sổ đặt giá (Bid Window).
 */
public class BidController {

    private final AuctionService auctionService = new AuctionService();

    @FXML private Label labelItemName;
    // ... rest of fields ...
    @FXML private Label labelCurrentPrice;
    @FXML private Label labelUserBalance;
    @FXML private Label labelError;
    @FXML private TextField textBidAmount;
    @FXML private CheckBox checkAnonymous;
    @FXML private Button cancelButton;
    @FXML private Button confirmButton;

    private Auction currentAuction;

    /**
     * Callback nhận BidResult để báo về cho màn hình cha (ItemDetailsController)
     * cập nhật UI ngay mà không cần chờ Server broadcast toàn bộ list.
     */
    private Consumer<BidResult> onBidDoneCallback;

    /**
     * Listener xử lý lỗi dạng String từ server (ERROR:code:msg, BID_FAILED:...).
     * Đảm bảo dialog không bị đơ mãi nếu server gửi lỗi dạng String thay vì BidResult object.
     */
    private final Consumer<String> errorStringListener = msg -> {
        if (msg == null) return;
        if (msg.startsWith("ERROR:") || msg.startsWith("BID_FAILED")) {
            cleanupListeners();
            setLoading(false);
            showError(extractErrorMessage(msg));
        }
    };

    public void setOnBidDoneCallback(Consumer<BidResult> callback) {
        this.onBidDoneCallback = callback;
    }

    /**
     * Nhận dữ liệu phiên đấu giá từ màn hình Dashboard/Details truyền sang.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        labelItemName.setText(auction.getItem().getItemName());
        labelCurrentPrice.setText(String.format("%,.0f ₫", auction.getCurrentPrice()));
        
        User user = AppState.getInstance().getCurrentUser();
        if (user != null) {
            labelUserBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        }
        
        // Không tự gợi ý sẵn giá đặt; để người dùng chủ động nhập mức muốn bid.
        textBidAmount.clear();
        if (checkAnonymous != null) {
            checkAnonymous.setSelected(false);
        }
    }

    @FXML
    void addBid10() {
        addToBid(10);
    }

    @FXML
    void addBid50() {
        addToBid(50);
    }

    @FXML
    void addBid100() {
        addToBid(100);
    }

    private void addToBid(double amount) {
        try {
            double current = 0;
            String text = textBidAmount.getText().trim();
            if (!text.isEmpty()) {
                // Dùng Locale.US cho dữ liệu số để Double.parseDouble không bị lỗi ở các máy dùng Locale khác.
                current = Double.parseDouble(text.replace(",", "."));
            } else {
                current = currentAuction.getCurrentPrice();
            }
            textBidAmount.setText(String.format(java.util.Locale.US, "%.2f", current + amount));
        } catch (NumberFormatException e) {
            textBidAmount.setText(String.format(java.util.Locale.US, "%.2f",
                    currentAuction.getCurrentPrice() + amount));
        }
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

            double amount = Double.parseDouble(amountStr.replace(",", "."));
            User currentUser = AppState.getInstance().getCurrentUser();

            // Sử dụng AuctionService để validate logic nghiệp vụ
            auctionService.validateBid(currentAuction, amount, currentUser);

            // 1. Chỉ vô hiệu hóa Confirm để tránh gửi trùng; Cancel vẫn hoạt động để thoát khi cần
            setLoading(true);

            // 2. Đăng ký nhận BidResult và cả lỗi dạng String từ server
            AppState.getInstance().getClient().setBidResultCallback(this::handleBidResult);
            AppState.getInstance().getClient().addStringMessageListener(errorStringListener);

            // 3. Gửi lệnh BID theo giao thức đã được đóng gói trong Service
            boolean isAnonymous = (checkAnonymous != null && checkAnonymous.isSelected());
            String command = auctionService.buildBidCommand(
                    currentAuction.getAuctionId(),
                    amount,
                    currentUser.getUserId(),
                    isAnonymous);
            
            AppState.getInstance().getClient().send(command);

        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ (phải là định dạng số)!");
        } catch (ValidationException e) {
            showError(e.getMessage());
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

        // Gỡ tất cả listener sau khi đã nhận được phản hồi
        cleanupListeners();
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
                labelCurrentPrice.setText(String.format("Giá hiện tại: %,.0f ₫", result.getCurrentPrice()));
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
        // Gỡ tất cả listener nếu thoát ngang
        cleanupListeners();
        closeStage();
    }

    /**
     * Gỡ mọi listener đã đăng ký — gọi ở mọi nơi thoát khỏi dialog.
     */
    private void cleanupListeners() {
        AppState.getInstance().getClient().setBidResultCallback(null);
        AppState.getInstance().getClient().removeStringMessageListener(errorStringListener);
    }

    /**
     * Trích message hiển thị từ chuỗi "ERROR:CODE:message" hoặc "BID_FAILED:reason".
     */
    private String extractErrorMessage(String msg) {
        if (msg.startsWith("ERROR:")) {
            String[] parts = msg.split(":", 3);
            return parts.length >= 3 ? parts[2] : msg;
        }
        if (msg.startsWith("BID_FAILED:")) {
            return msg.substring("BID_FAILED:".length());
        }
        return msg;
    }

    private void setLoading(boolean loading) {
        // Chỉ khóa Confirm — Cancel luôn được bật để user có thể thoát bất cứ lúc nào
        if (confirmButton != null) confirmButton.setDisable(loading);
    }

    private void showError(String msg) {
        labelError.setText(msg);
    }

    private void closeStage() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}
