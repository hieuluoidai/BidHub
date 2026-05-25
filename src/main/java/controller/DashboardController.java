package controller;

import java.io.IOException;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
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
    @FXML private Label auctionSubtitleLabel;
    @FXML private Label lblHomeWelcome;
    @FXML private Label lblHomeHeroSubtitle;
    @FXML private Label lblBalance;        // hiển thị số dư hiện tại
    @FXML private Label lblLockedBalance;  // hiển thị số dư bị khóa
    @FXML private TextField textSearch;

    @FXML private javafx.scene.control.ToggleGroup auctionStatusGroup;
    @FXML private javafx.scene.control.ToggleButton tglAllAuctions;
    @FXML private javafx.scene.control.ToggleButton tglOpenAuctions;
    @FXML private javafx.scene.control.ToggleButton tglRunningAuctions;
    @FXML private javafx.scene.control.ToggleButton tglEndedAuctions;

    @FXML private Label lblStatTotal;
    @FXML private Label lblStatRunning;
    @FXML private Label lblStatEnded;
    
    @FXML private javafx.scene.layout.FlowPane flowPaneAuctions;
    @FXML private javafx.scene.layout.VBox paneEndedSection;
    @FXML private javafx.scene.layout.FlowPane flowPaneEnded;
    
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
    @FXML private javafx.scene.layout.StackPane paneHomeHero;
    @FXML private javafx.scene.layout.VBox boxHomeHeroContent;
    @FXML private ImageView imgHomeHeroBackground;
    @FXML private ImageView imgHomeHeroNextBackground;
    @FXML private Label dotHomeSlide0;
    @FXML private Label dotHomeSlide1;
    @FXML private Label dotHomeSlide2;
    @FXML private Label dotHomeSlide3;

    // View Navigation
    @FXML private Button btnNavHome;
    @FXML private Button btnNavAuctions;
    @FXML private Button btnNavWallet;
    @FXML private Button btnNavMessages;
    @FXML private Label lblSidebarChatBadge;
    @FXML private javafx.scene.layout.VBox paneAuctionsAccordion;
    @FXML private javafx.scene.shape.SVGPath svgAuctionsArrow;

    // Auction Category Buttons
    @FXML private Button btnCategoryAll;
    @FXML private Button btnCategoryArt;
    @FXML private Button btnCategoryElectronics;
    @FXML private Button btnCategoryVehicle;

    @FXML private javafx.scene.layout.VBox viewHome;
    @FXML private javafx.scene.layout.VBox viewAuctions;
    @FXML private javafx.scene.layout.VBox viewWallet;
    @FXML private javafx.scene.layout.VBox viewMessages;
    @FXML private MessagesController messagesViewController;

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
    private javafx.beans.value.ChangeListener<Number> chatBadgeListener;
    private String selectedCategoryFilter = "ALL";
    private static final double HOME_HERO_IMAGE_OPACITY = 0.82;
    private static final Duration HOME_HERO_AUTO_ADVANCE = Duration.seconds(5);
    private final java.util.List<HomeHeroSlide> homeHeroSlides = java.util.List.of(
            new HomeHeroSlide("/Images/home-welcome.png", "Welcome",
                    "Khám phá các phiên đấu giá đang diễn ra và chọn danh mục bạn quan tâm"),
            new HomeHeroSlide("/Images/home-art.png", "Art Auctions",
                    "Khám phá tác phẩm nghệ thuật, bộ sưu tập hiếm và các phiên được tuyển chọn"),
            new HomeHeroSlide("/Images/home-electronics.png", "Electronics Auctions",
                    "Tìm thiết bị công nghệ, phụ kiện thông minh và sản phẩm điện tử đáng chú ý"),
            new HomeHeroSlide("/Images/home-vehicle.png", "Vehicle Auctions",
                    "Theo dõi ô tô, xe máy và phương tiện nổi bật trong các phiên đấu giá mới")
    );
    private int currentHomeSlideIndex = 0;
    private Timeline homeHeroAutoPlay;

    private record HomeHeroSlide(String imagePath, String title, String subtitle) {}

    /**
     * Khởi tạo bộ lọc và thiết lập các bộ lắng nghe sự kiện (Listeners).
     */
    public void initialize() {
        setupHomeHero();

        // Setup personal transaction list
        setupTransactionList();

        // Lấy danh sách từ AppState để hiển thị
        ObservableList<Auction> masterData = AppState.getInstance().getAuctionList();
        filteredData = new FilteredList<>(masterData, p -> true);

        // Thêm ListChangeListener để cập nhật giao diện thông minh
        masterData.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            boolean structuralChange = false;
            while (c.next()) {
                if (c.wasAdded() || c.wasRemoved()) {
                    structuralChange = true;
                } else if (c.wasUpdated() || c.wasReplaced()) {
                    for (int i = c.getFrom(); i < c.getTo(); i++) {
                        updateSpecificCard(masterData.get(i));
                    }
                }
            }
            if (structuralChange) {
                renderAuctions();
            }
            updateStats();
            updateOpenDetailWindow();
        });

        // Khi tìm kiếm or đổi loại hàng, bảng sẽ tự động update
        textSearch.textProperty().addListener((obs, old, newVal) -> {
            updatePredicate();
            renderAuctions();
        });
        if (auctionStatusGroup != null) {
            auctionStatusGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
                if (newVal == null && old != null) {
                    old.setSelected(true);
                    return;
                }
                updatePredicate();
                renderAuctions();
            });
        }

        // Đăng ký nhận thông báo thay đổi số dư real-time từ Server
        AppState.getInstance().getClient().addStringMessageListener(msg -> {
            if (msg.startsWith("TOPUP_OK:") || msg.startsWith("PAY_OK:") || 
                msg.startsWith("AUTOBID_OK:") || msg.startsWith("CANCEL_AUTOBID_OK:") ||
                msg.startsWith("BALANCE_UPDATE:")) {
                
                String[] parts = msg.split(":");
                if (parts.length >= 2) {
                    try {
                        if (msg.startsWith("BALANCE_UPDATE:")) {
                            double avail = Double.parseDouble(parts[1]);
                            double locked = Double.parseDouble(parts[2]);
                            User u = AppState.getInstance().getCurrentUser();
                            if (u != null) {
                                u.setBalance(avail);
                                u.setLockedBalance(locked);
                            }
                        } else {
                            double newAvail = Double.parseDouble(parts[parts.length - 1]);
                            User u = AppState.getInstance().getCurrentUser();
                            if (u != null) u.setBalance(newAvail);
                        }
                        javafx.application.Platform.runLater(this::refreshBalanceLabel);
                    } catch (Exception ignore) {}
                }
            } else if (msg.equals("SELLER_APPROVED") || msg.equals("SELLER_REVOKED")) {
                javafx.application.Platform.runLater(() -> {
                    User u = AppState.getInstance().getCurrentUser();
                    if (u != null) {
                        User updated = new database.UserDAO().findById(u.getUserId());
                        if (updated != null) AppState.getInstance().setCurrentUser(updated);
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

        // Wire chat badge → sidebar
        utils.ChatCenter.init();
        chatBadgeListener = (obs, oldV, newV) ->
                javafx.application.Platform.runLater(() -> updateChatBadge(newV.intValue()));
        AppState.getInstance().totalUnreadChatProperty().addListener(chatBadgeListener);
        updateChatBadge(AppState.getInstance().getTotalUnreadChat());

        // Đăng ký hook để các controller khác có thể yêu cầu mở chat
        AppState.getInstance().setOpenChatHook(args -> {
            javafx.application.Platform.runLater(() -> openChatWith(args[0], args[1], args[2]));
        });

        // Lắng nghe thay đổi "Star" để sắp xếp lại real-time
        AppState.getInstance().getStarredAuctionIds().addListener((javafx.collections.SetChangeListener<String>) c -> {
            javafx.application.Platform.runLater(this::renderAuctions);
        });

        // Render lần đầu
        updateAuctionHeader();
        renderAuctions();
        updateStats();
        updateSubMenuActiveState();
    }

    private String getSelectedStatusFilter() {
        if (auctionStatusGroup == null) return "TẤT CẢ";
        javafx.scene.control.Toggle t = auctionStatusGroup.getSelectedToggle();
        if (t instanceof javafx.scene.control.ToggleButton tb) return tb.getText().toUpperCase();
        return "TẤT CẢ";
    }

    private String getCategoryTitle() {
        return switch (selectedCategoryFilter) {
            case "ART" -> "Art Auctions";
            case "ELECTRONICS" -> "Electronics Auctions";
            case "VEHICLE" -> "Vehicle Auctions";
            default -> "All Auctions";
        };
    }

    private String getCategorySubtitle() {
        return switch (selectedCategoryFilter) {
            case "ART" -> "Danh mục các tác phẩm nghệ thuật, hội họa và đồ sưu tầm giá trị";
            case "ELECTRONICS" -> "Sàn đấu giá thiết bị công nghệ, đồ dùng điện tử và linh kiện";
            case "VEHICLE" -> "Khu vực đấu giá ô tô, xe máy và các loại phương tiện giao thông";
            default -> "Tổng hợp các phiên đấu giá đang diễn ra trên hệ thống BidHub";
        };
    }

    private boolean matchesCategory(Auction auction) {
        if ("ALL".equals(selectedCategoryFilter)) return true;
        if (auction == null || auction.getItem() == null || auction.getItem().getItemType() == null) return false;
        return auction.getItem().getItemType().trim().equalsIgnoreCase(selectedCategoryFilter);
    }

    private void updateAuctionHeader() {
        if (titleLabel != null) titleLabel.setText(getCategoryTitle());
        if (auctionSubtitleLabel != null) auctionSubtitleLabel.setText(getCategorySubtitle());
    }

    private void setupHomeHero() {
        if (paneHomeHero == null) return;

        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(48);
        clip.setArcHeight(48);
        clip.widthProperty().bind(paneHomeHero.widthProperty());
        clip.heightProperty().bind(paneHomeHero.heightProperty());
        paneHomeHero.setClip(clip);

        bindHomeHeroImage(imgHomeHeroBackground);
        bindHomeHeroImage(imgHomeHeroNextBackground);

        if (!homeHeroSlides.isEmpty()) {
            setHomeHeroImage(imgHomeHeroBackground, homeHeroSlides.get(0));
            if (imgHomeHeroBackground != null) imgHomeHeroBackground.setOpacity(HOME_HERO_IMAGE_OPACITY);
            if (imgHomeHeroNextBackground != null) imgHomeHeroNextBackground.setOpacity(0);
            updateHomeHeroText(0);
            updateHomeHeroDots(0);
            startHomeHeroAutoPlay();
        }
    }

    private void bindHomeHeroImage(ImageView imageView) {
        if (imageView == null) return;
        imageView.fitWidthProperty().bind(paneHomeHero.widthProperty());
        imageView.fitHeightProperty().bind(paneHomeHero.heightProperty());
    }

    private void setHomeHeroImage(ImageView imageView, HomeHeroSlide slide) {
        if (imageView == null || slide == null) return;
        var resource = getClass().getResource(slide.imagePath());
        if (resource != null) {
            imageView.setImage(new Image(resource.toExternalForm()));
        }
    }

    private void updateHomeHeroText(int slideIndex) {
        if (homeHeroSlides.isEmpty()) return;
        int normalizedIndex = normalizeHomeSlideIndex(slideIndex);
        HomeHeroSlide slide = homeHeroSlides.get(normalizedIndex);
        if (lblHomeWelcome != null) {
            lblHomeWelcome.setText(normalizedIndex == 0 ? "Welcome, " + getHomeUsername() : slide.title());
        }
        if (lblHomeHeroSubtitle != null) {
            lblHomeHeroSubtitle.setText(slide.subtitle());
        }
    }

    private String getHomeUsername() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) return "bidder";
        return user.getUsername();
    }

    private void updateHomeHeroDots(int activeIndex) {
        updateHomeHeroDot(dotHomeSlide0, activeIndex == 0);
        updateHomeHeroDot(dotHomeSlide1, activeIndex == 1);
        updateHomeHeroDot(dotHomeSlide2, activeIndex == 2);
        updateHomeHeroDot(dotHomeSlide3, activeIndex == 3);
    }

    private void updateHomeHeroDot(Label dot, boolean active) {
        if (dot == null) return;
        dot.getStyleClass().removeAll("home-hero-dot-active");
        if (active) {
            dot.getStyleClass().add("home-hero-dot-active");
        }
    }

    private int normalizeHomeSlideIndex(int index) {
        int size = homeHeroSlides.size();
        if (size == 0) return 0;
        return Math.floorMod(index, size);
    }

    private void startHomeHeroAutoPlay() {
        if (homeHeroSlides.size() < 2) return;
        if (homeHeroAutoPlay == null) {
            homeHeroAutoPlay = new Timeline(new KeyFrame(HOME_HERO_AUTO_ADVANCE,
                    event -> showHomeSlide(currentHomeSlideIndex + 1, false)));
            homeHeroAutoPlay.setCycleCount(Animation.INDEFINITE);
        }
        homeHeroAutoPlay.playFromStart();
    }

    private void stopHomeHeroAutoPlay() {
        if (homeHeroAutoPlay != null) {
            homeHeroAutoPlay.stop();
        }
    }

    private void restartHomeHeroAutoPlay() {
        stopHomeHeroAutoPlay();
        startHomeHeroAutoPlay();
    }

    private void showHomeSlide(int requestedIndex, boolean manual) {
        if (homeHeroSlides.isEmpty() || imgHomeHeroBackground == null || imgHomeHeroNextBackground == null) return;
        int targetIndex = normalizeHomeSlideIndex(requestedIndex);
        if (targetIndex == currentHomeSlideIndex) {
            if (manual) restartHomeHeroAutoPlay();
            return;
        }

        if (manual) stopHomeHeroAutoPlay();
        HomeHeroSlide targetSlide = homeHeroSlides.get(targetIndex);

        // 1. Chuẩn bị ảnh tiếp theo vào ImageView ẩn (imgHomeHeroNextBackground nằm đè lên Background)
        setHomeHeroImage(imgHomeHeroNextBackground, targetSlide);
        imgHomeHeroNextBackground.setOpacity(0);

        // 2. Tạo hiệu ứng Fade cho ảnh (Cross-fade)
        // Lưu ý: imgHomeHeroBackground mờ dần về 0, imgHomeHeroNextBackground hiện dần lên mức OPACITY chuẩn
        FadeTransition imageFadeOut = new FadeTransition(Duration.millis(800), imgHomeHeroBackground);
        imageFadeOut.setToValue(0);

        FadeTransition imageFadeIn = new FadeTransition(Duration.millis(800), imgHomeHeroNextBackground);
        imageFadeIn.setToValue(HOME_HERO_IMAGE_OPACITY);

        // 3. Tạo hiệu ứng cho text (Fade out -> đổi text -> Fade in)
        FadeTransition textFadeOut = new FadeTransition(Duration.millis(350), boxHomeHeroContent);
        textFadeOut.setToValue(0);
        textFadeOut.setOnFinished(e -> {
            updateHomeHeroText(targetIndex);
            FadeTransition textFadeIn = new FadeTransition(Duration.millis(400), boxHomeHeroContent);
            textFadeIn.setToValue(1.0);
            textFadeIn.play();
        });

        // Chạy đồng thời hiệu ứng ảnh và hiệu ứng text bắt đầu mờ đi
        ParallelTransition pt = new ParallelTransition(imageFadeOut, imageFadeIn, textFadeOut);
        pt.setOnFinished(e -> {
            // Sau khi xong, cập nhật lại trạng thái gốc:
            // Đặt ảnh mới cho Background chính và hiện nó lên, reset NextBackground về 0
            setHomeHeroImage(imgHomeHeroBackground, targetSlide);
            imgHomeHeroBackground.setOpacity(HOME_HERO_IMAGE_OPACITY);
            imgHomeHeroNextBackground.setOpacity(0);

            currentHomeSlideIndex = targetIndex;
            updateHomeHeroDots(targetIndex);
            if (manual) restartHomeHeroAutoPlay();
        });

        pt.play();
    }

    private void updateStats() {
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();
        long total = auctions.stream().filter(this::matchesCategory).count();
        long running = auctions.stream().filter(this::matchesCategory).filter(a -> "RUNNING".equals(a.getStatus())).count();
        long ended = auctions.stream().filter(this::matchesCategory).filter(this::isAuctionEnded).count();
        if (lblStatTotal != null) lblStatTotal.setText(String.valueOf(total));
        if (lblStatRunning != null) lblStatRunning.setText(String.valueOf(running));
        if (lblStatEnded != null) lblStatEnded.setText(String.valueOf(ended));
    }

    /**
     * Cập nhật một thẻ sản phẩm cụ thể mà không vẽ lại toàn bộ.
     */
    private void updateSpecificCard(Auction auction) {
        if (auction == null) return;
        if (lookAndUpdateIn(flowPaneAuctions, auction)) return;
        if (lookAndUpdateIn(flowPaneEnded, auction)) return;
        renderAuctions();
    }

    private boolean lookAndUpdateIn(javafx.scene.layout.FlowPane pane, Auction auction) {
        if (pane == null) return false;
        for (javafx.scene.Node node : pane.getChildren()) {
            Object userData = node.getUserData();
            if (userData instanceof ItemCardController cardController) {
                if (cardController.getAuctionId().equals(auction.getAuctionId())) {
                    cardController.setData(auction, this::handleViewDetailsOf, this::handleQuickBidOf);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Vẽ lại danh sách thẻ sản phẩm dựa trên dữ liệu đã lọc, phân loại và sắp xếp.
     */
    private void renderAuctions() {
        if (flowPaneAuctions == null || flowPaneEnded == null) return;

        flowPaneAuctions.getChildren().clear();
        flowPaneEnded.getChildren().clear();

        if (filteredData.isEmpty()) {
            showEmptyState(true);
            return;
        }

        showEmptyState(false);

        java.util.List<Auction> activeList = new java.util.ArrayList<>();
        java.util.List<Auction> endedList = new java.util.ArrayList<>();

        boolean splitEnded = "TẤT CẢ".equals(getSelectedStatusFilter());
        for (Auction a : filteredData) {
            if (splitEnded && isAuctionEnded(a)) {
                endedList.add(a);
            } else {
                activeList.add(a);
            }
        }

        activeList.sort(this::compareAuctions);
        endedList.sort(this::compareAuctions);

        for (Auction auction : activeList) {
            flowPaneAuctions.getChildren().add(createCard(auction));
        }

        if (!endedList.isEmpty()) {
            paneEndedSection.setVisible(true);
            paneEndedSection.setManaged(true);
            for (Auction auction : endedList) {
                flowPaneEnded.getChildren().add(createCard(auction));
            }
        } else {
            paneEndedSection.setVisible(false);
            paneEndedSection.setManaged(false);
        }
    }

    private int compareAuctions(Auction a, Auction b) {
        boolean starA = AppState.getInstance().isStarred(a.getAuctionId());
        boolean starB = AppState.getInstance().isStarred(b.getAuctionId());
        if (starA && !starB) return -1;
        if (!starA && starB) return 1;
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
            boolean noAuctionsAtAll = AppState.getInstance().getAuctionList().isEmpty();
            if (lblEmptyTitle != null) {
                lblEmptyTitle.setText(noAuctionsAtAll ? "Chưa có phiên đấu giá nào" : "Không tìm thấy phiên đấu giá nào");
            }
            if (lblEmptySubtitle != null) {
                lblEmptySubtitle.setText(noAuctionsAtAll
                        ? "Các phiên đấu giá mới sẽ sớm xuất hiện ở đây"
                        : "Thử thay đổi từ khóa hoặc bộ lọc của bạn");
            }
        }
    }

    private javafx.scene.Node createCard(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_card.fxml"));
            javafx.scene.Node card = loader.load();
            ItemCardController cardController = loader.getController();
            cardController.setData(auction, this::handleViewDetailsOf, this::handleQuickBidOf);
            card.setUserData(cardController);
            return card;
        } catch (IOException e) {
            System.err.println("Lỗi load item card: " + e.getMessage());
            return new Label("Error");
        }
    }

    private void updatePredicate() {
        filteredData.setPredicate(auction -> {
            if (auction == null || auction.getItem() == null) return false;
            if (!matchesCategory(auction)) return false;
            String search = textSearch.getText() == null ? "" : textSearch.getText().toLowerCase().trim();
            boolean matchesName = auction.getItem().getItemName().toLowerCase().contains(search);
            if (!matchesName) return false;
            String status = getSelectedStatusFilter();
            if (status.equals("SẮP DIỄN RA")) return "OPEN".equals(auction.getStatus());
            if (status.equals("ĐANG DIỄN RA")) return "RUNNING".equals(auction.getStatus());
            if (status.equals("ĐÃ KẾT THÚC")) return isAuctionEnded(auction);
            return true;
        });
    }

    @FXML
    void handleRefresh() {
        loadAuctionData();
    }

    @FXML
    private void loadAuctionData() {
        AppState.getInstance().getClient().send("REFRESH_DATA");
    }

    private void setupPermissions() {
        User user = AppState.getInstance().getCurrentUser();
        if (user != null) {
            updateHomeHeroText(currentHomeSlideIndex);
            if (lblSidebarUsername != null) lblSidebarUsername.setText(user.getUsername());
            if (lblSidebarRole != null) lblSidebarRole.setText(user.getClass().getSimpleName().toUpperCase());
            if (lblSidebarAvatar != null) {
                String firstChar = user.getUsername().isEmpty() ? "?" : user.getUsername().substring(0, 1).toUpperCase();
                lblSidebarAvatar.setText(firstChar);
                if (imgSidebarAvatar != null) {
                    if (sidebarAvatarClip != null) imgSidebarAvatar.setClip(sidebarAvatarClip);
                    if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
                        String uri = utils.ImageStorageService.toImageUrl(user.getAvatarPath());
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
            String uri = utils.ImageStorageService.toImageUrl(user.getAvatarPath());
            if (uri != null) {
                imgSidebarAvatar.setImage(new javafx.scene.image.Image(uri, true));
                imgSidebarAvatar.setVisible(true);
                if (lblSidebarAvatar != null) lblSidebarAvatar.setVisible(false);
            }
        }
    }

    public void refreshBalanceLabel() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        if (lblBalance != null) lblBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        if (lblLockedBalance != null) lblLockedBalance.setText(String.format("%,.0f ₫", user.getLockedBalance()));
        if (lblWalletBalance != null) lblWalletBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        if (lblWalletLocked != null) lblWalletLocked.setText(String.format("%,.0f ₫", user.getLockedBalance()));
    }

    @FXML
    void handleTopUp() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/topup_dialog.fxml"));
            Parent root = loader.load();
            controller.TopUpController controller = loader.getController();
            controller.setOnTopUpSuccess(this::refreshBalanceLabel);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            
            AppState.getInstance().getSceneManager().setupModalStage(stage, root, "Nạp tiền vào ví");
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleLogout() {
        stopHomeHeroAutoPlay();
        utils.NotificationCenter.reset();
        utils.ChatCenter.reset();
        utils.FriendCenter.reset();
        if (chatBadgeListener != null) {
            AppState.getInstance().totalUnreadChatProperty().removeListener(chatBadgeListener);
            chatBadgeListener = null;
        }
        AppState.getInstance().setCurrentUser(null);
        AppState.getInstance().getSceneManager().showLogin();
    }

    @FXML
    void handleCreateNewSession() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create_session.fxml"));
            Parent root = loader.load();
            CreateSessionController controller = loader.getController();
            String lockedType = getCreateSessionLockedType();
            if (lockedType != null) {
                controller.lockItemType(lockedType);
            }
            showStage(root, "Create New Auction Session");
            loadAuctionData();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCreateSessionLockedType() {
        return switch (selectedCategoryFilter) {
            case "ART" -> "Art";
            case "ELECTRONICS" -> "Electronics";
            case "VEHICLE" -> "Vehicle";
            default -> null;
        };
    }

    private void handleQuickBidOf(Auction auction) {
        auction = getLatestAuction(auction);
        if (auction == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();
            ((BidController)loader.getController()).setAuctionData(auction);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            
            AppState.getInstance().getSceneManager().setupModalStage(stage, root, "Place Your Bid");
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ItemDetailsController currentDetailController;
    private Stage currentDetailStage;

    private void handleViewDetailsOf(Auction auction) {
        auction = getLatestAuction(auction);
        if (auction == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();
            currentDetailController = loader.getController();
            currentDetailController.setItemData(auction);
            currentDetailStage = new Stage();
            utils.SceneManager.setAppIcon(currentDetailStage);
            currentDetailStage.setTitle("Item Details");
            currentDetailStage.setScene(new Scene(root));
            currentDetailStage.initModality(Modality.APPLICATION_MODAL);
            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            currentDetailStage.setWidth(Math.min(1120, screenBounds.getWidth() * 0.96));
            currentDetailStage.setHeight(Math.min(880, screenBounds.getHeight() * 0.96));
            currentDetailStage.setOnHidden(e -> {
                currentDetailController = null;
                currentDetailStage = null;
            });
            currentDetailStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleViewProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_details.fxml"));
            Parent root = loader.load();
            UserDetailsController controller = loader.getController();
            controller.setUserData(AppState.getInstance().getCurrentUser());
            controller.setOnAvatarChanged(this::refreshSidebarAvatar);
            Stage stage = new Stage();
            utils.SceneManager.setAppIcon(stage);
            stage.setTitle("Thông tin cá nhân");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            
            // Hoạt ảnh xuất hiện (Fade-in & Slide-down)
            root.setOpacity(0);
            root.setTranslateY(-20);
            FadeTransition ft = new FadeTransition(Duration.millis(350), root);
            ft.setFromValue(0); ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(350), root);
            tt.setFromY(-20); tt.setToY(0);
            ParallelTransition anim = new ParallelTransition(ft, tt);
            
            stage.setOnShown(e -> anim.play());
            stage.show();
            stage.sizeToScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateOpenDetailWindow() {
        if (currentDetailController != null && currentDetailStage != null && currentDetailStage.isShowing()) {
            Auction current = currentDetailController.getAuction();
            if (current != null) {
                boolean found = false;
                for (Auction a : AppState.getInstance().getAuctionList()) {
                    if (a.getAuctionId().equals(current.getAuctionId())) {
                        javafx.application.Platform.runLater(() -> currentDetailController.setItemData(a));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Phiên đã bị xóa hoặc biến mất khỏi danh sách master
                    javafx.application.Platform.runLater(() -> {
                        currentDetailStage.close();
                        utils.AlertHelper.show(utils.AlertHelper.Type.WARNING, 
                                "Phiên đấu giá này không còn tồn tại trên hệ thống.");
                    });
                }
            }
        }
    }

    private void openPopup(String fxml, String title) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            showStage(root, title);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showStage(Parent root, String title) {
        Stage stage = new Stage();
        utils.SceneManager.setAppIcon(stage);
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();
        renderAuctions();
    }

    private Auction getLatestAuction(Auction fallback) {
        if (fallback == null) return null;
        for (Auction candidate : AppState.getInstance().getAuctionList()) {
            if (candidate.getAuctionId().equals(fallback.getAuctionId())) return candidate;
        }
        return fallback;
    }

    @FXML
    void handleNavHome() {
        switchView("home");
    }

    @FXML
    void handleNavAuctions() {
        toggleAuctionsAccordion();
    }

    private void toggleAuctionsAccordion() {
        boolean isExpanding = !paneAuctionsAccordion.isManaged();
        animateAccordion(isExpanding);
    }

    private void animateAccordion(boolean expand) {
        if (expand) {
            paneAuctionsAccordion.setManaged(true);
            paneAuctionsAccordion.setVisible(true);
            paneAuctionsAccordion.setOpacity(0);
            paneAuctionsAccordion.setMaxHeight(0);

            Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, 
                    new KeyValue(paneAuctionsAccordion.maxHeightProperty(), 0),
                    new KeyValue(paneAuctionsAccordion.opacityProperty(), 0)
                ),
                new KeyFrame(Duration.millis(300),
                    new KeyValue(paneAuctionsAccordion.maxHeightProperty(), 200, Interpolator.EASE_BOTH),
                    new KeyValue(paneAuctionsAccordion.opacityProperty(), 1, Interpolator.EASE_IN)
                )
            );
            timeline.setOnFinished(e -> {
                paneAuctionsAccordion.setMaxHeight(Double.MAX_VALUE);
                svgAuctionsArrow.setRotate(180);
            });
            timeline.play();
            
            if (!viewAuctions.isVisible()) {
                openAuctionCategory("ALL");
            }
        } else {
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(paneAuctionsAccordion.maxHeightProperty(), paneAuctionsAccordion.getHeight()),
                    new KeyValue(paneAuctionsAccordion.opacityProperty(), 1)
                ),
                new KeyFrame(Duration.millis(250),
                    new KeyValue(paneAuctionsAccordion.maxHeightProperty(), 0, Interpolator.EASE_BOTH),
                    new KeyValue(paneAuctionsAccordion.opacityProperty(), 0, Interpolator.EASE_OUT)
                )
            );
            timeline.setOnFinished(e -> {
                paneAuctionsAccordion.setManaged(false);
                paneAuctionsAccordion.setVisible(false);
                svgAuctionsArrow.setRotate(0);
            });
            timeline.play();
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

    @FXML
    void handleBellClicked() {
        // NotificationCenter is already attached, but we can trigger it manually if needed
        // or just let the mouse click bubble up. Since we set onMouseClicked in FXML, 
        // we should ensure it doesn't conflict with NotificationCenter.attach.
    }

    @FXML
    void handleHomeAll() {
        openAuctionCategory("ALL");
    }

    @FXML
    void handleHomeArt() {
        openAuctionCategory("ART");
    }

    @FXML
    void handleHomeElectronics() {
        openAuctionCategory("ELECTRONICS");
    }

    @FXML
    void handleHomeVehicle() {
        openAuctionCategory("VEHICLE");
    }

    @FXML
    void handleHomeSlidePrev() {
        showHomeSlide(currentHomeSlideIndex - 1, true);
    }

    @FXML
    void handleHomeSlideNext() {
        showHomeSlide(currentHomeSlideIndex + 1, true);
    }

    @FXML
    void handleHomeSlide0() {
        showHomeSlide(0, true);
    }

    @FXML
    void handleHomeSlide1() {
        showHomeSlide(1, true);
    }

    @FXML
    void handleHomeSlide2() {
        showHomeSlide(2, true);
    }

    @FXML
    void handleHomeSlide3() {
        showHomeSlide(3, true);
    }

    private void openAuctionCategory(String category) {
        selectedCategoryFilter = (category == null) ? "ALL" : category;
        
        // Chuyển view sang auctions
        switchView("auctions");
        
        // Cập nhật dữ liệu hiển thị
        updateAuctionHeader();
        updatePredicate();
        renderAuctions();
        updateStats();
        updateSubMenuActiveState();
        
        // Đảm bảo menu con (accordion) được mở ra nếu đang đóng
        if (!paneAuctionsAccordion.isManaged()) {
            animateAccordion(true);
        }
    }

    private void updateSubMenuActiveState() {
        if (btnCategoryAll == null) return;
        
        btnCategoryAll.getStyleClass().remove("sidebar-sub-item-active");
        btnCategoryArt.getStyleClass().remove("sidebar-sub-item-active");
        btnCategoryElectronics.getStyleClass().remove("sidebar-sub-item-active");
        btnCategoryVehicle.getStyleClass().remove("sidebar-sub-item-active");

        switch (selectedCategoryFilter) {
            case "ALL" -> btnCategoryAll.getStyleClass().add("sidebar-sub-item-active");
            case "ART" -> btnCategoryArt.getStyleClass().add("sidebar-sub-item-active");
            case "ELECTRONICS" -> btnCategoryElectronics.getStyleClass().add("sidebar-sub-item-active");
            case "VEHICLE" -> btnCategoryVehicle.getStyleClass().add("sidebar-sub-item-active");
        }
    }

    @FXML
    void handleNavWallet() {
        switchView("wallet");
        refreshWalletData();
    }

    @FXML void handleNavMessages() {
        switchView("messages");
        if (messagesViewController != null) messagesViewController.refreshSummaries();
    }

    private String currentViewName = "home";

    private void switchView(String which) {
        if (which.equals(currentViewName)) return;

        javafx.scene.Node oldView = getViewByName(currentViewName);
        javafx.scene.Node nextView = getViewByName(which);

        // Update sidebar state immediately for responsiveness
        updateSidebarActiveState(which);

        if (oldView == null || nextView == null) {
            performInstantSwitch(which);
            currentViewName = which;
            return;
        }

        // Prepare next view
        nextView.setOpacity(0);
        nextView.setTranslateY(10); // Subtle slide up effect
        nextView.setVisible(true);
        nextView.setManaged(true);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(120), oldView);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), nextView);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(250), nextView);
        slideIn.setFromY(10);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        fadeOut.setOnFinished(e -> {
            oldView.setVisible(false);
            oldView.setManaged(false);
            oldView.setOpacity(1.0);
            oldView.setTranslateY(0);
        });

        new ParallelTransition(fadeOut, fadeIn, slideIn).play();
        currentViewName = which;
    }

    private javafx.scene.Node getViewByName(String name) {
        return switch (name) {
            case "home" -> viewHome;
            case "auctions" -> viewAuctions;
            case "wallet" -> viewWallet;
            case "messages" -> viewMessages;
            default -> null;
        };
    }

    private void updateSidebarActiveState(String which) {
        boolean h = "home".equals(which);
        boolean a = "auctions".equals(which);
        boolean w = "wallet".equals(which);
        boolean m = "messages".equals(which);

        if (btnNavHome != null) btnNavHome.getStyleClass().remove("sidebar-item-active");
        btnNavAuctions.getStyleClass().remove("sidebar-item-active");
        btnNavWallet.getStyleClass().remove("sidebar-item-active");
        if (btnNavMessages != null) btnNavMessages.getStyleClass().remove("sidebar-item-active");

        if (h && btnNavHome != null) btnNavHome.getStyleClass().add("sidebar-item-active");
        else if (a) btnNavAuctions.getStyleClass().add("sidebar-item-active");
        else if (w) btnNavWallet.getStyleClass().add("sidebar-item-active");
        else if (m && btnNavMessages != null) btnNavMessages.getStyleClass().add("sidebar-item-active");
    }

    private void performInstantSwitch(String which) {
        boolean h = "home".equals(which);
        boolean a = "auctions".equals(which);
        boolean w = "wallet".equals(which);
        boolean m = "messages".equals(which);

        if (viewHome != null) {
            viewHome.setVisible(h);
            viewHome.setManaged(h);
        }
        viewAuctions.setVisible(a); viewAuctions.setManaged(a);
        viewWallet.setVisible(w); viewWallet.setManaged(w);
        if (viewMessages != null) {
            viewMessages.setVisible(m);
            viewMessages.setManaged(m);
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

    /** Public hook để mở chat với 1 user cụ thể (gọi từ item_details). */
    public void openChatWith(String partnerId, String partnerUsername, String partnerAvatarPath) {
        switchView("messages");
        if (messagesViewController != null) {
            messagesViewController.openChatWith(partnerId, partnerUsername, partnerAvatarPath);
        }
    }

    private void refreshWalletData() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        lblWalletBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        lblWalletLocked.setText(String.format("%,.0f ₫", user.getLockedBalance()));
        if (paneAccountStatus != null) {
            lblCurrentRole.setText(user.getClass().getSimpleName().toUpperCase());
            if (user instanceof Seller || user instanceof Admin) {
                lblSellerRequestStatus.setText("Tài khoản của bạn đã có quyền đăng bán sản phẩm.");
                btnRequestSeller.setVisible(false);
                btnRequestSeller.setManaged(false);
            } else if (user.isPendingSeller()) {
                lblSellerRequestStatus.setText("Yêu cầu trở thành Seller của bạn đang được Admin xét duyệt.");
                btnRequestSeller.setDisable(true); btnRequestSeller.setText("Đang chờ duyệt...");
            } else {
                lblSellerRequestStatus.setText(
                        "Bạn có thể đăng ký để trở thành người bán (Seller) để đăng các sản phẩm của riêng mình.");
                btnRequestSeller.setVisible(true);
                btnRequestSeller.setManaged(true);
                btnRequestSeller.setDisable(false);
                btnRequestSeller.setText("Trở thành Seller?");
            }
        }
        new Thread(() -> {
            var transactions = new database.WalletTransactionDAO().findByUserId(user.getUserId());
            javafx.application.Platform.runLater(() -> transactionRows.setAll(transactions));
        }).start();
    }

    @FXML
    void handleRequestSeller() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || !(user instanceof Bidder) || user instanceof Seller) return;
        if (utils.AlertHelper.showConfirm("Xác nhận yêu cầu", "Bạn có chắc chắn muốn gửi yêu cầu trở thành Seller không?")) {
            AppState.getInstance().getClient().send("REQUEST_SELLER:" + user.getUserId());
            user.setPendingSeller(true);
            refreshWalletData();
            utils.AlertHelper.show(utils.AlertHelper.Type.SUCCESS,
                    "Đã gửi yêu cầu", "Yêu cầu của bạn đã được gửi tới quản trị viên.");
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
        Label lblIcon = new Label();
        lblIcon.getStyleClass().add("bid-avatar");
        String symbol = tx.getAmount() > 0 ? "+" : "";
        switch (tx.getType()) {
            case TOPUP -> {
                lblIcon.setText("💰");
                lblIcon.setStyle("-fx-background-color: #10B981; -fx-text-fill: white;");
            }
            case PAYMENT -> {
                lblIcon.setText("🛒");
                lblIcon.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white;");
            }
            case EARNING -> {
                lblIcon.setText("📈");
                lblIcon.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white;");
            }
            case REFUND -> {
                lblIcon.setText("🔄");
                lblIcon.setStyle("-fx-background-color: #F59E0B; -fx-text-fill: white;");
            }
        }
        javafx.scene.layout.VBox vContent = new javafx.scene.layout.VBox(2);
        Label lblTitle = new Label(tx.getDescription());
        lblTitle.getStyleClass().add("bid-user-name");
        Label lblTime = new Label(tx.getCreatedAt().format(dateTimeFormatter));
        lblTime.getStyleClass().add("bid-feed-time");
        vContent.getChildren().addAll(lblTitle, lblTime);
        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Label lblAmount = new Label(String.format("%s%,.0f ₫", symbol, tx.getAmount()));
        lblAmount.getStyleClass().add("bid-feed-amount");
        lblAmount.setStyle("-fx-text-fill: " + (tx.getAmount() < 0 ? "#EF4444;" : "#10B981;"));
        container.getChildren().addAll(lblIcon, vContent, spacer, lblAmount);
        return container;
    }
}
