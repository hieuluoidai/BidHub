package controller;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Modality;
import javafx.stage.Stage;

import model.auction.Auction;
import model.auction.DepositRequest;
import model.manager.AppState;
import model.notification.Notification;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

import database.DepositRequestDAO;
import database.UserDAO;

import utils.AlertHelper;
import utils.ChatCenter;
import utils.ImageStorageService;
import utils.NotificationCenter;

/**
 * Điều khiển màn hình Admin Dashboard, quản lý toàn bộ hệ thống real-time.
 */
public class AdminController {

    @FXML private javafx.scene.layout.FlowPane flowPaneAuctions;
    @FXML private javafx.scene.layout.VBox paneEndedSection;
    @FXML private javafx.scene.layout.FlowPane flowPaneEnded;

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
    @FXML private javafx.scene.layout.StackPane bellNotif;
    @FXML private Label lblNotifBadge;

    @FXML private Label labelTotalAuctions;
    @FXML private Label labelRunningAuctions;
    @FXML private Label labelFinishedAuctions;
    @FXML private Label labelTotalUsers;
    @FXML private Label labelTotalBidders;
    @FXML private Label labelTotalSellers;

    @FXML private TableView<DepositRequest> depositTable;
    @FXML private TableColumn<DepositRequest, String> colDepositRefCode;
    @FXML private TableColumn<DepositRequest, String> colDepositUsername;
    @FXML private TableColumn<DepositRequest, String> colDepositAmount;
    @FXML private TableColumn<DepositRequest, String> colDepositTime;
    @FXML private TableColumn<DepositRequest, String> colDepositAction;
    @FXML private javafx.scene.layout.VBox paneEmptyDeposits;
    @FXML private Label lblDepositCount;

    @FXML private TabPane mainTabPane;
    @FXML private Button navAuctionsBtn;
    @FXML private Button navUsersBtn;
    @FXML private Button navDepositsBtn;
    @FXML private Button navMessagesBtn;
    @FXML private Label lblSidebarChatBadge;
    @FXML private Button btnCreateSession;
    @FXML private MessagesController messagesViewController;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;

    private javafx.beans.value.ChangeListener<Number> chatBadgeListener;

    private final ObservableList<DepositRequest> depositList = FXCollections.observableArrayList();
    private Set<String> pendingDepositUserIds = new HashSet<>();

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @FXML
    public void initialize() {
        User currentUser = AppState.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (lblSidebarUsername != null) {
                lblSidebarUsername.setText(currentUser.getUsername());
            }
            if (lblSidebarRole != null) {
                lblSidebarRole.setText("ADMIN");
            }
            if (lblSidebarAvatar != null) {
                String firstChar = currentUser.getUsername().isEmpty()
                        ? "A"
                        : currentUser.getUsername().substring(0, 1).toUpperCase();
                lblSidebarAvatar.setText(firstChar);
                if (imgSidebarAvatar != null) {
                    if (sidebarAvatarClip != null) {
                        imgSidebarAvatar.setClip(sidebarAvatarClip);
                    }
                    if (currentUser.getAvatarPath() != null
                            && !currentUser.getAvatarPath().isEmpty()) {
                        String uri = ImageStorageService.toFileUri(currentUser.getAvatarPath());
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

        AppState.getInstance().getClient().addStringMessageListener(msg -> {
            if (msg.equals("USERS_UPDATED") || msg.startsWith("NEW_SELLER_REQUEST:")) {
                javafx.application.Platform.runLater(this::loadUserData);
            }
            if (msg.startsWith("NEW_DEPOSIT_REQUEST:") || msg.equals("USERS_UPDATED")) {
                javafx.application.Platform.runLater(this::loadDepositData);
            }
        });

        setupUserTable();
        setupAuctionFiltering();
        setupDepositTable();

        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();
        auctions.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            javafx.application.Platform.runLater(() -> {
                updateAuctionStats();
                if (userTable != null) {
                    userTable.refresh();
                }
            });
        });

        loadUserData();
        loadDepositData();
        updateAuctionStats();

        userTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
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

        try {
            AppState.getInstance().getClient().send("REFRESH_DATA");
        } catch (Exception e) {
            System.err.println("Không thể yêu cầu refresh: " + e.getMessage());
        }

        if (bellNotif != null && lblNotifBadge != null) {
            NotificationCenter.attach(bellNotif, lblNotifBadge, this::handleNotificationAction);
        }

        // Chat badge trên sidebar
        ChatCenter.init();
        chatBadgeListener = (obs, oldV, newV) ->
                javafx.application.Platform.runLater(() -> updateChatBadge(newV.intValue()));
        AppState.getInstance().totalUnreadChatProperty().addListener(chatBadgeListener);
        updateChatBadge(AppState.getInstance().getTotalUnreadChat());

        // Hook để ItemDetails mở chat ngay trong admin dashboard
        AppState.getInstance().setOpenChatHook(args ->
                javafx.application.Platform.runLater(() ->
                        openChatWith(args[0], args[1], args[2])));

        AppState.getInstance().getStarredAuctionIds().addListener(
                (javafx.collections.SetChangeListener<String>) c ->
                        javafx.application.Platform.runLater(this::renderAuctions));
    }

    private void handleNotificationAction(Notification n) {
        if (n.getType() == Notification.Type.ADMIN_NEW_SELLER_REQUEST) {
            String msg = n.getMessage();
            if (msg != null && msg.contains("Người dùng ") && msg.contains(" đang chờ")) {
                int start = msg.indexOf("Người dùng ") + "Người dùng ".length();
                int end = msg.indexOf(" đang chờ");
                if (start < end) {
                    navigateToUser(msg.substring(start, end).trim(), true);
                }
            }
        } else if (n.getType() == Notification.Type.ADMIN_DEPOSIT_REQUEST) {
            handleNavDeposits();
        } else if (n.getType() == Notification.Type.ADMIN_NEW_USER) {
            handleNavUsers();
        }
    }

    private void navigateToUser(String username, boolean autoApprove) {
        handleNavUsers();
        User found = null;
        for (User u : masterUsers) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                found = u;
                break;
            }
        }
        if (found != null) {
            userTable.getSelectionModel().select(found);
            userTable.scrollTo(found);
            openUserDetails(found, autoApprove);
        } else {
            try {
                User u = new UserDAO().findByUsername(username);
                if (u != null) {
                    openUserDetails(u, autoApprove);
                }
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    private void setupAuctionFiltering() {
        filteredAuctions = new FilteredList<>(AppState.getInstance().getAuctionList(), p -> true);
        filteredAuctions.addListener((javafx.collections.ListChangeListener<Auction>) c ->
                javafx.application.Platform.runLater(this::renderAuctions));
        txtAuctionSearch.textProperty().addListener(
                (obs, old, newValue) -> updateAuctionPredicate());
        auctionStatusGroup.selectedToggleProperty().addListener(
                (obs, old, newValue) -> updateAuctionPredicate());
        renderAuctions();
    }

    private void updateAuctionPredicate() {
        String searchText = txtAuctionSearch.getText() == null
                ? ""
                : txtAuctionSearch.getText().toLowerCase().trim();
        ToggleButton selectedTgl = (ToggleButton) auctionStatusGroup.getSelectedToggle();
        String statusFilter = selectedTgl == null ? "TẤT CẢ" : selectedTgl.getText().toUpperCase();
        filteredAuctions.setPredicate(auction -> {
            boolean matchesSearch = searchText.isEmpty()
                    || auction.getItem().getItemName().toLowerCase().contains(searchText)
                    || auction.getAuctionId().toLowerCase().contains(searchText);
            if (!matchesSearch) {
                return false;
            }
            if (statusFilter.equals("TẤT CẢ")) {
                return true;
            }
            if (statusFilter.equals("SẮP DIỄN RA")) {
                return "OPEN".equals(auction.getStatus());
            }
            if (statusFilter.equals("ĐANG DIỄN RA")) {
                return "RUNNING".equals(auction.getStatus());
            }
            if (statusFilter.equals("ĐÃ KẾT THÚC")) {
                return isAuctionEnded(auction);
            }
            return true;
        });
    }

    private void renderAuctions() {
        if (flowPaneAuctions == null || flowPaneEnded == null) {
            return;
        }
        flowPaneAuctions.getChildren().clear();
        flowPaneEnded.getChildren().clear();

        if (filteredAuctions.isEmpty()) {
            showEmptyState(true);
            return;
        }
        showEmptyState(false);

        List<Auction> activeList = new ArrayList<>();
        List<Auction> endedList = new ArrayList<>();
        for (Auction a : filteredAuctions) {
            if (isAuctionEnded(a)) {
                endedList.add(a);
            } else {
                activeList.add(a);
            }
        }

        activeList.sort(this::compareAuctions);
        endedList.sort(this::compareAuctions);

        for (Auction a : activeList) {
            flowPaneAuctions.getChildren().add(createCard(a));
        }
        if (!endedList.isEmpty()) {
            paneEndedSection.setVisible(true);
            paneEndedSection.setManaged(true);
            for (Auction a : endedList) {
                flowPaneEnded.getChildren().add(createCard(a));
            }
        } else {
            paneEndedSection.setVisible(false);
            paneEndedSection.setManaged(false);
        }
    }

    private int compareAuctions(Auction a, Auction b) {
        boolean starA = AppState.getInstance().isStarred(a.getAuctionId());
        boolean starB = AppState.getInstance().isStarred(b.getAuctionId());
        if (starA && !starB) {
            return -1;
        }
        if (!starA && starB) {
            return 1;
        }
        return a.getAuctionId().compareTo(b.getAuctionId());
    }

    private boolean isAuctionEnded(Auction a) {
        String s = a.getStatus();
        return "FINISHED".equals(s) || "PAID".equals(s) || "CANCELED".equals(s);
    }

    private void showEmptyState(boolean empty) {
        if (paneEmptyAuctions != null) {
            paneEmptyAuctions.setVisible(empty);
            paneEmptyAuctions.setManaged(empty);
        }
        if (empty) {
            paneEndedSection.setVisible(false);
            paneEndedSection.setManaged(false);
        }
    }

    private javafx.scene.Node createCard(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_card.fxml"));
            javafx.scene.Node card = loader.load();
            ItemCardController cardController = loader.getController();
            cardController.setData(auction, this::openItemDetails, this::handleQuickBidOf);
            card.setUserData(cardController);
            return card;
        } catch (IOException e) {
            return new Label("Error");
        }
    }

    @FXML
    void handleViewProfile() {
        openUserDetails(AppState.getInstance().getCurrentUser());
    }

    private void handleQuickBidOf(Auction auction) {
        auction = getLatestAuction(auction);
        if (auction == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();
            ((BidController) loader.getController()).setAuctionData(auction);
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
        filteredUsers = new FilteredList<>(masterUsers, p -> true);
        userTable.setItems(filteredUsers);
        userTable.setPlaceholder(new Label(""));
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
                    if (newValue == null || newValue.isEmpty()) {
                        return true;
                    }
                    String filter = newValue.toLowerCase().trim();
                    return user.getUsername().toLowerCase().contains(filter)
                            || user.getEmail().toLowerCase().contains(filter)
                            || user.getUserId().toLowerCase().contains(filter);
                });
            });
        }
        colUserId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserId()));
        colUsername.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().getUsername()));
        colUsername.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String username, boolean empty) {
                super.updateItem(username, empty);
                if (empty || username == null) {
                    setGraphic(null);
                    return;
                }
                User u = getTableRow().getItem();
                javafx.scene.layout.StackPane avatar = new javafx.scene.layout.StackPane();
                avatar.setMinSize(36, 36);
                avatar.setMaxSize(36, 36);
                String color = (u instanceof Admin) ? "#3B82F6"
                        : (u instanceof Seller) ? "#8B5CF6" : "#10B981";
                Label lblInit = new Label(
                        username.isEmpty() ? "?" : username.substring(0, 1).toUpperCase());
                lblInit.setStyle(
                        "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
                avatar.setStyle(
                        "-fx-background-color: " + color + "; -fx-background-radius: 50%;");
                avatar.getChildren().add(lblInit);
                if (u != null && u.getAvatarPath() != null && !u.getAvatarPath().isEmpty()) {
                    String uri = ImageStorageService.toFileUri(u.getAvatarPath());
                    if (uri != null) {
                        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                                new javafx.scene.image.Image(uri, 36, 36, true, true));
                        iv.setFitWidth(36);
                        iv.setFitHeight(36);
                        iv.setClip(new javafx.scene.shape.Circle(18, 18, 18));
                        avatar.getChildren().add(iv);
                    }
                }
                Label lblName = new Label(username);
                lblName.setStyle("-fx-font-size: 13px;");
                javafx.scene.layout.HBox box =
                        new javafx.scene.layout.HBox(10, avatar, lblName);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colRole.setCellValueFactory(
                d -> new SimpleStringProperty(
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
                    if ("ADMIN".equals(role)) {
                        badge.getStyleClass().add("role-admin");
                    } else if ("SELLER".equals(role)) {
                        badge.getStyleClass().add("role-seller");
                    } else {
                        badge.getStyleClass().add("role-bidder");
                    }
                    setGraphic(badge);
                }
            }
        });
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserId()));
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String userId, boolean empty) {
                super.updateItem(userId, empty);
                if (empty || userId == null
                        || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                User user = getTableRow().getItem();
                List<javafx.scene.Node> badges = buildStatusBadges(user);
                if (badges.isEmpty()) {
                    Label label = new Label("Bình thường");
                    label.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px;");
                    setGraphic(label);
                } else {
                    javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane();
                    flow.getStyleClass().add("status-badge-flow");
                    flow.getChildren().addAll(badges);
                    setGraphic(flow);
                }
            }
        });
        if (colBalance != null) {
            colBalance.setCellValueFactory(
                    d -> new SimpleStringProperty(
                            String.format("%,.0f ₫", d.getValue().getBalance())));
            colBalance.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String balance, boolean empty) {
                    super.updateItem(balance, empty);
                    if (empty || balance == null) {
                        setText(null);
                        getStyleClass().remove("balance-cell-positive");
                    } else {
                        setText(balance);
                        if (!balance.startsWith("0 ₫")) {
                            getStyleClass().add("balance-cell-positive");
                        } else {
                            getStyleClass().remove("balance-cell-positive");
                        }
                    }
                }
            });
        }
    }

    private List<javafx.scene.Node> buildStatusBadges(User user) {
        List<javafx.scene.Node> badges = new ArrayList<>();
        if (user.isPendingSeller()) {
            badges.add(makeBadge("CHỜ DUYỆT SELLER", "role-pending"));
        }
        if (pendingDepositUserIds.contains(user.getUserId())) {
            badges.add(makeBadge("CHỜ NẠP TIỀN", "role-deposit"));
        }
        String uid = user.getUserId();
        boolean hosting = false;
        for (Auction a : AppState.getInstance().getAuctionList()) {
            if (!"RUNNING".equals(a.getStatus())) {
                continue;
            }
            if (uid.equals(a.getSellerId())) {
                hosting = true;
                break;
            }
        }
        if (hosting) {
            badges.add(makeBadge("ĐANG MỞ PHIÊN", "role-hosting"));
        }
        return badges;
    }

    private Label makeBadge(String text, String colorClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("role-badge", colorClass);
        return badge;
    }

    private void updateAuctionStats() {
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();
        long running = auctions.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
        long finished = auctions.stream().filter(a -> "FINISHED".equals(a.getStatus())).count();
        long finalized = auctions.stream()
                .filter(a -> "PAID".equals(a.getStatus()) || "CANCELED".equals(a.getStatus()))
                .count();
        if (labelTotalAuctions != null) {
            labelTotalAuctions.setText(String.valueOf(auctions.size()));
        }
        if (labelRunningAuctions != null) {
            labelRunningAuctions.setText(String.valueOf(running));
        }
        if (labelFinishedAuctions != null) {
            labelFinishedAuctions.setText(String.valueOf(finished + finalized));
        }
    }

    private void loadUserData() {
        try {
            pendingDepositUserIds = new HashSet<>(new DepositRequestDAO().getPendingUserIds());
            List<User> allUsers = new UserDAO().findAll();
            masterUsers.setAll(allUsers);
            long bidders = allUsers.stream().filter(u -> u instanceof Bidder).count();
            long sellers = allUsers.stream().filter(u -> u instanceof Seller).count();
            if (labelTotalUsers != null) {
                labelTotalUsers.setText(String.valueOf(allUsers.size()));
            }
            if (labelTotalBidders != null) {
                labelTotalBidders.setText(String.valueOf(bidders));
            }
            if (labelTotalSellers != null) {
                labelTotalSellers.setText(String.valueOf(sellers));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openItemDetails(Auction auction) {
        auction = getLatestAuction(auction);
        if (auction == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();
            ItemDetailsController controller = loader.getController();
            controller.setItemData(auction);
            Stage stage = new Stage();
            stage.setTitle("Chi tiết phiên: " + auction.getAuctionId());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setWidth(Math.min(1120, screenBounds.getWidth() * 0.96));
            stage.setHeight(Math.min(880, screenBounds.getHeight() * 0.96));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleCreateSession() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/create_session.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Tạo phiên đấu giá mới");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            updateAuctionStats();
        } catch (Exception e) {
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
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().select(0);
        }
        setActiveNav(navAuctionsBtn, navUsersBtn, navDepositsBtn, navMessagesBtn);
        if (pageTitleLabel != null) {
            pageTitleLabel.setText("Phiên đấu giá");
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText("Tổng quan toàn bộ phiên đấu giá trong hệ thống");
        }
        if (btnCreateSession != null) {
            btnCreateSession.setVisible(true);
            btnCreateSession.setManaged(true);
        }
    }

    @FXML
    void handleNavUsers() {
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().select(1);
        }
        setActiveNav(navUsersBtn, navAuctionsBtn, navDepositsBtn, navMessagesBtn);
        if (pageTitleLabel != null) {
            pageTitleLabel.setText("Quản lý người dùng");
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText("Danh sách tất cả tài khoản trong hệ thống");
        }
        if (btnCreateSession != null) {
            btnCreateSession.setVisible(false);
            btnCreateSession.setManaged(false);
        }
    }

    @FXML
    void handleNavMessages() {
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().select(3);
        }
        setActiveNav(navMessagesBtn, navAuctionsBtn, navUsersBtn, navDepositsBtn);
        if (pageTitleLabel != null) {
            pageTitleLabel.setText("Tin nhắn & Bạn bè");
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText("Nhắn tin và quản lý kết bạn");
        }
        if (btnCreateSession != null) {
            btnCreateSession.setVisible(false);
            btnCreateSession.setManaged(false);
        }
        if (messagesViewController != null) {
            messagesViewController.refreshSummaries();
        }
    }

    /** Mở chat với 1 user — gọi từ AppState.openChatHook (vd: ItemDetailsController). */
    public void openChatWith(String partnerId, String partnerUsername, String partnerAvatarPath) {
        handleNavMessages();
        if (messagesViewController != null) {
            messagesViewController.openChatWith(partnerId, partnerUsername, partnerAvatarPath);
        }
    }

    private void updateChatBadge(int unread) {
        if (lblSidebarChatBadge == null) return;
        if (unread <= 0) {
            lblSidebarChatBadge.setVisible(false);
            lblSidebarChatBadge.setManaged(false);
        } else {
            lblSidebarChatBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            lblSidebarChatBadge.setVisible(true);
            lblSidebarChatBadge.setManaged(true);
        }
    }

    private void setActiveNav(Button active, Button... others) {
        if (active != null && !active.getStyleClass().contains("sidebar-item-active")) {
            active.getStyleClass().add("sidebar-item-active");
        }
        for (Button b : others) {
            if (b != null) {
                b.getStyleClass().removeAll("sidebar-item-active");
            }
        }
    }

    @FXML
    void handleLogout() {
        if (chatBadgeListener != null) {
            AppState.getInstance().totalUnreadChatProperty().removeListener(chatBadgeListener);
        }
        if (messagesViewController != null) {
            messagesViewController.detach();
        }
        AppState.getInstance().setOpenChatHook(null);
        NotificationCenter.reset();
        AppState.getInstance().setCurrentUser(null);
        AppState.getInstance().getSceneManager().showLogin();
    }

    private void openUserDetails(User user) {
        openUserDetails(user, false);
    }

    private void openUserDetails(User user, boolean autoApprove) {
        if (user == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/user_details.fxml"));
            Parent root = loader.load();
            UserDetailsController controller = loader.getController();
            if (controller != null) {
                controller.setUserData(user, autoApprove);
                Stage stage = new Stage();
                stage.setTitle("Thông tin người dùng: " + user.getUsername());
                stage.setScene(new Scene(root));
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi",
                    "Không thể mở chi tiết người dùng: " + e.getMessage());
        }
    }

    @FXML
    void handleViewUserDetails() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.show(AlertHelper.Type.WARNING, "Hãy chọn một người dùng từ bảng!");
            return;
        }
        openUserDetails(selected);
    }

    private Auction getLatestAuction(Auction fallback) {
        if (fallback == null) {
            return null;
        }
        for (Auction candidate : AppState.getInstance().getAuctionList()) {
            if (candidate.getAuctionId().equals(fallback.getAuctionId())) {
                return candidate;
            }
        }
        return fallback;
    }

    private void setupDepositTable() {
        if (depositTable == null) {
            return;
        }
        depositTable.setItems(depositList);
        depositTable.setPlaceholder(new Label(""));
        depositList.addListener((javafx.collections.ListChangeListener<DepositRequest>) c -> {
            boolean isEmpty = depositList.isEmpty();
            if (paneEmptyDeposits != null) {
                paneEmptyDeposits.setVisible(isEmpty);
                paneEmptyDeposits.setManaged(isEmpty);
            }
        });
        colDepositRefCode.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().getRequestId()));
        colDepositUsername.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().getUsername()));
        colDepositAmount.setCellValueFactory(
                d -> new SimpleStringProperty(
                        String.format("%,.0f ₫", d.getValue().getAmount())));
        colDepositAmount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                } else {
                    setText(v);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #0369a1;");
                }
            }
        });
        colDepositTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCreatedAt() != null
                        ? d.getValue().getCreatedAt().format(DT_FMT) : ""));
        colDepositAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnApprove = new Button("✓ Duyệt");
            private final Button btnReject = new Button("✗ Từ chối");
            {
                btnApprove.setStyle(
                        "-fx-background-color: #10B981; -fx-text-fill: white;"
                        + " -fx-background-radius: 6; -fx-font-size: 12px; -fx-padding: 4 10;");
                btnReject.setStyle(
                        "-fx-background-color: #EF4444; -fx-text-fill: white;"
                        + " -fx-background-radius: 6; -fx-font-size: 12px; -fx-padding: 4 10;");
                btnApprove.setOnAction(
                        e -> handleDepositApprove(getTableView().getItems().get(getIndex())));
                btnReject.setOnAction(
                        e -> handleDepositReject(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    javafx.scene.layout.HBox box =
                            new javafx.scene.layout.HBox(10, btnApprove, btnReject);
                    box.setAlignment(javafx.geometry.Pos.CENTER);
                    setGraphic(box);
                    setStyle("-fx-alignment: CENTER; -fx-padding: 0;");
                }
            }
        });
    }

    private void loadDepositData() {
        try {
            List<DepositRequest> pending = new DepositRequestDAO().findPending();
            depositList.setAll(pending);
            if (lblDepositCount != null) {
                lblDepositCount.setText(String.valueOf(pending.size()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDepositApprove(DepositRequest dr) {
        AppState.getInstance().getClient().setStringMessageCallback(msg -> {
            if (!msg.startsWith("DEPOSIT_REVIEW_OK")
                    && !msg.startsWith("DEPOSIT_REVIEW_FAILED")) {
                return;
            }
            AppState.getInstance().getClient().setStringMessageCallback(null);
            javafx.application.Platform.runLater(() -> {
                if (msg.startsWith("DEPOSIT_REVIEW_OK")) {
                    loadDepositData();
                    loadUserData();
                    AlertHelper.show(AlertHelper.Type.SUCCESS,
                            "Đã duyệt yêu cầu nạp cho " + dr.getUsername());
                } else {
                    AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi",
                            msg.substring("DEPOSIT_REVIEW_FAILED:".length()));
                }
            });
        });
        AppState.getInstance().getClient().send(
                "DEPOSIT_REVIEW:" + dr.getRequestId() + ":APPROVED:");
    }

    private void handleDepositReject(DepositRequest dr) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Từ chối yêu cầu nạp tiền");
        dialog.setHeaderText("Từ chối nạp của " + dr.getUsername());
        dialog.setContentText("Lý do từ chối (tùy chọn):");
        dialog.showAndWait().ifPresent(note -> {
            AppState.getInstance().getClient().setStringMessageCallback(msg -> {
                if (!msg.startsWith("DEPOSIT_REVIEW_OK")
                        && !msg.startsWith("DEPOSIT_REVIEW_FAILED")) {
                    return;
                }
                AppState.getInstance().getClient().setStringMessageCallback(null);
                javafx.application.Platform.runLater(() -> {
                    if (msg.startsWith("DEPOSIT_REVIEW_OK")) {
                        loadDepositData();
                        loadUserData();
                        AlertHelper.show(AlertHelper.Type.SUCCESS,
                                "Đã từ chối yêu cầu nạp tiền của " + dr.getUsername());
                    } else {
                        AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi",
                                msg.substring("DEPOSIT_REVIEW_FAILED:".length()));
                    }
                });
            });
            AppState.getInstance().getClient().send(
                    "DEPOSIT_REVIEW:" + dr.getRequestId() + ":REJECTED:" + note);
        });
    }

    @FXML
    void handleNavDeposits() {
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().select(2);
        }
        setActiveNav(navDepositsBtn, navAuctionsBtn, navUsersBtn, navMessagesBtn);
        if (pageTitleLabel != null) {
            pageTitleLabel.setText("Yêu cầu nạp tiền");
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText(
                    "Duyệt yêu cầu chuyển khoản ngân hàng từ người dùng");
        }
        if (btnCreateSession != null) {
            btnCreateSession.setVisible(false);
            btnCreateSession.setManaged(false);
        }
    }

    @FXML
    void handleRefreshDeposits() {
        loadDepositData();
        loadUserData();
    }
}
