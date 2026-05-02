package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.auction.Auction;
import model.manager.AppState;

import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.util.Optional;

import model.item.Art;
import model.item.Electronics;
import model.item.Vehicle;
import model.user.Bidder;
import utils.AlertHelper;
import utils.SessionPermission;

/**
 * Điều khiển màn hình viewDetails.
 * Hiển thị đầy đủ thông số kỹ thuật, mô tả và lịch sử đặt giá gần nhất.
 *
 * Người dùng có quyền (Admin / Seller chủ phiên) sẽ thấy 2 nút Sửa và Xóa
 * khi phiên đang ở trạng thái OPEN.
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
     * Đổ toàn bộ dữ liệu từ đối tượng Auction vào các Label trên giao diện.
     */
    public void setItemData(Auction auction) {
        this.auction = auction;
        refreshUI();
        setupPermissions();
    }

    /**
     * Vẽ lại toàn bộ thông tin lên màn hình. Tách ra hàm riêng để có thể gọi lại
     * sau khi user sửa thông tin (Edit) thành công.
     */
    private void refreshUI() {
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
     * Bật/tắt các nút theo quyền của người dùng hiện tại:
     *  - btnOpenBid: chỉ Bidder + phiên RUNNING
     *  - btnEdit / btnDelete: Admin hoặc Seller-chủ-phiên + phiên OPEN
     */
    private void setupPermissions() {
        boolean isBidder = (AppState.getInstance().getCurrentUser() instanceof Bidder);
        boolean canBid   = isBidder && "RUNNING".equals(auction.getStatus());
        boolean canEdit   = SessionPermission.canEdit(auction);
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
     * Mở cửa sổ Sửa phiên đấu giá (popup modal).
     * Sau khi user save: cửa sổ này (item_details) cũng đóng theo
     * vì dữ liệu đã được cập nhật qua broadcast realtime.
     */
    @FXML
    void handleEdit() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/edit_session.fxml"));
            Parent root = loader.load();

            EditSessionController controller = loader.getController();
            controller.setAuction(this.auction);
            // Sau khi save xong → đóng luôn cửa sổ item_details vì dữ liệu cũ đã obsolete
            controller.setOnSavedCallback(this::closeWindow);

            Stage stage = new Stage();
            stage.setTitle("Sửa phiên: " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) {
            System.err.println("Lỗi mở cửa sổ sửa: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Xóa phiên đấu giá. Hỏi xác nhận trước khi gửi lệnh xuống server.
     */
    @FXML
    void handleDelete() {
        // Hộp thoại xác nhận với cảnh báo cụ thể
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION
        );
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText("Bạn chắc chắn muốn xóa phiên này?");
        confirm.setContentText(
                "Phiên: " + auction.getItemName() + "\n" +
                        "ID: "    + auction.getAuctionId() + "\n\n" +
                        "Hành động này KHÔNG thể hoàn tác. " +
                        "Toàn bộ thông tin sản phẩm và lịch sử bid liên quan sẽ bị xóa vĩnh viễn."
        );

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // Gửi lệnh xuống server. Server sẽ kiểm tra quyền + xóa DB + broadcast
        String requesterId = AppState.getInstance().getCurrentUser().getUserId();
        String cmd = "DELETE_AUCTION:" + auction.getAuctionId() + ":" + requesterId;
        AppState.getInstance().getClient().send(cmd);

        // Đóng cửa sổ ngay vì dữ liệu phiên này không còn ý nghĩa.
        // Việc cập nhật bảng list ở Dashboard sẽ tự diễn ra qua socket broadcast.
        closeWindow();
        AlertHelper.show(AlertHelper.Type.INFO, "Yêu cầu xóa đã được gửi đi.");
    }

    /**
     * Nhấn nút Quay lại: Đóng cửa sổ viewDetails để về Dashboard.
     */
    @FXML
    void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) lblItemName.getScene().getWindow();
        stage.close();
    }

    @FXML
    public void handleOpenBidDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();

            BidController controller = loader.getController();
            controller.setAuctionData(this.auction);

            // Khi nhận BidResult: nếu SUCCESS thì cập nhật giá ngay từ kết quả,
            // không cần chờ server broadcast (tránh race condition)
            controller.setOnBidDoneCallback(result -> {
                if (result.getStatus() == model.auction.BidResult.Status.SUCCESS) {
                    String now = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                    lblCurrentPrice.setText(String.format("$%,.2f", result.getCurrentPrice()));
                    lblHighestBidder.setText(result.getWinnerUsername());
                    lblBidTime.setText(now);
                }
            });

            Stage stage = new Stage();
            stage.setTitle("Đặt giá cho " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (IOException e) {
            System.err.println("Lỗi mở hộp thoại đặt giá: " + e.getMessage());
        }
    }
}