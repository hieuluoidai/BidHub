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
import javafx.event.ActionEvent;
import java.io.IOException;

public class ItemDetailsController {

    @FXML private Label lblItemName;
    @FXML private Label lblCategory;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimeLeft;
    @FXML private TextArea txtDescription;
    @FXML private Label lblEndTime;

    private Auction auction;

    public void setItemData(Auction auction) {
        this.auction = auction;

        // Đề bài yêu cầu hiển thị tên, mô tả, giá hiện tại
        lblItemName.setText(auction.getItemName());
        lblCategory.setText("Category: " + auction.getItem().getClass().getSimpleName());
        lblCurrentPrice.setText(String.format("$%,.2f", auction.getCurrentPrice()));
        txtDescription.setText(auction.getItem().getDescription());

        // 1. Tạo một bộ định dạng thời gian theo ý muốn (Ngày/Tháng/Năm Giờ:Phút:Giây)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        // 2. Ép thời gian của phiên đấu giá qua bộ định dạng này
        String formattedTime = auction.getEndTime().format(formatter);

        // 3. Hiển thị lên màn hình
        lblEndTime.setText("Ends at: " + formattedTime);
    }

    @FXML
    void handleBack() {
        // Đóng cửa sổ chi tiết
        lblItemName.getScene().getWindow().hide();
    }


    // Trong ItemDetailsController.java
    @FXML
    public void handleOpenBidDialog(javafx.event.ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();

            // Truyền dữ liệu phiên đấu giá sang cửa sổ đặt giá
            BidController controller = loader.getController();
            controller.setAuctionData(this.auction);

            Stage stage = new Stage();
            stage.setTitle("Đặt giá cho " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL); // Chế độ popup
            stage.showAndWait();

            // Sau khi đóng popup, cập nhật lại giá hiển thị trên màn hình chi tiết
            lblCurrentPrice.setText(String.format("$%,.2f", auction.getCurrentPrice()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}