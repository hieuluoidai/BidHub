package controller;

import java.time.LocalDateTime;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.Node;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.manager.AuctionManager;

public class CreateSessionController {

    @FXML private TextField textItemName;
    @FXML private TextField textStartingPrice;
    @FXML private TextField textDescription;
    @FXML private TextField textExtraInfo;
    @FXML private Label labelError;
    @FXML private ComboBox<String> cbItemType;

    @FXML
    private Label labelExtraInfo; // Nhớ khai báo @FXML cho cái Label này nhé

    @FXML
    public void initialize() {

        cbItemType.getItems().addAll("Electronics", "Art", "Vehicle");

        cbItemType.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            switch (newVal.toString()) {
                case "Electronics":
                    labelExtraInfo.setText("Brand (Hãng sản xuất)");
                    textExtraInfo.setPromptText("e.g. Apple, Samsung...");
                    break;
                case "Art":
                    labelExtraInfo.setText("Author (Tác giả)");
                    textExtraInfo.setPromptText("e.g. Picasso, Van Gogh...");
                    break;
                case "Vehicle":
                    labelExtraInfo.setText("Manufacturer (Hãng xe)");
                    textExtraInfo.setPromptText("e.g. Toyota, Honda...");
                    break;
                default:
                    labelExtraInfo.setText("Extra Information");
            }
        });
    }

    @FXML
    void handleSave(ActionEvent event) {
        try {
            // ... (Giữ nguyên đoạn lấy dữ liệu và validate của cậu) ...
            String type = cbItemType.getValue();
            String name = textItemName.getText();
            String priceStr = textStartingPrice.getText();
            String desc = textDescription.getText();
            String extraInfo = textExtraInfo.getText();

            // Check nếu chưa chọn loại sản phẩm
            if (type == null) {
                labelError.setTextFill(Color.RED);
                labelError.setText("Please select an item type!");
                return;
            }

            if (name.isEmpty() || priceStr.isEmpty() || extraInfo.isEmpty()) {
                labelError.setTextFill(Color.RED);
                labelError.setText("Please fill in all the required information!");
                return;
            }

            double startingPrice = Double.parseDouble(priceStr);
            if (startingPrice <= 0) {
                labelError.setTextFill(Color.RED);
                labelError.setText("Starting price must be greater than 0!");
                return;
            }

            String id = "ITEM_" + System.currentTimeMillis();
            Item newItem = ItemFactory.createItem(type, id, name, desc, startingPrice, extraInfo);

            String auctionId = "AUC_" + System.currentTimeMillis();
            LocalDateTime startTime = LocalDateTime.now();
            LocalDateTime endTime = startTime.plusDays(7);

            Auction newAuction = new Auction(auctionId, newItem, startTime, endTime);
            AuctionManager.getInstance().addAuction(newAuction);

            closeWindow(event);

        } catch (NumberFormatException e) {
            labelError.setTextFill(Color.RED);
            // --- SỬA LẠI THÔNG BÁO LỖI CHO KHỚP ---
            labelError.setText("Error: Price must be a valid number!");
            // --------------------------------------
        } catch (IllegalArgumentException e) {
            labelError.setTextFill(Color.RED);
            labelError.setText("Error: Couldn't recognize item type!");
        }
    }

    @FXML
    void handleCancel(ActionEvent event) {
        closeWindow(event);
    }

    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}