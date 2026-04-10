package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.auction.Auction;
import model.manager.AuctionManager;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import model.manager.SessionManager;
import utils.SceneSwitcher;

import java.io.IOException;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;

public class DashboardController {
	
	@FXML private Label titleLabel;
	@FXML private TextField textSearch;

    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> colId;
    @FXML private TableColumn<Auction, String> colItemName;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;
    
    @FXML private Button bidButton;
    @FXML private Button detailsButton;
    @FXML private Button createSessionButton;

    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName")); 
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        loadAuctionData();

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            titleLabel.setText("Welcome, " + currentUser.getUsername() + " (" + currentUser.getClass().getSimpleName() + ")");
            
            if (currentUser instanceof Admin || currentUser instanceof Seller) {
                createSessionButton.setVisible(true);
                createSessionButton.setManaged(true);
            } else {
                createSessionButton.setVisible(false);
                createSessionButton.setManaged(false);
            }

            if (!(currentUser instanceof Bidder)) {
                bidButton.setDisable(true);
            }
        }
    }
    
    @FXML
    private void loadAuctionData() {
        ObservableList<Auction> auctionList = FXCollections.observableArrayList(
            AuctionManager.getInstance().getAllAuctions()
        );
        auctionTable.setItems(auctionList);
    }
    
    @FXML
    void handleLogout(ActionEvent event) {
        try {
            SessionManager.getInstance().logout();
			SceneSwitcher.changeScene(event, "/view/login.fxml");
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    @FXML
	void handleCreateNewSession(ActionEvent event) {
	    try {
	        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create_session.fxml"));
	        Parent root = loader.load();

	        Stage popupStage = new Stage();
	        popupStage.setTitle("Create New Auction Session");
	        popupStage.setScene(new Scene(root));
	        popupStage.initModality(Modality.APPLICATION_MODAL);
	        popupStage.showAndWait();

	        loadAuctionData();
	        auctionTable.refresh();

	    } catch (Exception e) {
	        System.err.println("Error opening create session pop-up: " + e.getMessage());
	        e.printStackTrace();
	    }
	}
    
    @FXML
    void handleBid(ActionEvent event) {
        Auction selectedAuction = auctionTable.getSelectionModel().getSelectedItem();

        if (selectedAuction == null) {
            System.out.println("Please select an item to bid!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();

            BidController controller = loader.getController();
            controller.setAuctionData(selectedAuction);

            Stage stage = new Stage();
            stage.setTitle("Place your Bid");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            auctionTable.refresh();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}