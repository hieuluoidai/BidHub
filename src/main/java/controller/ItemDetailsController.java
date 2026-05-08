package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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

    @FXML private Button btnOpenBid;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;

    private Auction auction;

    /**
     * Khởi tạo dữ liệu cho màn hình.
     */
    public void setItemData(Auction auction) {
        this.auction = auction;
        refreshUI();
        setupPermissions();
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
    }

    /**
     * Kiểm tra quyền để hiển thị các nút chức năng.
     */
    private void setupPermissions() {
        boolean isBidder = (AppState.getInstance().getCurrentUser() instanceof Bidder);
        boolean canBid   = isBidder && "RUNNING".equals(auction.getStatus());
        boolean canEdit  = SessionPermission.canEdit(auction);
        boolean canDelete = SessionPermission.canDelete(auction);

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
            
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT); 
            stage.setScene(scene);

            // Thêm kích thước Stage để không bị cắt bóng đổ
            stage.setWidth(500);
            stage.setHeight(420);

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