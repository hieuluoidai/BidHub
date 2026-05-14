package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.auction.Auction;
import model.auction.BidResult;
import model.item.Art;
import model.item.Electronics;
import model.item.Vehicle;
import model.manager.AppState;
import model.user.Bidder;
import utils.AlertHelper;
import utils.ImageStorageService;
import utils.SessionPermission;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Điều khiển màn hình viewDetails.
 * Hiển thị đầy đủ thông số kỹ thuật, mô tả và lịch sử đặt giá gần nhất.
 */
public class ItemDetailsController {

    @FXML private Label lblItemName;
    @FXML private Label lblCategory;
    @FXML private Label lblCurrentPrice;
    @FXML private TextArea txtDescription;
    @FXML private Label lblEndTime;
    @FXML private Label lblExtraInfo;
    @FXML private Label lblHighestBidder;
    @FXML private Label lblBidTime;
    @FXML private ImageView itemImageView;    // Ảnh sản phẩm (nullable)

    @FXML private Button btnOpenBid;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnCancel;     // Hủy phiên (chuyển sang CANCELED)
    @FXML private Button btnPay;        // Thanh toán phiên FINISHED (cho winner)

    // UI cho Auto-Bid (Logic mới của Hiếu)
    @FXML private javafx.scene.layout.VBox paneAutoBid;
    @FXML private javafx.scene.control.TextField txtAutoMaxBid;
    @FXML private javafx.scene.control.TextField txtAutoIncrement;
    @FXML private Button btnSetAutoBid;
    @FXML private Button btnCancelAutoBid;

    private Auction auction;

    public Auction getAuction() {
        return auction;
    }

    /**
     * Khởi tạo dữ liệu cho màn hình.
     */
    public void setItemData(Auction auction) {
        this.auction = auction;
        refreshUI();
        setupPermissions();
        checkExistingAutoBid();
    }

    /**
     * Kiểm tra xem user hiện tại đã có auto-bid cho phiên này chưa.
     */
    private void checkExistingAutoBid() {
        if (!(AppState.getInstance().getCurrentUser() instanceof Bidder)) return;
        if (!"RUNNING".equals(auction.getStatus())) return;

        var user = AppState.getInstance().getCurrentUser();
        String cmd = "GET_MY_AUTOBID:" + auction.getAuctionId() + ":" + user.getUserId();
        
        AppState.getInstance().getClient().setStringMessageCallback(this::handleAutoBidResponse);
        AppState.getInstance().getClient().send(cmd);
    }

    /**
     * Cập nhật thông tin lên các nhãn hiển thị.
     */
    private void refreshUI() {
        if (auction == null) return;

        lblItemName.setText(auction.getItemName());
        lblCategory.setText(auction.getItem().getClass().getSimpleName());
        lblCurrentPrice.setText(String.format("$%,.2f", auction.getCurrentPrice()));
        txtDescription.setText(auction.getItem().getDescription());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        lblEndTime.setText(auction.getEndTime().format(formatter));

        // Thông tin đặc thù theo từng loại sản phẩm
        String extraInfoText = "Không có thông tin chi tiết";
        var item = auction.getItem();
        if (item instanceof Electronics elec) {
            extraInfoText = "Hãng sản xuất: " + elec.getBrand();
        } else if (item instanceof Art art) {
            extraInfoText = "Tác giả: " + art.getArtist();
        } else if (item instanceof Vehicle veh) {
            extraInfoText = "Hãng xe: " + veh.getBrand();
        }
        lblExtraInfo.setText(extraInfoText);

        // Hiển thị người đang dẫn đầu
        if (auction.getHighestBid() != null) {
            lblHighestBidder.setText(auction.getHighestBid().getBidder().getUsername());
            lblBidTime.setText(auction.getHighestBid().getTimestamp().format(formatter));
        } else {
            lblHighestBidder.setText("Chưa có ai (Giá khởi điểm)");
            lblBidTime.setText("-");
        }

        // Hiển thị ảnh sản phẩm (nếu có)
        if (itemImageView != null) {
            String imgPath = item.getImagePath();
            if (imgPath != null && !imgPath.isBlank()) {
                String uri = ImageStorageService.toFileUri(imgPath);
                if (uri != null) {
                    itemImageView.setImage(new Image(uri, 280, 0, true, true));
                } else {
                    itemImageView.setImage(null);  // File bị xóa khỏi disk
                }
            } else {
                itemImageView.setImage(null);
            }
        }
    }

    /**
     * Kiểm tra quyền để hiển thị các nút chức năng.
     */
    private void setupPermissions() {
        boolean isBidder  = (AppState.getInstance().getCurrentUser() instanceof Bidder);
        boolean canBid    = isBidder && "RUNNING".equals(auction.getStatus());
        boolean canEdit   = SessionPermission.canEdit(auction);
        boolean canDelete = SessionPermission.canDelete(auction);
        boolean canCancel = SessionPermission.canCancel(auction);

        // Hiển thị pane Auto-Bid cho Bidder khi phiên đang RUNNING
        if (paneAutoBid != null) {
            paneAutoBid.setVisible(canBid);
            paneAutoBid.setManaged(canBid);
        }

        // Nút Thanh toán: chỉ winner của phiên FINISHED chưa PAID mới thấy
        String winnerId = (auction.getHighestBid() != null
                && auction.getHighestBid().getBidder() != null)
                ? auction.getHighestBid().getBidder().getUserId()
                : null;
        boolean canPay = SessionPermission.canPay(auction, winnerId);

        if (btnOpenBid != null) {
            btnOpenBid.setVisible(canBid);
            btnOpenBid.setManaged(canBid);
        }
        if (btnEdit != null) {
            btnEdit.setVisible(canEdit);
            btnEdit.setManaged(canEdit);
        }
        if (btnDelete != null) {
            btnDelete.setVisible(canDelete);
            btnDelete.setManaged(canDelete);
        }
        if (btnCancel != null) {
            btnCancel.setVisible(canCancel);
            btnCancel.setManaged(canCancel);
        }
        if (btnPay != null) {
            btnPay.setVisible(canPay);
            btnPay.setManaged(canPay);
        }
    }

    /**
     * Xử lý Thiết lập Auto-Bid.
     */
    @FXML
    public void handleSetAutoBid() {
        String maxStr = txtAutoMaxBid.getText().trim();
        String incStr = txtAutoIncrement.getText().trim();

        if (maxStr.isEmpty() || incStr.isEmpty()) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Thiếu thông tin", "Vui lòng nhập giá tối đa và bước giá.");
            return;
        }

        try {
            double maxBid = Double.parseDouble(maxStr);
            double increment = Double.parseDouble(incStr);
            double currentPrice = auction.getCurrentPrice();

            if (maxBid <= currentPrice) {
                AlertHelper.show(AlertHelper.Type.ERROR, "Giá không hợp lệ", "Giá tối đa phải lớn hơn giá hiện tại.");
                return;
            }
            if (increment <= 0) {
                AlertHelper.show(AlertHelper.Type.ERROR, "Bước giá không hợp lệ", "Bước giá phải lớn hơn 0.");
                return;
            }

            var user = AppState.getInstance().getCurrentUser();
            String cmd = String.format("SET_AUTOBID:%s:%s:%.2f:%.2f", 
                auction.getAuctionId(), user.getUserId(), maxBid, increment);
            
            AppState.getInstance().getClient().setStringMessageCallback(this::handleAutoBidResponse);
            AppState.getInstance().getClient().send(cmd);
            
            btnSetAutoBid.setDisable(true);
            btnSetAutoBid.setText("Đang xử lý...");

        } catch (NumberFormatException e) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi định dạng", "Vui lòng nhập số hợp lệ.");
        }
    }

    /**
     * Xử lý Hủy Auto-Bid.
     */
    @FXML
    public void handleCancelAutoBid() {
        boolean confirm = AlertHelper.showConfirm("Xác nhận hủy", 
            "Bạn có chắc chắn muốn hủy đấu giá tự động không? \nSố tiền đang bị khóa sẽ được hoàn trả vào ví của bạn.");
        
        if (!confirm) return;

        var user = AppState.getInstance().getCurrentUser();
        String cmd = "CANCEL_AUTOBID:" + auction.getAuctionId() + ":" + user.getUserId();
        
        AppState.getInstance().getClient().setStringMessageCallback(this::handleAutoBidResponse);
        AppState.getInstance().getClient().send(cmd);
        
        btnCancelAutoBid.setDisable(true);
    }

    private void handleAutoBidResponse(String msg) {
        javafx.application.Platform.runLater(() -> {
            // Kiểm tra nếu thông báo không dành cho auction hiện tại thì bỏ qua (nếu format có auctionId)
            if (msg.contains(":") && !msg.startsWith("AUTOBID_FAILED")) {
                String[] parts = msg.split(":");
                if (parts.length > 1 && !parts[1].equals(auction.getAuctionId())) return;
            }

            if (msg.startsWith("AUTOBID_OK")) {
                String[] parts = msg.split(":");
                if (parts.length >= 3) {
                    try {
                        double balance = Double.parseDouble(parts[2]);
                        AppState.getInstance().getCurrentUser().setBalance(balance);
                    } catch (NumberFormatException ignore) {}
                }
                AlertHelper.show(AlertHelper.Type.SUCCESS, "Thành công", "Đã thiết lập đấu giá tự động!");
                updateAutoBidUI(true, txtAutoMaxBid.getText(), txtAutoIncrement.getText());

            } else if (msg.startsWith("MY_AUTOBID:")) {
                // Format: MY_AUTOBID:<auctionId>:<maxBid>:<increment>
                String[] parts = msg.split(":");
                if (parts.length >= 4) {
                    updateAutoBidUI(true, parts[2], parts[3]);
                }

            } else if (msg.startsWith("MY_AUTOBID_NONE")) {
                updateAutoBidUI(false, "", "");

            } else if (msg.startsWith("CANCEL_AUTOBID_OK")) {
                String[] parts = msg.split(":");
                if (parts.length >= 3) {
                    try {
                        double balance = Double.parseDouble(parts[2]);
                        AppState.getInstance().getCurrentUser().setBalance(balance);
                    } catch (NumberFormatException ignore) {}
                }
                AlertHelper.show(AlertHelper.Type.INFO, "Đã hủy", "Đã hủy đấu giá tự động và hoàn lại tiền khóa.");
                updateAutoBidUI(false, "", "");

            } else if (msg.startsWith("AUTOBID_FAILED")) {
                String reason = msg.substring("AUTOBID_FAILED:".length());
                AlertHelper.show(AlertHelper.Type.ERROR, "Thất bại", "Không thể thiết lập Auto-Bid: " + reason);
                btnSetAutoBid.setDisable(false);
                btnSetAutoBid.setText("Thiết lập Auto-Bid");
            }
        });
    }

    private void updateAutoBidUI(boolean active, String max, String inc) {
        btnSetAutoBid.setVisible(!active);
        btnSetAutoBid.setManaged(!active);
        btnSetAutoBid.setDisable(false);
        btnSetAutoBid.setText("Thiết lập Auto-Bid");

        btnCancelAutoBid.setVisible(active);
        btnCancelAutoBid.setManaged(active);
        btnCancelAutoBid.setDisable(false);

        txtAutoMaxBid.setText(max);
        txtAutoIncrement.setText(inc);
        txtAutoMaxBid.setEditable(!active);
        txtAutoIncrement.setEditable(!active);
    }

    /**
     * Xử lý mở hộp thoại Đặt giá (Bid).
     */
    @FXML
    public void handleOpenBidDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();

            BidController controller = loader.getController();
            controller.setAuctionData(this.auction);

            // Cập nhật giá ngay lập tức khi bid thành công (Concurrent Bidding UI Update)
            controller.setOnBidDoneCallback(result -> {
                if (result.getStatus() == BidResult.Status.SUCCESS) {
                    refreshUIFromBid(result);
                }
            });

            Stage stage = new Stage();
            stage.setTitle("Đặt giá cho " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void refreshUIFromBid(BidResult result) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        lblCurrentPrice.setText(String.format("$%,.2f", result.getCurrentPrice()));
        lblHighestBidder.setText(result.getWinnerUsername());
        lblBidTime.setText(now);
    }

    /**
     * Xử lý mở hộp thoại Sửa phiên.
     */
    @FXML
    void handleEdit() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/edit_session.fxml"));
            Parent root = loader.load();

            EditSessionController controller = loader.getController();
            controller.setAuction(this.auction);
            controller.setOnSavedCallback(this::closeWindow);

            Stage stage = new Stage();
            stage.setTitle("Sửa phiên: " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Xử lý Xóa phiên với giao diện Xác nhận hiện đại mới.
     */
    @FXML
    void handleOpenDelete() { // <--- Đổi từ handleDelete thành handleOpenDelete
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConfirmDelete.fxml"));
            Parent root = loader.load();

            ConfirmDeleteController controller = loader.getController();
            controller.setAuctionData(this.auction);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initOwner(lblItemName.getScene().getWindow());

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            // Tự động căn giữa stage so với Owner
            stage.setOnShown(event -> {
                Stage owner = (Stage) stage.getOwner();
                if (owner != null) {
                    double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0;
                    double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0;
                    stage.setX(x);
                    stage.setY(y);
                }
            });

            stage.showAndWait();

            if (controller.isConfirmed()) {
                executeDelete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gửi lệnh xóa chính thức lên Server.
     */
    private void executeDelete() {
        if (this.auction == null) return;

        String auctionId = this.auction.getAuctionId();
        String requesterId = AppState.getInstance().getCurrentUser().getUserId();

        // Format lệnh: "DELETE_AUCTION:<auctionId>:<requesterId>"
        String command = "DELETE_AUCTION:" + auctionId + ":" + requesterId;
        AppState.getInstance().getClient().send(command);

        System.out.println(">>> Đã gửi yêu cầu xóa phiên: " + auctionId);

        closeWindow();
        AlertHelper.show(AlertHelper.Type.INFO, "Yêu cầu xóa đã được gửi đi.");
    }

    /**
     * Hủy phiên đấu giá (chuyển trạng thái sang CANCELED).
     * Khác với XÓA: phiên CANCELED vẫn còn trong DB để có lịch sử.
     *
     * Quyền:
     *   - Admin: hủy phiên ở mọi status (trừ CANCELED, PAID)
     *   - Seller: chỉ phiên của mình ở status OPEN/RUNNING
     */
    @FXML
    void handleCancelAuction() {
        if (this.auction == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConfirmCancel.fxml"));
            Parent root = loader.load();

            ConfirmCancelController controller = loader.getController();
            controller.setAuctionData(this.auction);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initOwner(lblItemName.getScene().getWindow());

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            // Tự động căn giữa stage so với Owner
            stage.setOnShown(event -> {
                Stage owner = (Stage) stage.getOwner();
                if (owner != null) {
                    double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0;
                    double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0;
                    stage.setX(x);
                    stage.setY(y);
                }
            });

            stage.showAndWait();

            if (controller.isConfirmed()) {
                String auctionId   = this.auction.getAuctionId();
                String requesterId = AppState.getInstance().getCurrentUser().getUserId();
                String command = "CANCEL_AUCTION:" + auctionId + ":" + requesterId;
                AppState.getInstance().getClient().send(command);

                System.out.println(">>> Đã gửi yêu cầu hủy phiên: " + auctionId);
                closeWindow();
                AlertHelper.show(AlertHelper.Type.INFO, "Yêu cầu hủy phiên đã được gửi đi.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Thanh toán phiên đấu giá thắng cuộc.
     *
     * Quy trình:
     *   1. Hiện confirm dialog với số tiền cần thanh toán + balance hiện tại
     *   2. User xác nhận → gửi PAY_AUCTION lên server
     *   3. Server check balance + transfer atomic + markAsPaid
     *   4. Client nhận PAY_OK / PAY_FAILED qua callback
     */
    @FXML
    void handlePay() {
        if (this.auction == null) return;
        if (this.auction.getHighestBid() == null) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Phiên này không có người thắng");
            return;
        }

        var currentUser = AppState.getInstance().getCurrentUser();
        if (currentUser == null) return;

        double finalPrice = this.auction.getCurrentPrice();
        double currentBalance = currentUser.getBalance();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConfirmPay.fxml"));
            Parent root = loader.load();

            ConfirmPayController controller = loader.getController();
            controller.setPaymentData(finalPrice, currentBalance);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initOwner(lblItemName.getScene().getWindow());

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            // Tự động căn giữa stage so với Owner
            stage.setOnShown(event -> {
                Stage owner = (Stage) stage.getOwner();
                if (owner != null) {
                    double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0;
                    double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0;
                    stage.setX(x);
                    stage.setY(y);
                }
            });

            stage.showAndWait();

            // Nếu User bấm "Xác nhận thanh toán"
            if (controller.isConfirmed()) {
                AppState.getInstance().getClient().setStringMessageCallback(this::handlePayResponse);
                String command = "PAY_AUCTION:" + auction.getAuctionId() + ":" + currentUser.getUserId();
                AppState.getInstance().getClient().send(command);

                if (btnPay != null) btnPay.setDisable(true);
                System.out.println(">>> Đã gửi yêu cầu thanh toán: " + auction.getAuctionId());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Callback xử lý PAY_OK / PAY_FAILED từ server.
     */
    private void handlePayResponse(String msg) {
        // Bỏ qua nếu không phải response của PAY
        if (!msg.startsWith("PAY_OK") && !msg.startsWith("PAY_FAILED")) return;

        AppState.getInstance().getClient().setStringMessageCallback(null);
        if (btnPay != null) btnPay.setDisable(false);

        if (msg.startsWith("PAY_OK:")) {
            // Format: PAY_OK:<auctionId>:<newBalance>
            String[] parts = msg.split(":");
            if (parts.length >= 3) {
                try {
                    double newBalance = Double.parseDouble(parts[2]);
                    var user = AppState.getInstance().getCurrentUser();
                    if (user != null) user.setBalance(newBalance);
                } catch (NumberFormatException ignore) {}
            }

            // ĐÃ SỬA: Dùng AlertHelper hiện đại thay vì Alert mặc định
            utils.AlertHelper.show(
                    utils.AlertHelper.Type.SUCCESS,
                    "Thanh toán thành công",
                    "Bạn đã thanh toán thành công cho phiên này. Sản phẩm chính thức thuộc về bạn!"
            );

            closeWindow();
        } else {
            // PAY_FAILED:<reason>
            String reason = msg.substring("PAY_FAILED:".length()).trim();
            AlertHelper.show(AlertHelper.Type.ERROR, "Thanh toán thất bại", reason);
        }
    }

    @FXML
    void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        if (lblItemName != null && lblItemName.getScene() != null) {
            Stage stage = (Stage) lblItemName.getScene().getWindow();
            stage.close();
        }
    }
}