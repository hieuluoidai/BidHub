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
import utils.SceneSwitcher;

import java.io.IOException;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;

public class DashboardController {
	
	@FXML
	private Label titleLabel;
	
	@FXML
    private TextField textSearch;

    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> colId;
    @FXML private TableColumn<Auction, String> colItemName;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;
    
    @FXML
    private Button bidButton;

    @FXML
    private Button detailsButton;

    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName")); 
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        ObservableList<Auction> data = FXCollections.observableArrayList(
            AuctionManager.getInstance().getAllAuctions()
        );

        auctionTable.setItems(data);
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
}
