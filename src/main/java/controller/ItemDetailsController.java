package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.auction.Auction;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import model.item.Art;
import model.item.Electronics;
import model.item.Vehicle;

/**
 * Điều khiển màn hình viewDetails.
 * Hiển thị đầy đủ thông số kỹ thuật, mô tả và lịch sử đặt giá gần nhất.
 */
public class ItemDetailsController {

    @FXML private Label lblItemName;
    @FXML private Label lblCategory;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimeLeft;
    @FXML private TextArea txtDescription;
    @FXML private Label lblEndTime;
    @FXML private Label lblExtraInfo;
    @FXML private Label lblHighestBidder;
    @FXML private Label lblBidTime;

    private Auction auction;

    /**
     * Đổ toàn bộ dữ liệu từ đối tượng Auction vào các Label trên giao diện.
     */
    public void setItemData(Auction auction) {
        this.auction = auction;
        lblItemName.setText(auction.getItemName());
        lblCategory.setText(auction.getItem().getClass().getSimpleName());
        lblCurrentPrice.setText(String.format("$%,.2f", auction.getCurrentPrice()));
        txtDescription.setText(auction.getItem().getDescription());

        // Định dạng thời gian hiển thị: Ngày/Tháng/Năm Giờ:Phút:Giây
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        lblEndTime.setText("Thời điểm kết thúc: " + auction.getEndTime().format(formatter));

        // Xử lí thông tin đặc thù
        // Tùy theo loại hàng là Điện tử, Nghệ thuật hay Xe cộ mà hiện thông tin riêng
        String extraInfoText = "Không có thông tin chi tiết";
        var item = auction.getItem();

        if (item instanceof Electronics elec) {
            extraInfoText = "Hãng sản xuất: " + elec.getBrand();
        } else if (item instanceof Art art) {
            extraInfoText = "Tác giả: " + art.getAuthor();
        } else if (item instanceof Vehicle veh) {
            extraInfoText = "Hãng xe: " + veh.getBrand();
        }
        lblExtraInfo.setText(extraInfoText);

        // Hiển thị người đang dânx đầu
        if (auction.getHighestBid() != null) {
            lblHighestBidder.setText(auction.getHighestBid().getBidder().getUsername());
            lblBidTime.setText(auction.getHighestBid().getTimestamp().format(formatter));
        } else {
            lblHighestBidder.setText("Chưa có ai (Giá khởi điểm)");
            lblBidTime.setText("-");
        }
    }

    /**
     * Nhấn nút Quay lại: Đóng cửa sổ viewDetails để về Dashboard.
     */
    @FXML
    void handleBack() {
        // Lấy cửa sổ hiện tại và đóng lại
        Stage stage = (Stage) lblItemName.getScene().getWindow();
        stage.close();
    }

    /**
     * Mở trực tiếp hộp thoại Bid ngay tại màn hình chi tiết.
     */
    @FXML
    public void handleOpenBidDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();

            // Truyền dữ liệu của món hàng hiện tại sang cho cửa sổ Bid
            BidController controller = loader.getController();
            controller.setAuctionData(this.auction);

            // Mở cửa sổ Bid theo kiểu Pop-up
            Stage stage = new Stage();
            stage.setTitle("Đặt giá cho " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL); 
            stage.showAndWait();

            // Update lại giao diện sau khi bid
            // Sau khi đóng Pop-up đặt giá, ta cần update số tiền mới ngay lập tức trên màn hình viewDetails
            lblCurrentPrice.setText(String.format("$%,.2f", auction.getCurrentPrice()));

            if (auction.getHighestBid() != null) {
                lblHighestBidder.setText(auction.getHighestBid().getBidder().getUsername());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                lblBidTime.setText(auction.getHighestBid().getTimestamp().format(formatter));
            }
            
        } catch (IOException e) {
            System.err.println("Lỗi mở hộp thoại đặt giá: " + e.getMessage());
        }
    }
}