package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;

import model.auction.Auction;
import model.manager.AppState;
import model.notification.Notification;
import model.user.User;

import utils.AnimationUtils;
import utils.ChatCenter;
import utils.ImageStorageService;
import utils.NotificationCenter;

import java.io.IOException;

/**
 * Main Controller for the Admin Dashboard.
 * Orchestrates navigation and high-level system monitoring.
 */
public class AdminController {

    // Sidebar & Profile
    @FXML private Label lblSidebarAvatar;
    @FXML private Circle sidebarAvatarClip;
    @FXML private ImageView imgSidebarAvatar;
    @FXML private Label lblSidebarUsername;
    @FXML private Label lblSidebarRole;
    @FXML private StackPane bellNotif;
    @FXML private Label lblNotifBadge;
    @FXML private Label lblSidebarChatBadge;

    // Navigation Buttons
    @FXML private Button navAuctionsBtn;
    @FXML private Button navUsersBtn;
    @FXML private Button navDepositsBtn;
    @FXML private Button navMessagesBtn;

    // Accordion
    @FXML private VBox paneAuctionsAccordion;
    @FXML private SVGPath svgAuctionsArrow;
    @FXML private Button btnCategoryAll;
    @FXML private Button btnCategoryArt;
    @FXML private Button btnCategoryElectronics;
    @FXML private Button btnCategoryVehicle;

    // Sub-views & Their Controllers (injected by JavaFX)
    @FXML private Node auctionsView;
    @FXML private AdminAuctionsController auctionsViewController;

    @FXML private Node usersView;
    @FXML private AdminUsersController usersViewController;

    @FXML private Node walletView;
    @FXML private AdminWalletController walletViewController;

    @FXML private VBox messagesViewContainer;
    @FXML private Node messagesView;
    @FXML private MessagesController messagesViewController;

    // Top Bar
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Button btnCreateSession;

    private String currentViewName = "auctions";
    private javafx.beans.value.ChangeListener<Number> chatBadgeListener;

    /**
     * Initializes the controller and its sub-components.
     */
    @FXML
    public void initialize() {
        setupProfile();
        setupNetworkListeners();
        setupNavigation();
        setupNotificationSystem();
        setupChatBadge();

        // Connect sub-controllers
        if (auctionsViewController != null) {
            auctionsViewController.setMainController(this);
        }
        if (usersViewController != null) {
            usersViewController.setMainController(this);
        }
        if (walletViewController != null) {
            walletViewController.setMainController(this);
        }

        // Initial view state
        performInstantSwitch("auctions");
    }

    private void setupProfile() {
        User currentUser = AppState.getInstance().getCurrentUser();
        if (currentUser == null) {
            return;
        }

        lblSidebarUsername.setText(currentUser.getUsername());
        lblSidebarRole.setText("ADMIN");
        
        String firstChar = currentUser.getUsername().isEmpty() ? "A" 
                : currentUser.getUsername().substring(0, 1).toUpperCase();
        lblSidebarAvatar.setText(firstChar);

        if (imgSidebarAvatar != null && sidebarAvatarClip != null) {
            imgSidebarAvatar.setClip(sidebarAvatarClip);
            if (currentUser.getAvatarPath() != null && !currentUser.getAvatarPath().isEmpty()) {
                String uri = ImageStorageService.toImageUrl(currentUser.getAvatarPath());
                if (uri != null) {
                    imgSidebarAvatar.setImage(new Image(uri));
                    imgSidebarAvatar.setVisible(true);
                    lblSidebarAvatar.setVisible(false);
                }
            }
        }
    }

    private void setupNetworkListeners() {
        AppState.getInstance().getClient().addStringMessageListener(msg -> {
            if (msg.equals("USERS_UPDATED") || msg.startsWith("NEW_SELLER_REQUEST:")) {
                Platform.runLater(this::refreshUserData);
            }
            if (msg.startsWith("NEW_DEPOSIT_REQUEST:") || msg.equals("USERS_UPDATED")) {
                Platform.runLater(this::refreshWalletData);
            }
        });

        try {
            AppState.getInstance().getClient().send("REFRESH_DATA");
        } catch (Exception e) {
            System.err.println("Refresh data request failed: " + e.getMessage());
        }
    }

    private void setupNavigation() {
        // Auctions is default, ensure it's visible
        auctionsView.setVisible(true);
        auctionsView.setManaged(true);
    }

    private void setupNotificationSystem() {
        if (bellNotif != null && lblNotifBadge != null) {
            NotificationCenter.attach(bellNotif, lblNotifBadge, this::handleNotificationAction);
        }
    }

    private void setupChatBadge() {
        ChatCenter.init();
        chatBadgeListener = (obs, oldV, newV) ->
                Platform.runLater(() -> updateChatBadge(newV.intValue()));
        AppState.getInstance().totalUnreadChatProperty().addListener(chatBadgeListener);
        updateChatBadge(AppState.getInstance().getTotalUnreadChat());

        AppState.getInstance().setOpenChatHook(args ->
                Platform.runLater(() -> openChatWith(args[0], args[1], args[2])));
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
        if (usersViewController != null) {
            for (User u : usersViewController.getMasterUsers()) {
                if (u.getUsername().equalsIgnoreCase(username)) {
                    found = u;
                    break;
                }
            }
            if (found != null) {
                usersViewController.getUserTable().getSelectionModel().select(found);
                usersViewController.getUserTable().scrollTo(found);
                openUserDetails(found, autoApprove);
            }
        }
    }

    @FXML
    void handleNavAuctions() {
        switchView("auctions");
        setActiveNav(navAuctionsBtn, navUsersBtn, navDepositsBtn, navMessagesBtn);
        updateTopBar("Phiên đấu giá", "Tổng quan toàn bộ phiên đấu giá trong hệ thống", true);
        animateAccordion(!paneAuctionsAccordion.isManaged());
    }

    @FXML
    void handleNavUsers() {
        switchView("users");
        setActiveNav(navUsersBtn, navAuctionsBtn, navDepositsBtn, navMessagesBtn);
        updateTopBar("Quản lý người dùng", "Danh sách tất cả tài khoản trong hệ thống", false);
    }

    @FXML
    void handleNavDeposits() {
        switchView("deposits");
        setActiveNav(navDepositsBtn, navAuctionsBtn, navUsersBtn, navMessagesBtn);
        updateTopBar("Yêu cầu nạp tiền", "Duyệt yêu cầu chuyển khoản ngân hàng từ người dùng", false);
    }

    @FXML
    void handleNavMessages() {
        switchView("messages");
        setActiveNav(navMessagesBtn, navAuctionsBtn, navUsersBtn, navDepositsBtn);
        updateTopBar("Tin nhắn & Bạn bè", "Nhắn tin & Kết bạn", false);
        if (messagesViewController != null) {
            messagesViewController.refreshSummaries();
        }
    }

    private void updateTopBar(String title, String subtitle, boolean showCreate) {
        if (pageTitleLabel != null) {
            pageTitleLabel.setText(title);
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText(subtitle);
        }
        if (btnCreateSession != null) {
            btnCreateSession.setVisible(showCreate);
            btnCreateSession.setManaged(showCreate);
        }
    }

    @FXML
    void handleCategoryAll() {
        openAuctionCategory("ALL");
    }

    @FXML
    void handleCategoryArt() {
        openAuctionCategory("ART");
    }

    @FXML
    void handleCategoryElectronics() {
        openAuctionCategory("ELECTRONICS");
    }

    @FXML
    void handleCategoryVehicle() {
        openAuctionCategory("VEHICLE");
    }

    private void openAuctionCategory(String category) {
        if (auctionsViewController != null) {
            auctionsViewController.setCategoryFilter(category);
        }
        switchView("auctions");
        setActiveNav(navAuctionsBtn, navUsersBtn, navDepositsBtn, navMessagesBtn);
        updateTopBar("Phiên đấu giá", "Tổng quan toàn bộ phiên đấu giá trong hệ thống", true);
        updateSubMenuActiveState(category);
        if (!paneAuctionsAccordion.isManaged()) {
            animateAccordion(true);
        }
    }

    private void updateSubMenuActiveState(String category) {
        btnCategoryAll.getStyleClass().remove("sidebar-sub-item-active");
        btnCategoryArt.getStyleClass().remove("sidebar-sub-item-active");
        btnCategoryElectronics.getStyleClass().remove("sidebar-sub-item-active");
        btnCategoryVehicle.getStyleClass().remove("sidebar-sub-item-active");

        switch (category) {
            case "ALL" -> btnCategoryAll.getStyleClass().add("sidebar-sub-item-active");
            case "ART" -> btnCategoryArt.getStyleClass().add("sidebar-sub-item-active");
            case "ELECTRONICS" -> btnCategoryElectronics.getStyleClass().add("sidebar-sub-item-active");
            case "VEHICLE" -> btnCategoryVehicle.getStyleClass().add("sidebar-sub-item-active");
            default -> {}
        }
    }

    private void animateAccordion(boolean open) {
        AnimationUtils.animateAccordion(paneAuctionsAccordion, svgAuctionsArrow, open, 140);
    }

    private void switchView(String which) {
        if (which.equals(currentViewName)) {
            return;
        }
        Node oldView = getViewByName(currentViewName);
        Node nextView = getViewByName(which);
        AnimationUtils.switchView(oldView, nextView, null);
        currentViewName = which;
    }

    private Node getViewByName(String name) {
        return switch (name) {
            case "auctions" -> auctionsView;
            case "users" -> usersView;
            case "deposits" -> walletView;
            case "messages" -> messagesViewContainer;
            default -> null;
        };
    }

    private void performInstantSwitch(String which) {
        auctionsView.setVisible("auctions".equals(which));
        auctionsView.setManaged("auctions".equals(which));
        usersView.setVisible("users".equals(which));
        usersView.setManaged("users".equals(which));
        walletView.setVisible("deposits".equals(which));
        walletView.setManaged("deposits".equals(which));
        messagesViewContainer.setVisible("messages".equals(which));
        messagesViewContainer.setManaged("messages".equals(which));
    }

    @FXML
    void handleCreateSession() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create_session.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            
            AppState.getInstance().getSceneManager().setupModalStage(stage, root, "Tạo phiên đấu giá mới");
            
            stage.showAndWait();
            if (auctionsViewController != null) {
                auctionsViewController.updateAuctionStats();
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    @FXML
    void handleViewProfile() {
        openUserDetails(AppState.getInstance().getCurrentUser());
    }

    public void openUserDetails(User user) {
        openUserDetails(user, false);
    }

    public void openUserDetails(User user, boolean autoApprove) {
        if (user == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_details.fxml"));
            Parent root = loader.load();
            UserDetailsController controller = loader.getController();
            if (controller != null) {
                controller.setUserData(user, autoApprove);
                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                
                AppState.getInstance().getSceneManager().setupModalStage(stage, root,
                        "Thông tin người dùng: " + user.getUsername());
                
                stage.show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void openItemDetails(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();
            ItemDetailsController controller = loader.getController();
            controller.setItemData(auction);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            
            AppState.getInstance().getSceneManager().setupModalStage(stage, root, "Chi tiết phiên: " + auction.getAuctionId());
            
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleQuickBidOf(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();
            ((BidController) loader.getController()).setAuctionData(auction);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            
            AppState.getInstance().getSceneManager().setupModalStage(stage, root, "Admin Place Bid");
            
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void refreshUserData() {
        if (usersViewController != null) {
            usersViewController.loadUserData();
        }
    }

    public void refreshWalletData() {
        if (walletViewController != null) {
            walletViewController.loadDepositData();
        }
    }

    public void openChatWith(String partnerId, String partnerUsername, String partnerAvatarPath) {
        handleNavMessages();
        if (messagesViewController != null) {
            messagesViewController.openChatWith(partnerId, partnerUsername, partnerAvatarPath);
        }
    }

    private void updateChatBadge(int unread) {
        if (lblSidebarChatBadge == null) {
            return;
        }
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
        if (active != null) {
            active.getStyleClass().add("sidebar-item-active");
        }
        for (Button b : others) {
            if (b != null) {
                b.getStyleClass().removeAll("sidebar-item-active");
            }
        }
    }
}
