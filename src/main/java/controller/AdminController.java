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

import java.io.IOException;
import java.util.List;

public class AdminController {

    @FXML private javafx.scene.layout.FlowPane flowPaneAuctions;

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colUserId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colBalance;

    @FXML private Label lblSidebarAvatar;
    @FXML private Label lblSidebarUsername;
    @FXML private Label lblSidebarRole;

    @FXML private Label labelTotalAuctions;
    @FXML private Label labelRunningAuctions;
    @FXML private Label labelFinishedAuctions;
    @FXML private Label labelTotalUsers;
    @FXML private Label labelTotalBidders;
    @FXML private Label labelTotalSellers;

    @FXML private TabPane mainTabPane;
    @FXML private Button navAuctionsBtn;
    @FXML private Button navUsersBtn;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;

    @FXML
    public void initialize() {
        User currentUser = AppState.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (lblSidebarUsername != null) lblSidebarUsername.setText(currentUser.getUsername());
            if (lblSidebarRole != null) lblSidebarRole.setText("ADMIN");
            if (lblSidebarAvatar != null) {
                String firstChar = currentUser.getUsername().isEmpty() ? "A" : currentUser.getUsername().substring(0, 1).toUpperCase();
                lblSidebarAvatar.setText(firstChar);
            }
        }

        setupUserTable();

        // Bind vào ObservableList từ AppState — tự động cập nhật theo socket
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();
        
        auctions.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            javafx.application.Platform.runLater(() -> {
                updateAuctionStats();
                renderAuctions();
            });
        });

        loadUserData();
        updateAuctionStats();
        renderAuctions();

        userTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openUserDetails(row.getItem());
                }
            });
            return row;
        });

        // Yêu cầu server gửi lại danh sách đấu giá
        try {
            AppState.getInstance().getClient().send("REFRESH_DATA");
        } catch (Exception e) {
            System.err.println("Không thể yêu cầu refresh: " + e.getMessage());
        }
    }

    private void renderAuctions() {
        if (flowPaneAuctions == null) return;
        flowPaneAuctions.getChildren().clear();

        for (Auction auction : AppState.getInstance().getAuctionList()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_card.fxml"));
                javafx.scene.Node card = loader.load();
                
                ItemCardController cardController = loader.getController();
                cardController.setData(auction, 
                    this::openItemDetails, 
                    this::handleQuickBidOf);
                
                flowPaneAuctions.getChildren().add(card);
            } catch (IOException e) {
                System.err.println("Lỗi load item card (Admin): " + e.getMessage());
            }
        }
    }

    private void handleQuickBidOf(Auction auction) {
        // Admin thường không bid, nhưng nếu cần:
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();
            ((BidController)loader.getController()).setAuctionData(auction);
            Stage stage = new Stage();
            stage.setTitle("Admin Place Bid");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    void handleViewProfile() {
        openUserDetails(AppState.getInstance().getCurrentUser());
    }

    private void setupUserTable() {
        colUserId  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserId()));
        colUsername.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        colEmail   .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colRole    .setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getClass().getSimpleName().toUpperCase()));
        if (colBalance != null) {
            colBalance.setCellValueFactory(d -> new SimpleStringProperty(
                String.format("$%,.2f", d.getValue().getBalance())));
        }
    }

    private void updateAuctionStats() {
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();

        long open     = auctions.stream().filter(a -> "OPEN".equals(a.getStatus())).count();
        long running  = auctions.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
        long finished = auctions.stream().filter(a -> "FINISHED".equals(a.getStatus())).count();
        long finalized = auctions.stream().filter(a -> "PAID".equals(a.getStatus()) || "CANCELED".equals(a.getStatus())).count();

        if (labelTotalAuctions   != null) labelTotalAuctions  .setText("TOTAL: " + auctions.size() + " (OPEN: " + open + ")");
        if (labelRunningAuctions != null) labelRunningAuctions.setText("RUNNING: " + running);
        if (labelFinishedAuctions!= null) labelFinishedAuctions.setText("PAID/CANCELLED: " + (finished + finalized));
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

    /** Mở cửa sổ chi tiết phiên đấu giá */
    private void openItemDetails(Auction auction) {
        if (auction == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();

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

        if (mainTabPane != null && mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
            // Tab 1 = Quản lý người dùng
            handleViewUserDetails();
        } else {
            // Tab 0 = Phiên đấu giá
            // Với giao diện thẻ, Admin có thể click trực tiếp vào thẻ
            utils.AlertHelper.show(utils.AlertHelper.Type.INFO, "Hãy nhấn trực tiếp vào thẻ sản phẩm để xem chi tiết!");
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
    void handleNavAuctions() {
        if (mainTabPane != null) mainTabPane.getSelectionModel().select(0);
        setActiveNav(navAuctionsBtn, navUsersBtn);
        if (pageTitleLabel != null) pageTitleLabel.setText("Phiên đấu giá");
        if (pageSubtitleLabel != null) pageSubtitleLabel.setText("Tổng quan toàn bộ phiên đấu giá trong hệ thống");
    }

    @FXML
    void handleNavUsers() {
        if (mainTabPane != null) mainTabPane.getSelectionModel().select(1);
        setActiveNav(navUsersBtn, navAuctionsBtn);
        if (pageTitleLabel != null) pageTitleLabel.setText("Quản lý người dùng");
        if (pageSubtitleLabel != null) pageSubtitleLabel.setText("Danh sách tất cả tài khoản trong hệ thống");
    }

    private void setActiveNav(Button active, Button... others) {
        if (active != null && !active.getStyleClass().contains("sidebar-item-active")) {
            active.getStyleClass().add("sidebar-item-active");
        }
        for (Button b : others) {
            if (b != null) b.getStyleClass().removeAll("sidebar-item-active");
        }
    }

    @FXML
    void handleLogout() {
        AppState.getInstance().setCurrentUser(null);
        AppState.getInstance().getSceneManager().showLogin();
    }
    
    private void openUserDetails(User user) {
        if (user == null) return;
        try {
            System.out.println(">>> Admin opening user details for: " + user.getUsername());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_details.fxml"));
            Parent root = loader.load();

            UserDetailsController controller = loader.getController();
            if (controller != null) {
                controller.setUserData(user);
                
                Stage stage = new Stage();
                stage.setTitle("Thông tin người dùng: " + user.getUsername());
                stage.setScene(new Scene(root));
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.show();
            } else {
                System.err.println(">>> Lỗi: Không tìm thấy UserDetailsController");
            }
        } catch (Exception e) {
            System.err.println("Lỗi mở user details: " + e.getMessage());
            e.printStackTrace();
            utils.AlertHelper.show(utils.AlertHelper.Type.ERROR, "Không thể mở chi tiết người dùng: " + e.getMessage());
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