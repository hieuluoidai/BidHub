package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import model.auction.Auction;

public class ConfirmDeleteController {
    @FXML private Label labelItemName;
    @FXML private Label labelAuctionId;

    private boolean confirmed = false;

    public void setAuctionData(Auction auction) {
        labelItemName.setText(auction.getItem().getItemName());
        labelAuctionId.setText(auction.getAuctionId());
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    @FXML
    void handleConfirm() {
        confirmed = true;
        closeWindow();
    }

    @FXML
    void handleCancel() {
        confirmed = false;
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) labelItemName.getScene().getWindow();
        stage.close();
    }
}