package controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.auction.Auction;
import model.manager.AppState;
import model.user.User;
import database.UserDAO;

import java.util.List;

public class AdminController {

    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> colAucId;
    @FXML private TableColumn<Auction, String> colAucItem;
    @FXML private TableColumn<Auction, String> colAucStatus;
    @FXML private TableColumn<Auction, String> colAucPrice;
    @FXML private TableColumn<Auction, String> colAucWinner;

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colUserId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;

    @FXML private Label labelAdminName;
    @FXML private Label labelTotalAuctions;
    @FXML private Label labelRunningAuctions;
    @FXML private Label labelFinishedAuctions;
    @FXML private Label labelTotalUsers;
    @FXML private Label labelTotalBidders;
    @FXML private Label labelTotalSellers;
    
    @FXML private TabPane mainTabPane;

    @FXML
    public void initialize() {
        User currentUser = AppState.getInstance().getCurrentUser();
        if (currentUser != null) {
            labelAdminName.setText("Admin: " + currentUser.getUsername());
        }

        setupAuctionTable();
        setupUserTable();

        // Bind vào ObservableList từ AppState — tự động cập nhật theo socket
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();
        auctionTable.setItems(auctions);

        auctions.addListener((javafx.collections.ListChangeListener<Auction>) c -> updateAuctionStats());

        loadUserData();
        updateAuctionStats();

        auctionTable.setRowFactory(tv -> {
            TableRow<Auction> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openItemDetails(row.getItem());
                }
            });
            return row;
        });
        
        userTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openUserDetails(row.getItem());
                }
            });
            return row;
        });

        // ⭐ YÊU CẦU SERVER GỬI LẠI TOÀN BỘ PHIÊN ĐẤU GIÁ
        // Vì socket vừa mới kết nối, server chưa auto-push dữ liệu
        try {
            AppState.getInstance().getClient().send("REFRESH_DATA");
        } catch (Exception e) {
            System.err.println("Không thể yêu cầu refresh: " + e.getMessage());
        }
    }

    private void setupAuctionTable() {
        colAucId.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getAuctionId()));

        colAucItem.setCellValueFactory(d -> {
            String name = d.getValue().getItem() != null
                ? d.getValue().getItemName() : "N/A";
            return new SimpleStringProperty(name);
        });

        colAucStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStatus()));

        colAucPrice.setCellValueFactory(d -> {
            try {
                return new SimpleStringProperty(
                    String.format("$%.2f", d.getValue().getCurrentPrice()));
            } catch (Exception e) {
                return new SimpleStringProperty("N/A");
            }
        });

        colAucWinner.setCellValueFactory(d -> {
            var highest = d.getValue().getHighestBid();
            if (highest == null || highest.getBidder() == null) {
                return new SimpleStringProperty("Chưa có");
            }
            return new SimpleStringProperty(highest.getBidder().getUsername());
        });
    }

    private void setupUserTable() {
        colUserId  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserId()));
        colUsername.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        colEmail   .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colRole    .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getClass().getSimpleName().toUpperCase()));
    }

    private void updateAuctionStats() {
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();

        long running  = auctions.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
        long finished = auctions.stream().filter(a -> "FINISHED".equals(a.getStatus())).count();

        if (labelTotalAuctions   != null) labelTotalAuctions  .setText("Tổng phiên: "   + auctions.size());
        if (labelRunningAuctions != null) labelRunningAuctions.setText("Đang chạy: "    + running);
        if (labelFinishedAuctions!= null) labelFinishedAuctions.setText("Đã kết thúc: " + finished);
    }

    private void loadUserData() {
        try {
            List<User> allUsers = new UserDAO().findAll();
            userTable.setItems(FXCollections.observableArrayList(allUsers));

            long bidders = allUsers.stream().filter(u -> u instanceof model.user.Bidder).count();
            long sellers = allUsers.stream().filter(u -> u instanceof model.user.Seller).count();

            if (labelTotalUsers   != null) labelTotalUsers  .setText("Tổng users: " + allUsers.size());
            if (labelTotalBidders != null) labelTotalBidders.setText("Bidders: "    + bidders);
            if (labelTotalSellers != null) labelTotalSellers.setText("Sellers: "    + sellers);
        } catch (Exception e) {
            System.err.println("Lỗi load users: " + e.getMessage());
        }
    }

    // ----- Chức năng bổ sung -----

    /** Mở cửa sổ chi tiết phiên đấu giá (double-click hoặc nút View Details) */
    private void openItemDetails(Auction auction) {
        if (auction == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();

            // Gọi trực tiếp method đúng của ItemDetailsController
            ItemDetailsController controller = loader.getController();
            controller.setItemData(auction);

            Stage stage = new Stage();
            stage.setTitle("Chi tiết phiên: " + auction.getAuctionId());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) {
            System.err.println("Lỗi mở chi tiết: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleViewDetails() {
        // Kiểm tra tab đang mở để quyết định xem chi tiết phiên hay chi tiết user
        // Cần thêm fx:id="mainTabPane" vào TabPane trong FXML

        if (mainTabPane != null && mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
            // Tab 1 = Quản lý người dùng
            handleViewUserDetails();
        } else {
            // Tab 0 = Phiên đấu giá
            Auction selected = auctionTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                utils.AlertHelper.show(utils.AlertHelper.Type.WARNING, "Hãy chọn một phiên từ bảng!");
                return;
            }
            openItemDetails(selected);
        }
    }

    @FXML
    void handleCreateSession() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create_session.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Tạo phiên đấu giá mới");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            updateAuctionStats(); // refresh sau khi đóng cửa sổ
        } catch (Exception e) {
            System.err.println("Lỗi mở create session: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleRefresh() {
        updateAuctionStats();
        loadUserData();
    }

    @FXML
    void handleLogout() {
        AppState.getInstance().setCurrentUser(null);
        AppState.getInstance().getSceneManager().showLogin();
    }
    
    private void openUserDetails(User user) {
        if (user == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_details.fxml"));
            Parent root = loader.load();

            UserDetailsController controller = loader.getController();
            controller.setUserData(user);

            Stage stage = new Stage();
            stage.setTitle("Thông tin: " + user.getUsername());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) {
            System.err.println("Lỗi mở user details: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleViewUserDetails() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            utils.AlertHelper.show(utils.AlertHelper.Type.WARNING, "Hãy chọn một người dùng từ bảng!");
            return;
        }
        openUserDetails(selected);
    }
}