package controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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
    @FXML private TableColumn<User, String> colStatus;
    @FXML private TableColumn<User, String> colBalance;
    @FXML private TextField txtUserSearch;
    @FXML private TextField txtAuctionSearch;

    @FXML private ToggleGroup auctionStatusGroup;

    @FXML private javafx.scene.layout.VBox paneEmptyAuctions;
    @FXML private javafx.scene.layout.VBox paneEmptyUsers;

    private FilteredList<User> filteredUsers;
    private FilteredList<Auction> filteredAuctions;
    private final ObservableList<User> masterUsers = FXCollections.observableArrayList();

    @FXML private Label lblSidebarAvatar;
    @FXML private javafx.scene.shape.Circle sidebarAvatarClip;
    @FXML private javafx.scene.image.ImageView imgSidebarAvatar;
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
                String firstChar = currentUser.getUsername().isEmpty()
                        ? "A" : currentUser.getUsername().substring(0, 1).toUpperCase();
                lblSidebarAvatar.setText(firstChar);
                
                if (imgSidebarAvatar != null) {
                    if (sidebarAvatarClip != null) imgSidebarAvatar.setClip(sidebarAvatarClip);
                    
                    if (currentUser.getAvatarPath() != null && !currentUser.getAvatarPath().isEmpty()) {
                        String uri = utils.ImageStorageService.toFileUri(currentUser.getAvatarPath());
                        if (uri != null) {
                            imgSidebarAvatar.setImage(new javafx.scene.image.Image(uri));
                            imgSidebarAvatar.setVisible(true);
                            lblSidebarAvatar.setVisible(false);
                        } else {
                            imgSidebarAvatar.setVisible(false);
                            lblSidebarAvatar.setVisible(true);
                        }
                    } else {
                        imgSidebarAvatar.setVisible(false);
                        lblSidebarAvatar.setVisible(true);
                    }
                }
            }
        }

        // Lắng nghe các thông báo cập nhật người dùng từ Server để refresh bảng real-time
        AppState.getInstance().getClient().addStringMessageListener(msg -> {
            if (msg.equals("USERS_UPDATED") || msg.startsWith("NEW_SELLER_REQUEST:")) {
                javafx.application.Platform.runLater(this::loadUserData);
            }
        });

        setupUserTable();
        setupAuctionFiltering();

        // Bind vào ObservableList từ AppState — tự động cập nhật theo socket
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();
        
        auctions.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            javafx.application.Platform.runLater(() -> {
                updateAuctionStats();
                // Không cần gọi renderAuctions() ở đây nữa vì FilteredList sẽ lo việc đó
            });
        });

        loadUserData();
        updateAuctionStats();
        // renderAuctions(); // Sẽ được gọi thông qua listener của filteredAuctions

        userTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            
            // Highlight hàng nếu user đang chờ duyệt Seller
            row.itemProperty().addListener((obs, oldVal, newVal) -> {
                row.getStyleClass().remove("row-pending");
                if (newVal != null && newVal.isPendingSeller()) {
                    row.getStyleClass().add("row-pending");
                }
            });

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

    private void setupAuctionFiltering() {
        filteredAuctions = new FilteredList<>(AppState.getInstance().getAuctionList(), p -> true);
        
        // Listener khi danh sách đã lọc thay đổi -> render lại UI
        filteredAuctions.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            javafx.application.Platform.runLater(this::renderAuctions);
        });

        // Listener cho Search Box
        txtAuctionSearch.textProperty().addListener((obs, old, newValue) -> updateAuctionPredicate());

        // Listener cho Toggle Group (Bộ lọc trạng thái)
        auctionStatusGroup.selectedToggleProperty().addListener((obs, old, newValue) -> updateAuctionPredicate());

        renderAuctions(); // Lần đầu tiên
    }

    private void updateAuctionPredicate() {
        String searchText = txtAuctionSearch.getText() == null ? "" : txtAuctionSearch.getText().toLowerCase().trim();
        ToggleButton selectedTgl = (ToggleButton) auctionStatusGroup.getSelectedToggle();
        String statusFilter = selectedTgl == null ? "TẤT CẢ" : selectedTgl.getText().toUpperCase();

        filteredAuctions.setPredicate(auction -> {
            // 1. Kiểm tra search text
            boolean matchesSearch = searchText.isEmpty() || 
                                   auction.getItem().getItemName().toLowerCase().contains(searchText) ||
                                   auction.getAuctionId().toLowerCase().contains(searchText);

            if (!matchesSearch) return false;

            // 2. Kiểm tra status filter
            if (statusFilter.equals("TẤT CẢ")) return true;
            if (statusFilter.equals("SẮP DIỄN RA")) return "OPEN".equals(auction.getStatus());
            if (statusFilter.equals("ĐANG DIỄN RA")) return "RUNNING".equals(auction.getStatus());
            if (statusFilter.equals("ĐÃ KẾT THÚC")) {
                return !"OPEN".equals(auction.getStatus()) && !"RUNNING".equals(auction.getStatus());
            }

            return true;
        });
    }

    private void renderAuctions() {
        if (flowPaneAuctions == null) return;
        flowPaneAuctions.getChildren().clear();

        if (filteredAuctions.isEmpty()) {
            paneEmptyAuctions.setVisible(true);
            paneEmptyAuctions.setManaged(true);
        } else {
            paneEmptyAuctions.setVisible(false);
            paneEmptyAuctions.setManaged(false);

            for (Auction auction : filteredAuctions) {
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
    }

    @FXML
    void handleViewProfile() {
        openUserDetails(AppState.getInstance().getCurrentUser());
    }

    private void handleQuickBidOf(Auction auction) {
        auction = getLatestAuction(auction);
        if (auction == null) return;

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupUserTable() {
        // Cấu hình lọc dữ liệu
        filteredUsers = new FilteredList<>(masterUsers, p -> true);
        userTable.setItems(filteredUsers);
        userTable.setPlaceholder(new Label("")); // Xóa dòng "No content in table" mặc định

        // Listener để cập nhật Empty State khi danh sách thay đổi
        filteredUsers.addListener((javafx.collections.ListChangeListener<User>) c -> {
            boolean isEmpty = filteredUsers.isEmpty();
            if (paneEmptyUsers != null) {
                paneEmptyUsers.setVisible(isEmpty);
                paneEmptyUsers.setManaged(isEmpty);
            }
        });

        if (txtUserSearch != null) {
            txtUserSearch.textProperty().addListener((obs, old, newValue) -> {
                filteredUsers.setPredicate(user -> {
                    if (newValue == null || newValue.isEmpty()) return true;
                    String lowerCaseFilter = newValue.toLowerCase().trim();
                    if (user.getUsername().toLowerCase().contains(lowerCaseFilter)) return true;
                    if (user.getEmail().toLowerCase().contains(lowerCaseFilter)) return true;
                    if (user.getUserId().toLowerCase().contains(lowerCaseFilter)) return true;
                    return false;
                });
            });
        }

        colUserId  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserId()));
        colUsername.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        colEmail   .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));

        // Gán Style Class để căn lề Header (đồng bộ với CSS)
        colRole.getStyleClass().add("centered-column");
        colStatus.getStyleClass().add("centered-column");
        colBalance.getStyleClass().add("right-column");

        // 1. Role Column: Chỉ hiển thị badge Vai trò hiện tại
        colRole.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getClass().getSimpleName().toUpperCase()));
        colRole.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(role);
                    badge.getStyleClass().add("role-badge");
                    switch (role) {
                        case "ADMIN" -> badge.getStyleClass().add("role-admin");
                        case "SELLER" -> badge.getStyleClass().add("role-seller");
                        case "BIDDER" -> badge.getStyleClass().add("role-bidder");
                    }
                    setGraphic(badge);
                }
            }
        });

        // 2. Status Column: Tách riêng trạng thái xét duyệt
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().isPendingSeller() ? "PENDING" : "NORMAL"));
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    if ("PENDING".equals(status)) {
                        Label badge = new Label("CHỜ DUYỆT SELLER");
                        badge.getStyleClass().addAll("role-badge", "role-pending");
                        setGraphic(badge);
                    } else {
                        Label label = new Label("Bình thường");
                        label.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px;");
                        setGraphic(label);
                    }
                }
            }
        });

        // Balance Column với định dạng tiền tệ
        if (colBalance != null) {
            colBalance.setCellValueFactory(d -> new SimpleStringProperty(
                    String.format("$%,.2f", d.getValue().getBalance())));
            colBalance.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String balance, boolean empty) {
                    super.updateItem(balance, empty);
                    if (empty || balance == null) {
                        setText(null);
                        getStyleClass().remove("balance-cell-positive");
                    } else {
                        setText(balance);
                        if (!balance.startsWith("$0.00")) {
                            getStyleClass().add("balance-cell-positive");
                        } else {
                            getStyleClass().remove("balance-cell-positive");
                        }
                    }
                }
            });
        }
    }

    private void updateAuctionStats() {
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();

        long open     = auctions.stream().filter(a -> "OPEN".equals(a.getStatus())).count();
        long running  = auctions.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
        long finished = auctions.stream().filter(a -> "FINISHED".equals(a.getStatus())).count();
        long finalized = auctions.stream().filter(a -> "PAID".equals(a.getStatus()) || "CANCELED".equals(a.getStatus())).count();

        if (labelTotalAuctions   != null) labelTotalAuctions  .setText(String.valueOf(auctions.size()));
        if (labelRunningAuctions != null) labelRunningAuctions.setText(String.valueOf(running));
        if (labelFinishedAuctions!= null) labelFinishedAuctions.setText(String.valueOf(finished + finalized));
    }

    private void loadUserData() {
        try {
            List<User> allUsers = new UserDAO().findAll();
            masterUsers.setAll(allUsers);

            long bidders = allUsers.stream().filter(u -> u instanceof model.user.Bidder).count();
            long sellers = allUsers.stream().filter(u -> u instanceof model.user.Seller).count();

            if (labelTotalUsers   != null) labelTotalUsers  .setText(String.valueOf(allUsers.size()));
            if (labelTotalBidders != null) labelTotalBidders.setText(String.valueOf(bidders));
            if (labelTotalSellers != null) labelTotalSellers.setText(String.valueOf(sellers));
        } catch (Exception e) {
            System.err.println("Lỗi load users: " + e.getMessage());
        }
    }

    /** Mở cửa sổ chi tiết phiên đấu giá */
    private void openItemDetails(Auction auction) {
        auction = getLatestAuction(auction);
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

            // Giới hạn kích thước theo màn hình (Anti-overflow)
            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setMaxWidth(screenBounds.getWidth() * 0.96);
            stage.setMaxHeight(screenBounds.getHeight() * 0.96);

            stage.show();
        } catch (Exception e) {
            System.err.println("Lỗi mở chi tiết: " + e.getMessage());
            e.printStackTrace();
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
            utils.AlertHelper.show(utils.AlertHelper.Type.ERROR,
                    "Không thể mở chi tiết người dùng: " + e.getMessage());
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

    private Auction getLatestAuction(Auction fallback) {
        if (fallback == null) return null;

        for (Auction candidate : AppState.getInstance().getAuctionList()) {
            if (candidate.getAuctionId().equals(fallback.getAuctionId())) {
                return candidate;
            }
        }
        return fallback;
    }
}
