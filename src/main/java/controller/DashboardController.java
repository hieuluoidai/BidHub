package controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
        // Cấu hình các cột của bảng
        colId.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAuctionId()));
        colItemName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getItem().getItemName()));
        colCurrentPrice.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getCurrentPrice()));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().toString()));
        loadAuctionData();

        // Xử lý phân quyền
        User currentUser = SessionManager.getInstance().getCurrentUser();
        boolean isBidder = (currentUser instanceof Bidder);

        if (currentUser != null) {
            titleLabel.setText("Welcome, " + currentUser.getUsername() + " (" + currentUser.getClass().getSimpleName() + ")");

            if (currentUser instanceof Admin || currentUser instanceof Seller) {
                createSessionButton.setVisible(true);
                createSessionButton.setManaged(true);
            } else {
                createSessionButton.setVisible(false);
                createSessionButton.setManaged(false);
            }
        }

        // ==========================================
        // PHẦN TỐI ƯU UX: KHÓA/MỞ NÚT THÔNG MINH
        // ==========================================
        detailsButton.setDisable(true);
        bidButton.setDisable(true);

        auctionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailsButton.setDisable(false);
                if (isBidder) {
                    bidButton.setDisable(false);
                }
            } else {
                detailsButton.setDisable(true);
                bidButton.setDisable(true);
            }
        });

        // ==========================================
        // THÊM MỚI: BẮT SỰ KIỆN DOUBLE CLICK
        // ==========================================
        auctionTable.setOnMouseClicked(event -> {
            // Kiểm tra: Nếu nhấp chuột 2 lần VÀ có chọn 1 sản phẩm
            if (event.getClickCount() == 2 && auctionTable.getSelectionModel().getSelectedItem() != null) {
                System.out.println("Đã nháy đúp chuột, mở xem chi tiết...");

                // Gọi thẳng hàm Xem chi tiết của cậu.
                // Ta truyền 'null' vào vì hàm handleViewDetails của cậu mở Popup, không cần dùng biến event.
                handleViewDetails(null);
            }
        });
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
        SessionManager.getInstance().logout();
        SceneSwitcher.switchScene(event, "/view/login.fxml");
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


    @FXML
    void handleViewDetails(ActionEvent event) {
        Auction selectedAuction = auctionTable.getSelectionModel().getSelectedItem();

        if (selectedAuction == null) {
            System.out.println("Please select an auction to view details!");
            return;
        }

        try {
            // Tải file FXML item_details
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();

            // Lấy controller của trang chi tiết và truyền dữ liệu sang
            ItemDetailsController controller = loader.getController();
            controller.setItemData(selectedAuction);

            // Hiển thị dưới dạng cửa sổ mới (Popup)
            Stage stage = new Stage();
            stage.setTitle("Auction Item Details");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            // Cập nhật lại bảng sau khi xem (phòng trường hợp có thay đổi)
            auctionTable.refresh();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}