package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.manager.SessionManager;
import model.auction.Auction;
import model.user.Bidder;
import model.user.User;

public class BidController {

    @FXML private Label labelItemName;
    @FXML private Label labelCurrentPrice;
    @FXML private Label labelError;
    @FXML private TextField textBidAmount;

    private Auction currentAuction;

    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        labelItemName.setText("Product: " + auction.getItemName());
        labelCurrentPrice.setText("Current Price: $" + auction.getCurrentPrice());
    }

    @FXML
    void handleConfirm(ActionEvent event) {
        labelError.setText(""); 
        
        try {
            String amountStr = textBidAmount.getText().trim();
            if (amountStr.isEmpty()) {
                labelError.setText("Please enter an amount!");
                labelError.setTextFill(javafx.scene.paint.Color.RED);
                return;
            }
            double amount = Double.parseDouble(amountStr);

            User currentUser = model.manager.AppState.getInstance().getCurrentUser();

            if (currentUser instanceof Bidder) {
                Bidder currentBidder = (Bidder) currentUser;

                // 1. Cập nhật giá nội bộ trên RAM của máy này
                currentAuction.placeBid(currentBidder, amount);

                // 2. SỬA Ở ĐÂY: Bắn gói dữ liệu Auction mang mức giá mới này thẳng lên Server
                model.manager.AppState.getInstance().getClient().send(currentAuction);

                closeStage(event);
                System.out.println("Bid placed successfully by: " + currentBidder.getUsername());
            } else {
                labelError.setText("Only Bidders can place bids!");
                labelError.setTextFill(javafx.scene.paint.Color.RED);
            }

        } catch (NumberFormatException e) {
            labelError.setText("Invalid price format!");
            labelError.setTextFill(javafx.scene.paint.Color.RED);
        } catch (IllegalArgumentException | IllegalStateException e) {
            labelError.setText(e.getMessage());
            labelError.setTextFill(javafx.scene.paint.Color.RED);
        }
    }

    @FXML
    void handleCancel(ActionEvent event) {
        closeStage(event);
    }

    private void closeStage(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}