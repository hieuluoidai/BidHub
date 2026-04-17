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
import model.item.Art;
import model.item.Electronics;
import model.item.Vehicle;
import model.item.Item;

public class ItemDetailsController {

    @FXML private Label lblItemName;
    @FXML private Label lblCategory;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimeLeft;
    @FXML private TextArea txtDescription;
    @FXML private Label lblEndTime;
    @FXML private Label lblExtraInfo;

    private Auction auction;

    public void setItemData(Auction auction) {
        this.auction = auction;
        lblItemName.setText(auction.getItemName());
        lblCategory.setText("Category: " + auction.getItem().getClass().getSimpleName());
        lblCurrentPrice.setText(String.format("$%,.2f", auction.getCurrentPrice()));
        txtDescription.setText(auction.getItem().getDescription());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedTime = auction.getEndTime().format(formatter);

        // 3. Hiển thị lên màn hình
        lblEndTime.setText("Ends at: " + formattedTime);

        model.item.Item item = auction.getItem();
        String extraInfoText = "Không có thông tin chi tiết";

        if (item instanceof Electronics elec) {
            extraInfoText = "Hãng sản xuất: " + elec.getBrand();
        } else if (item instanceof Art art) {
            extraInfoText = "Tác giả: " + art.getAuthor();
        } else if (item instanceof Vehicle veh) {
            extraInfoText = "Hãng xe: " + veh.getBrand();
        }
        lblExtraInfo.setText(extraInfoText);
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