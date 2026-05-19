package controller;

import java.io.IOException;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.auction.Auction;
import model.manager.AppState;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

/**
 * Điều khiển màn hình Dashboard chính, quản lý danh sách đấu giá và tương tác người dùng.
 */
public class DashboardController {

    @FXML private Label titleLabel;
    @FXML private Label lblBalance;        // hiển thị số dư hiện tại
    @FXML private Label lblLockedBalance;  // hiển thị số dư bị khóa
    @FXML private TextField textSearch;
    @FXML private ComboBox<String> comboFilter;
    @FXML private javafx.scene.layout.FlowPane flowPaneAuctions;
    @FXML private javafx.scene.layout.VBox paneEmptyAuctions;
    @FXML private Label lblEmptyTitle;
    @FXML private Label lblEmptySubtitle;
    
    @FXML private Button createSessionButton;
    @FXML private Button btnTopUp;          // nút nạp tiền
    @FXML private Label sidebarUserLabel;
    @FXML private javafx.scene.shape.Circle sidebarAvatarClip;
    @FXML private javafx.scene.image.ImageView imgSidebarAvatar;
    @FXML private Label lblSidebarAvatar;
    @FXML private Label lblSidebarUsername;
    @FXML private Label lblSidebarRole;
    @FXML private javafx.scene.layout.VBox paneUserProfile;
    @FXML private javafx.scene.layout.StackPane bellNotif;
    @FXML private Label lblNotifBadge;

    // View Navigation
    @FXML private Button btnNavAuctions;
    @FXML private Button btnNavWallet;
    @FXML private javafx.scene.layout.VBox viewAuctions;
    @FXML private javafx.scene.layout.VBox viewWallet;

    // Wallet Section
    @FXML private Label lblWalletBalance;
    @FXML private Label lblWalletLocked;
    @FXML private javafx.scene.control.ListView<model.auction.WalletTransaction> listTransactions;
    @FXML private javafx.scene.layout.VBox paneAccountStatus;
    @FXML private Label lblCurrentRole;
    @FXML private Label lblSellerRequestStatus;
    @FXML private Button btnRequestSeller;

    private FilteredList<Auction> filteredData;
    private final ObservableList<model.auction.WalletTransaction> transactionRows = FXCollections.observableArrayList();
    private final java.time.format.DateTimeFormatter dateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Khởi tạo bộ lọc và thiết lập các bộ lắng nghe sự kiện (Listeners).
     */
    public void initialize() {
        // Khởi tạo các giá trị cho bộ lọc danh mục
        comboFilter.setItems(FXCollections.observableArrayList("All", "Electronics", "Art", "Vehicle"));
        comboFilter.setValue("All");

        // Setup personal transaction list
        setupTransactionList();

        // Lấy danh sách từ AppState để hiển thị
        ObservableList<Auction> masterData = AppState.getInstance().getAuctionList();
        filteredData = new FilteredList<>(masterData, p -> true);

        // Thêm ListChangeListener để cập nhật giao diện thông minh
        masterData.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            // Change chỉ hợp lệ trong chính callback này; nếu đẩy sang runLater,
            // các card có thể giữ reference cũ dù list đã có Auction mới.
            while (c.next()) {
                if (c.wasUpdated() || c.wasReplaced()) {
                    // Cập nhật từng thẻ bị thay đổi thay vì vẽ lại toàn bộ.
                    for (int i = c.getFrom(); i < c.getTo(); i++) {
                        updateSpecificCard(masterData.get(i));
                    }
                } else {
                    // Nếu là thêm/xóa/reset thì vẽ lại toàn bộ cho chắc chắn.
                    renderAuctions();
                }
            }
            updateOpenDetailWindow();
        });

        // Khi tìm kiếm or đổi loại hàng, bảng sẽ tự động update
        textSearch.textProperty().addListener((obs, old, newVal) -> {
            updatePredicate();
            renderAuctions();
        });
        comboFilter.valueProperty().addListener((obs, old, newVal) -> {
            updatePredicate();
            renderAuctions();
        });

        // Đăng ký nhận thông báo thay đổi số dư real-time từ Server
        AppState.getInstance().getClient().addStringMessageListener(msg -> {
            if (msg.startsWith("TOPUP_OK:") || msg.startsWith("PAY_OK:") || 
                msg.startsWith("AUTOBID_OK:") || msg.startsWith("CANCEL_AUTOBID_OK:") ||
                msg.startsWith("BALANCE_UPDATE:")) {
                
                // Trích xuất số dư mới nếu có trong message (thường là part cuối)
                String[] parts = msg.split(":");
                if (parts.length >= 2) {
                    try {
                        // Giả sử server gửi BALANCE_UPDATE:available:locked
                        if (msg.startsWith("BALANCE_UPDATE:")) {
                            double avail = Double.parseDouble(parts[1]);
                            double locked = Double.parseDouble(parts[2]);
                            User u = AppState.getInstance().getCurrentUser();
                            if (u != null) {
                                u.setBalance(avail);
                                u.setLockedBalance(locked);
                            }
                        } else {
                            // Các message cũ chỉ gửi 1 số dư (available)
                            double newAvail = Double.parseDouble(parts[parts.length - 1]);
                            User u = AppState.getInstance().getCurrentUser();
                            if (u != null) u.setBalance(newAvail);
                        }
                        javafx.application.Platform.runLater(this::refreshBalanceLabel);
                    } catch (Exception ignore) {
                    }
                }
            } else if (msg.equals("SELLER_APPROVED")) {
                javafx.application.Platform.runLater(() -> {
                    User u = AppState.getInstance().getCurrentUser();
                    if (u != null) {
                        // Reload user object from DB to get the correct subclass (Seller)
                        User updated = new database.UserDAO().findById(u.getUserId());
                        if (updated != null) {
                            AppState.getInstance().setCurrentUser(updated);
                        }
                        setupPermissions();
                        refreshWalletData();
                    }
                });
            } else if (msg.equals("SELLER_REVOKED")) {
                javafx.application.Platform.runLater(() -> {
                    User u = AppState.getInstance().getCurrentUser();
                    if (u != null) {
                        // Reload user object from DB to get the correct subclass (Bidder)
                        User updated = new database.UserDAO().findById(u.getUserId());
                        if (updated != null) {
                            AppState.getInstance().setCurrentUser(updated);
                        }
                        setupPermissions();
                        refreshWalletData();
                    }
                });
            }
        });

        // Kích hoạt các thiết lập bổ trợ cho giao diện
        setupPermissions();
        loadAuctionData();

        // Gắn Notification Center (bell + popup)
        if (bellNotif != null && lblNotifBadge != null) {
            utils.NotificationCenter.attach(bellNotif, lblNotifBadge);
        }

        // Render lần đầu
        renderAuctions();
    }

    /**
     * Cập nhật một thẻ sản phẩm cụ thể mà không vẽ lại toàn bộ.
     */
    private void updateSpecificCard(Auction auction) {
        if (flowPaneAuctions == null || auction == null) return;

        for (javafx.scene.Node node : flowPaneAuctions.getChildren()) {
            // Lấy controller từ thuộc tính của node (nếu có) hoặc tìm cách định danh
            Object userData = node.getUserData();
            if (userData instanceof ItemCardController cardController) {
                if (cardController.getAuctionId().equals(auction.getAuctionId())) {
                    cardController.setData(auction, this::handleViewDetailsOf, this::handleQuickBidOf);
                    return;
                }
            }
        }
        // Nếu không tìm thấy thẻ để update (ví dụ mới thêm), ta render lại toàn bộ cho an toàn
        renderAuctions();
    }

    /**
     * Vẽ lại danh sách thẻ sản phẩm dựa trên dữ liệu đã lọc.
     */
    private void renderAuctions() {
        if (flowPaneAuctions == null) return;
        flowPaneAuctions.getChildren().clear();

        if (filteredData.isEmpty()) {
            if (paneEmptyAuctions != null) {
                paneEmptyAuctions.setVisible(true);
                paneEmptyAuctions.setManaged(true);
            }
            boolean noAuctionsAtAll = AppState.getInstance().getAuctionList().isEmpty();
            if (lblEmptyTitle != null) {
                lblEmptyTitle.setText(noAuctionsAtAll
                        ? "Chưa có phiên đấu giá nào"
                        : "Không tìm thấy phiên đấu giá nào");
            }
            if (lblEmptySubtitle != null) {
                lblEmptySubtitle.setText(noAuctionsAtAll
                        ? "Các phiên đấu giá mới sẽ sớm xuất hiện ở đây"
                        : "Thử thay đổi từ khóa hoặc bộ lọc của bạn");
            }
        } else {
            if (paneEmptyAuctions != null) {
                paneEmptyAuctions.setVisible(false);
                paneEmptyAuctions.setManaged(false);
            }

            for (Auction auction : filteredData) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_card.fxml"));
                    javafx.scene.Node card = loader.load();
                    
                    ItemCardController cardController = loader.getController();
                    cardController.setData(auction, 
                        this::handleViewDetailsOf, 
                        this::handleQuickBidOf);
                    
                    // Lưu controller vào node để phục vụ updateSpecificCard
                    card.setUserData(cardController);
                    
                    flowPaneAuctions.getChildren().add(card);
                } catch (IOException e) {
                    System.err.println("Lỗi load item card: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Xác định hiển thị món hàng nào
     */
    private void updatePredicate() {
        filteredData.setPredicate(auction -> {
            if (auction == null || auction.getItem() == null) return false;

            String search = textSearch.getText().toLowerCase().trim();
            String filter = comboFilter.getValue();

            // Khớp loại hàng và tên thì hiển thị
            boolean matchesType = filter.equals("All") || auction.getItem().getItemType().equalsIgnoreCase(filter);
            boolean matchesName = auction.getItem().getItemName().toLowerCase().contains(search);

            return matchesType && matchesName;
        });
    }

    /**
     * Gửi tín hiệu yêu cầu Server cập nhật lại toàn bộ danh sách phiên đấu giá mới nhất.
     */
    @FXML
    private void loadAuctionData() {
        AppState.getInstance().getClient().send("REFRESH_DATA");
    }

    /**
     * Kiểm tra vai trò của người dùng (Admin, Seller, Bidder) để ẩn/hiện các tính năng phù hợp.
     */
    private void setupPermissions() {
        User user = AppState.getInstance().getCurrentUser();
        if (user != null) {
            titleLabel.setText("Welcome, " + user.getUsername());
            
            // Cập nhật Widget Profile Sidebar (Giao diện mới)
            if (lblSidebarUsername != null) lblSidebarUsername.setText(user.getUsername());
            if (lblSidebarRole != null) lblSidebarRole.setText(user.getClass().getSimpleName().toUpperCase());
            if (lblSidebarAvatar != null) {
                String firstChar = user.getUsername().isEmpty() ? "?" : user.getUsername().substring(0, 1).toUpperCase();
                lblSidebarAvatar.setText(firstChar);
                
                if (imgSidebarAvatar != null) {
                    if (sidebarAvatarClip != null) imgSidebarAvatar.setClip(sidebarAvatarClip);
                    
                    if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
                        String uri = utils.ImageStorageService.toFileUri(user.getAvatarPath());
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

            // Mọi user đều được xem số dư và nạp tiền
            if (btnTopUp != null) {
                btnTopUp.setVisible(true);
                btnTopUp.setManaged(true);
            }

            boolean canCreate = (user instanceof Admin || user instanceof Seller);
            createSessionButton.setVisible(canCreate);
            createSessionButton.setManaged(canCreate);
        }
        refreshBalanceLabel();
    }

    private void refreshSidebarAvatar() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || imgSidebarAvatar == null) return;
        if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            String uri = utils.ImageStorageService.toFileUri(user.getAvatarPath());
            if (uri != null) {
                imgSidebarAvatar.setImage(new javafx.scene.image.Image(uri, true));
                imgSidebarAvatar.setVisible(true);
                if (lblSidebarAvatar != null) lblSidebarAvatar.setVisible(false);
            }
        }
    }

    /**
     * Cập nhật label hiển thị số dư khả dụng và số dư bị khóa.
     * Gọi sau khi nạp tiền, bid hoặc thanh toán xong để UI khớp với DB.
     */
    public void refreshBalanceLabel() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        
        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        }
        if (lblLockedBalance != null) {
            lblLockedBalance.setText(String.format("%,.0f ₫", user.getLockedBalance()));
        }
    }

    /**
     * Mở dialog nạp tiền.
     */
    @FXML
    void handleTopUp() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/topup_dialog.fxml"));
            javafx.scene.Parent root = loader.load();

            controller.TopUpController controller = loader.getController();
            // Khi nạp xong, cập nhật label balance trong dashboard
            controller.setOnTopUpSuccess(this::refreshBalanceLabel);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Nạp tiền vào ví");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleLogout() {
        utils.NotificationCenter.reset();
        AppState.getInstance().setCurrentUser(null);
        AppState.getInstance().getSceneManager().showLogin();
    }

    /**
     * Mở cửa sổ Pop-up để người dùng nhập thông tin và tạo một phiên đấu giá mới.
     */
    @FXML
    void handleCreateNewSession() {
        openPopup("/view/create_session.fxml", "Create New Auction Session");
        loadAuctionData();
    }

    /**
     * Mở hộp thoại bid cho một phiên đấu giá cụ thể (gọi từ ItemCard).
     */
    private void handleQuickBidOf(Auction auction) {
        auction = getLatestAuction(auction);
        if (auction == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();
            ((BidController)loader.getController()).setAuctionData(auction);
            
            Stage stage = new Stage();
            stage.setTitle("Place Your Bid");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) { 
            e.printStackTrace(); 
        }
    }

    private ItemDetailsController currentDetailController;
    private Stage currentDetailStage;

    /**
     * Hiển thị màn hình chi tiết cho một phiên cụ thể (gọi từ ItemCard).
     */
    private void handleViewDetailsOf(Auction auction) {
        auction = getLatestAuction(auction);
        if (auction == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();

            currentDetailController = loader.getController();
            currentDetailController.setItemData(auction);

            currentDetailStage = new Stage();
            currentDetailStage.setTitle("Item Details");
            currentDetailStage.setScene(new Scene(root));
            currentDetailStage.initModality(Modality.APPLICATION_MODAL);

            // Tự động điều chỉnh kích thước ban đầu để không vượt quá màn hình (Anti-overflow)
            // nhưng không dùng setMaxWidth để tránh lỗi khi người dùng nhấn Maximize cửa sổ.
            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            double initialWidth = Math.min(1120, screenBounds.getWidth() * 0.96);
            double initialHeight = Math.min(880, screenBounds.getHeight() * 0.96);
            
            currentDetailStage.setWidth(initialWidth);
            currentDetailStage.setHeight(initialHeight);

            currentDetailStage.setOnHidden(e -> {
                currentDetailController = null;
                currentDetailStage = null;
            });

            currentDetailStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Mở dialog xem thông tin cá nhân của chính mình.
     */
    @FXML
    void handleViewProfile() {
        try {
            System.out.println(">>> Attempting to open profile for user: "
                    + AppState.getInstance().getCurrentUser().getUsername());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_details.fxml"));
            Parent root = loader.load();
            
            UserDetailsController controller = loader.getController();
            controller.setUserData(AppState.getInstance().getCurrentUser());
            controller.setOnAvatarChanged(this::refreshSidebarAvatar);

            Stage stage = new Stage();
            stage.setTitle("Thông tin cá nhân");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) {
            System.err.println("Lỗi mở profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cập nhật dữ liệu cho cửa sổ chi tiết đang mở nếu có thay đổi từ Server.
     */
    private void updateOpenDetailWindow() {
        if (currentDetailController != null && currentDetailStage != null && currentDetailStage.isShowing()) {
            Auction current = currentDetailController.getAuction();
            if (current != null) {
                // Tìm auction mới nhất trong list dựa trên ID
                for (Auction a : AppState.getInstance().getAuctionList()) {
                    if (a.getAuctionId().equals(current.getAuctionId())) {
                        javafx.application.Platform.runLater(() -> {
                            currentDetailController.setItemData(a);
                        });
                        break;
                    }
                }
            }
        }
    }

    /**
     * Tìm và mở file giao diện (FXML) dưới dạng pop-up
     */
    private void openPopup(String fxml, String title) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            showStage(root, title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Tạo cửa sổ mới và bắt người dùng phải xử lý xong mới được quay lại bảng chính.
     */
    private void showStage(Parent root, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
        renderAuctions();
    }

    /**
     * Luôn mở từ bản Auction mới nhất trong AppState, tránh card cũ giữ reference cũ.
     */
    private Auction getLatestAuction(Auction fallback) {
        if (fallback == null) return null;

        for (Auction candidate : AppState.getInstance().getAuctionList()) {
            if (candidate.getAuctionId().equals(fallback.getAuctionId())) {
                return candidate;
            }
        }
        return fallback;
    }

    // =========================================================================
    // WALLET & VIEW NAVIGATION LOGIC
    // =========================================================================

    @FXML
    void handleNavAuctions() {
        switchView(true);
    }

    @FXML
    void handleNavWallet() {
        switchView(false);
        refreshWalletData();
    }

    private void switchView(boolean showAuctions) {
        viewAuctions.setVisible(showAuctions);
        viewAuctions.setManaged(showAuctions);
        viewWallet.setVisible(!showAuctions);
        viewWallet.setManaged(!showAuctions);

        // Update sidebar active state
        btnNavAuctions.getStyleClass().removeAll("sidebar-item-active");
        btnNavWallet.getStyleClass().removeAll("sidebar-item-active");

        if (showAuctions) {
            btnNavAuctions.getStyleClass().add("sidebar-item-active");
        } else {
            btnNavWallet.getStyleClass().add("sidebar-item-active");
        }
    }

    private void refreshWalletData() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;

        lblWalletBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        lblWalletLocked.setText(String.format("%,.0f ₫", user.getLockedBalance()));
        
        // Cập nhật trạng thái tài khoản
        if (paneAccountStatus != null) {
            lblCurrentRole.setText(user.getClass().getSimpleName().toUpperCase());
            if (user instanceof Seller || user instanceof Admin) {
                lblSellerRequestStatus.setText("Tài khoản của bạn đã có quyền đăng bán sản phẩm.");
                btnRequestSeller.setVisible(false);
                btnRequestSeller.setManaged(false);
            } else if (user.isPendingSeller()) {
                lblSellerRequestStatus.setText("Yêu cầu trở thành Seller của bạn đang được Admin xét duyệt.");
                btnRequestSeller.setDisable(true);
                btnRequestSeller.setText("Đang chờ duyệt...");
            } else {
                lblSellerRequestStatus.setText(
                        "Bạn có thể đăng ký để trở thành người bán (Seller) để đăng các sản phẩm của riêng mình.");
                btnRequestSeller.setVisible(true);
                btnRequestSeller.setManaged(true);
                btnRequestSeller.setDisable(false);
                btnRequestSeller.setText("Trở thành Seller?");
            }
        }

        // Fetch wallet transactions from DB
        new Thread(() -> {
            var transactions = new database.WalletTransactionDAO().findByUserId(user.getUserId());
            javafx.application.Platform.runLater(() -> {
                transactionRows.setAll(transactions);
            });
        }).start();
    }

    @FXML
    void handleRequestSeller() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || !(user instanceof Bidder) || user instanceof Seller) return;

        boolean confirm = utils.AlertHelper.showConfirm(
            "Xác nhận yêu cầu",
            "Bạn có chắc chắn muốn gửi yêu cầu trở thành Seller không? "
                    + "Sau khi gửi, Admin sẽ xét duyệt hồ sơ của bạn."
        );

        if (confirm) {
            AppState.getInstance().getClient().send("REQUEST_SELLER:" + user.getUserId());
            user.setPendingSeller(true);
            refreshWalletData();
            
            utils.AlertHelper.show(
                utils.AlertHelper.Type.SUCCESS,
                "Đã gửi yêu cầu",
                "Yêu cầu của bạn đã được gửi tới quản trị viên."
            );
        }
    }

    private void setupTransactionList() {
        if (listTransactions == null) return;

        listTransactions.setItems(transactionRows);
        listTransactions.setPlaceholder(new Label("Bạn chưa có giao dịch tài chính nào."));
        listTransactions.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(model.auction.WalletTransaction tx, boolean empty) {
                super.updateItem(tx, empty);
                if (empty || tx == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setGraphic(createTransactionRow(tx));
                    setStyle("-fx-background-color: transparent; -fx-padding: 6 0 6 0;");
                }
            }
        });
    }

    private javafx.scene.Node createTransactionRow(model.auction.WalletTransaction tx) {
        javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(15);
        container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        container.getStyleClass().add("bid-feed-item");

        // 1. Icon & Style based on Type
        Label lblIcon = new Label();
        lblIcon.getStyleClass().add("bid-avatar");
        
        String symbol = tx.getAmount() > 0 ? "+" : "";
        String colorStyle = "-fx-text-fill: white;";
        
        switch (tx.getType()) {
            case TOPUP -> {
                lblIcon.setText("💰");
                lblIcon.setStyle("-fx-background-color: #10B981;" + colorStyle);
            }
            case PAYMENT -> {
                lblIcon.setText("🛒");
                lblIcon.setStyle("-fx-background-color: #EF4444;" + colorStyle);
            }
            case EARNING -> {
                lblIcon.setText("📈");
                lblIcon.setStyle("-fx-background-color: #3B82F6;" + colorStyle);
            }
            case REFUND -> {
                lblIcon.setText("🔄");
                lblIcon.setStyle("-fx-background-color: #F59E0B;" + colorStyle);
            }
        }

        // 2. Info
        javafx.scene.layout.VBox vContent = new javafx.scene.layout.VBox(2);
        Label lblTitle = new Label(tx.getDescription());
        lblTitle.getStyleClass().add("bid-user-name");

        Label lblTime = new Label(tx.getCreatedAt().format(dateTimeFormatter));
        lblTime.getStyleClass().add("bid-feed-time");
        vContent.getChildren().addAll(lblTitle, lblTime);

        // 3. Amount
        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        
        Label lblAmount = new Label(String.format("%s%,.0f ₫", symbol, tx.getAmount()));
        lblAmount.getStyleClass().add("bid-feed-amount");
        
        if (tx.getAmount() < 0) {
            lblAmount.setStyle("-fx-text-fill: #EF4444;");
        } else {
            lblAmount.setStyle("-fx-text-fill: #10B981;");
        }

        container.getChildren().addAll(lblIcon, vContent, spacer, lblAmount);
        return container;
    }
}

