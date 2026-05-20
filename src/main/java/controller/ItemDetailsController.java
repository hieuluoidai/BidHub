package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import model.auction.Auction;
import model.auction.BidTransaction;
import model.item.Art;
import model.item.Electronics;
import model.item.Vehicle;
import model.manager.AppState;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import utils.AlertHelper;
import utils.FriendCenter;
import utils.ImageStorageService;
import utils.SessionPermission;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Điều khiển màn hình chi tiết phiên đấu giá.
 */
public class ItemDetailsController {
    private static final int MAX_VISIBLE_BIDS_ON_CHART = 10;

    @FXML private Label lblItemName;
    @FXML private Label lblCategory;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblStartingPrice;
    @FXML private Label lblBidCount;
    @FXML private TextArea txtDescription;
    @FXML private ScrollPane detailsScrollPane;
    @FXML private Label lblCountdown;
    @FXML private HBox extraInfoContainer;
    @FXML private Label lblHighestBidder;
    @FXML private Label lblBidTime;
    @FXML private ImageView itemImageView;
    @FXML private VBox noImagePlaceholder;

    @FXML private AreaChart<Number, Number> bidChart;
    @FXML private NumberAxis bidXAxis;
    @FXML private NumberAxis bidYAxis;

    @FXML private ListView<BidTransaction> bidHistoryList;

    @FXML private VBox biddingContent;
    @FXML private VBox paneNoPermission;
    @FXML private Label lblNoPermission;
    @FXML private VBox paneStatusMessage;
    @FXML private Label lblStatusMessage;

    @FXML private VBox paneManualBid;
    @FXML private Button btnOpenBid;
    @FXML private Button btnPay;
    @FXML private VBox paneWinner;
    @FXML private Label lblWinnerName;
    @FXML private VBox panePayWinner;

    @FXML private VBox paneAutoBid;
    @FXML private TextField txtAutoMaxBid;
    @FXML private TextField txtAutoIncrement;
    @FXML private Button btnSetAutoBid;
    @FXML private Button btnCancelAutoBid;

    @FXML private ToggleGroup bidTypeGroup;
    @FXML private ToggleButton tglManualBid;
    @FXML private ToggleButton tglAutoBid;

    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnCancel;
    @FXML private Button btnAddFriend;
    @FXML private Button btnChatSeller;

    @FXML private ProgressBar progressTimeBar;
    @FXML private VBox paneAntiSnipeNotif;

    private final ObservableList<BidTransaction> bidRows = FXCollections.observableArrayList();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private Auction auction;
    private String autoBidCheckedAuctionId;
    private javafx.animation.Timeline countdownTimeline;
    private javafx.animation.ScaleTransition pulseAnimation;
    private double lastDisplayedPrice = -1.0;
    private java.util.function.Consumer<String> antiSnipeListener;
    private java.util.function.Consumer<String> friendStatusListener;
    private String currentFriendStatus = "NONE";

    @FXML
    private void initialize() {
        setupBidHistoryList();
        setupBidChart();
        setupSegmentedControl();
    }

    private void setupSegmentedControl() {
        if (bidTypeGroup == null) return;
        
        bidTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                if (oldVal != null) oldVal.setSelected(true); // Ngăn không cho bỏ chọn cả 2
                return;
            }
            boolean isAuto = (newVal == tglAutoBid);
            paneManualBid.setVisible(!isAuto);
            paneManualBid.setManaged(!isAuto);
            paneAutoBid.setVisible(isAuto);
            paneAutoBid.setManaged(isAuto);
        });
    }

    public Auction getAuction() {
        return auction;
    }

    /**
     * Khởi tạo hoặc refresh dữ liệu của phiên đang mở.
     */
    public void setItemData(Auction auction) {
        // Đăng ký listener anti-snipe một lần duy nhất khi cửa sổ mở lần đầu
        if (antiSnipeListener == null) {
            antiSnipeListener = msg -> {
                if (!msg.startsWith("ANTI_SNIPE_EXTENDED:")) return;
                String extId = msg.substring("ANTI_SNIPE_EXTENDED:".length());
                if (this.auction != null && extId.equals(this.auction.getAuctionId())) {
                    Platform.runLater(this::showAntiSnipeNotification);
                }
            };
            AppState.getInstance().getClient().addStringMessageListener(antiSnipeListener);
        }

        this.auction = auction;
        refreshUI();
        setupPermissions();
        scrollToTop();
        startCountdown();

        if (auction != null && !auction.getAuctionId().equals(autoBidCheckedAuctionId)) {
            autoBidCheckedAuctionId = auction.getAuctionId();
            checkExistingAutoBid();
        }
    }

    private void startCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        stopPulseAnimation();
        if (auction == null || lblCountdown == null) return;

        countdownTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), event -> updateCountdownLabel())
        );
        countdownTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        countdownTimeline.play();
        updateCountdownLabel(); // Cập nhật ngay lần đầu
    }

    private void updateCountdownLabel() {
        if (auction == null || lblCountdown == null) return;

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime start = auction.getStartTime();
        java.time.LocalDateTime end = auction.getEndTime();
        String status = auction.getStatus();

        if (status.equals("FINISHED") || status.equals("PAID") || status.equals("CANCELED") || now.isAfter(end)) {
            lblCountdown.setText("Đã kết thúc");
            lblCountdown.getStyleClass().remove("countdown-urgent");
            stopPulseAnimation();
            setProgressBar(0.0, false);
            if (countdownTimeline != null) countdownTimeline.stop();
            return;
        }

        if (status.equals("OPEN") && now.isBefore(start)) {
            java.time.Duration duration = java.time.Duration.between(now, start);
            lblCountdown.setText(String.format("%02d:%02d:%02d",
                    duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart()));
            stopPulseAnimation();
            setProgressBar(1.0, false);
            return;
        }

        java.time.Duration duration = java.time.Duration.between(now, end);
        lblCountdown.setText(String.format("%02d:%02d:%02d",
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart()));

        // FOMO effect khi còn dưới 5 phút
        if (duration.toMinutes() < 5) {
            if (!lblCountdown.getStyleClass().contains("countdown-urgent")) {
                lblCountdown.getStyleClass().add("countdown-urgent");
            }
        } else {
            lblCountdown.getStyleClass().remove("countdown-urgent");
        }

        // Pulse animation khi còn dưới 30 giây
        if (duration.toSeconds() >= 0 && duration.toSeconds() < 30) {
            if (pulseAnimation == null) {
                pulseAnimation = new javafx.animation.ScaleTransition(
                        javafx.util.Duration.millis(500), lblCountdown);
                pulseAnimation.setFromX(1.0);
                pulseAnimation.setToX(1.1);
                pulseAnimation.setFromY(1.0);
                pulseAnimation.setToY(1.1);
                pulseAnimation.setAutoReverse(true);
                pulseAnimation.setCycleCount(javafx.animation.Animation.INDEFINITE);
                pulseAnimation.play();
            }
        } else {
            stopPulseAnimation();
        }

        // Progress bar: tỉ lệ thời gian còn lại / tổng thời gian
        if (start != null && end != null) {
            long totalSeconds = java.time.Duration.between(start, end).toSeconds();
            double progress = totalSeconds > 0
                    ? Math.max(0.0, (double) duration.toSeconds() / totalSeconds)
                    : 0.0;
            setProgressBar(progress, progress < 0.1);
        }
    }

    private void stopPulseAnimation() {
        if (pulseAnimation != null) {
            pulseAnimation.stop();
            pulseAnimation = null;
        }
        if (lblCountdown != null) {
            lblCountdown.setScaleX(1.0);
            lblCountdown.setScaleY(1.0);
        }
    }

    private void setProgressBar(double progress, boolean urgent) {
        if (progressTimeBar == null) return;
        progressTimeBar.setProgress(progress);
        progressTimeBar.getStyleClass().remove("time-progress-urgent");
        if (urgent) {
            progressTimeBar.getStyleClass().add("time-progress-urgent");
        }
    }

    private void flashPriceLabel() {
        if (lblCurrentPrice == null) return;
        javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(
                javafx.util.Duration.millis(200), lblCurrentPrice);
        st.setFromX(1.0); st.setToX(1.15);
        st.setFromY(1.0); st.setToY(1.15);
        st.setAutoReverse(true); st.setCycleCount(2);
        st.play();
    }

    private void showAntiSnipeNotification() {
        if (paneAntiSnipeNotif == null) return;
        paneAntiSnipeNotif.setOpacity(0);
        paneAntiSnipeNotif.setVisible(true);
        paneAntiSnipeNotif.setManaged(true);

        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(400), paneAntiSnipeNotif);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);

        javafx.animation.Timeline hideTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                    javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
                            javafx.util.Duration.millis(600), paneAntiSnipeNotif);
                    fadeOut.setFromValue(1); fadeOut.setToValue(0);
                    fadeOut.setOnFinished(ev -> {
                        paneAntiSnipeNotif.setVisible(false);
                        paneAntiSnipeNotif.setManaged(false);
                    });
                    fadeOut.play();
                })
        );
        fadeIn.setOnFinished(e -> hideTimer.play());
        fadeIn.play();
    }

    private void scrollToTop() {
        if (detailsScrollPane == null) return;

        // Gọi ngay để reset state hiện tại, rồi gọi lại sau layout pass đầu tiên
        // vì JavaFX đôi khi tự cuộn xuống control vừa nhận focus.
        detailsScrollPane.setVvalue(0);
        Platform.runLater(() -> detailsScrollPane.setVvalue(0));
    }

    private void setupBidHistoryList() {
        if (bidHistoryList == null) return;

        bidHistoryList.setItems(bidRows);
        bidHistoryList.setPlaceholder(new Label("Chưa có lượt đặt giá nào"));
        bidHistoryList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(BidTransaction bid, boolean empty) {
                super.updateItem(bid, empty);
                if (empty || bid == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setGraphic(createBidRowLayout(bid));
                    setStyle("-fx-background-color: transparent; -fx-padding: 6 0 6 0;");
                }
            }
        });
    }

    /**
     * Tạo layout "Activity Feed" cho mỗi dòng bid.
     */
    private javafx.scene.Node createBidRowLayout(BidTransaction bid) {
        HBox container = new HBox(15);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("bid-feed-item");

        // 1. Avatar (Initial)
        String name = (bid.getBidder() != null) ? bid.getBidder().getUsername() : "Ẩn danh";
        String initial = name.substring(0, 1).toUpperCase();
        Label lblAvatar = new Label(initial);
        lblAvatar.getStyleClass().add("bid-avatar");
        // Đổi màu avatar dựa trên tên để tạo sự khác biệt
        int colorIdx = Math.abs(name.hashCode() % 5);
        lblAvatar.getStyleClass().add("avatar-color-" + colorIdx);

        // 2. Nội dung chính (User + Hành động)
        VBox vContent = new VBox(2);
        vContent.setAlignment(Pos.CENTER_LEFT);

        Label lblUser = new Label(name);
        lblUser.getStyleClass().add("bid-user-name");

        Label lblAction = new Label("đã đặt giá cho sản phẩm");
        lblAction.getStyleClass().add("bid-action-text");

        HBox hUserBox = new HBox(6, lblUser, lblAction);
        hUserBox.setAlignment(Pos.BASELINE_LEFT);

        // Badge loại bid
        Label badge = new Label(bid.getBidType().getDisplayName());
        badge.getStyleClass().addAll("bid-type-badge",
                bid.getBidType() == BidTransaction.BidType.AUTO_BID ? "badge-auto" : "badge-manual");

        vContent.getChildren().addAll(hUserBox, badge);

        // 3. Thông tin giá và thời gian (Bên phải)
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox vPrice = new VBox(2);
        vPrice.setAlignment(Pos.CENTER_RIGHT);

        Label lblAmount = new Label(String.format("%,.0f ₫", bid.getBidAmount()));
        lblAmount.getStyleClass().add("bid-feed-amount");

        Label lblTime = new Label(bid.getTimestamp().format(dateTimeFormatter));
        lblTime.getStyleClass().add("bid-feed-time");

        vPrice.getChildren().addAll(lblAmount, lblTime);

        container.getChildren().addAll(lblAvatar, vContent, spacer, vPrice);
        return container;
    }

    private void setupBidChart() {
        if (bidChart == null || bidXAxis == null || bidYAxis == null) return;

        bidChart.setAnimated(false);
        bidChart.setCreateSymbols(true);
        bidChart.setLegendVisible(false);

        bidXAxis.setForceZeroInRange(false);
        bidXAxis.setAutoRanging(false);
        bidXAxis.setMinorTickVisible(false);
        bidXAxis.setTickLabelsVisible(false);
        bidXAxis.setTickMarkVisible(false);

        bidYAxis.setForceZeroInRange(false);
        bidYAxis.setAutoRanging(false);
        bidYAxis.setMinorTickVisible(false);
        bidYAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                if (value == null) return "";
                return String.format("%,.0f ₫", value.doubleValue());
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Kiểm tra auto-bid hiện có của user hiện tại cho phiên này.
     */
    private void checkExistingAutoBid() {
        if (auction == null) return;
        if (!(AppState.getInstance().getCurrentUser() instanceof Bidder)) return;
        if (!"RUNNING".equals(auction.getStatus())) return;

        var user = AppState.getInstance().getCurrentUser();
        String cmd = "GET_MY_AUTOBID:" + auction.getAuctionId() + ":" + user.getUserId();

        AppState.getInstance().getClient().setStringMessageCallback(this::handleAutoBidResponse);
        AppState.getInstance().getClient().send(cmd);
    }

    /**
     * Cập nhật toàn bộ màn chi tiết, bao gồm chart và bảng lịch sử.
     */
    private void refreshUI() {
        if (auction == null) return;

        lblItemName.setText(auction.getItemName());
        lblCategory.setText(auction.getItem().getClass().getSimpleName());
        lblStartingPrice.setText(String.format("%,.0f ₫", auction.getItem().getStartingPrice()));

        double newPrice = auction.getCurrentPrice();
        lblCurrentPrice.setText(String.format("%,.0f ₫", newPrice));
        if (lastDisplayedPrice >= 0 && newPrice > lastDisplayedPrice) {
            flashPriceLabel();
        }
        lastDisplayedPrice = newPrice;

        txtDescription.setText(auction.getItem().getDescription());

        // Cập nhật Property Chips
        extraInfoContainer.getChildren().clear();
        var item = auction.getItem();
        if (item instanceof Electronics elec) {
            extraInfoContainer.getChildren().add(createPropertyChip("Hãng sản xuất", elec.getBrand()));
        } else if (item instanceof Art art) {
            extraInfoContainer.getChildren().add(createPropertyChip("Tác giả", art.getArtist()));
        } else if (item instanceof Vehicle veh) {
            extraInfoContainer.getChildren().add(createPropertyChip("Hãng xe", veh.getBrand()));
        } else {
            Label defaultLbl = new Label("Chưa có thông số kỹ thuật");
            defaultLbl.getStyleClass().add("details-muted-text");
            extraInfoContainer.getChildren().add(defaultLbl);
        }

        List<BidTransaction> history = new ArrayList<>(auction.getBidHistory());

        if (!history.isEmpty()) {
            BidTransaction latestBid = history.get(history.size() - 1);
            lblHighestBidder.setText(latestBid.getBidder() != null
                    ? latestBid.getBidder().getUsername()
                    : "Ẩn danh");
            lblBidTime.setText(latestBid.getTimestamp().format(dateTimeFormatter));

            // Hiển thị người thắng nếu phiên đã kết thúc
            if ("FINISHED".equals(auction.getStatus()) || "PAID".equals(auction.getStatus())) {
                paneWinner.setVisible(true);
                paneWinner.setManaged(true);
                lblWinnerName.setText(latestBid.getBidder() != null ? latestBid.getBidder().getUsername() : "Ẩn danh");
            } else {
                paneWinner.setVisible(false);
                paneWinner.setManaged(false);
            }
        } else {
            lblHighestBidder.setText("Chưa có ai dẫn đầu");
            lblBidTime.setText("-");
            paneWinner.setVisible(false);
            paneWinner.setManaged(false);
        }

        lblBidCount.setText(String.valueOf(history.size()));
        renderBidHistory(history);
        renderBidChart(history);
        refreshItemImage();
    }

    private Label createPropertyChip(String property, String value) {
        Label chip = new Label(property + ": " + value);
        chip.getStyleClass().add("property-chip");
        return chip;
    }

    private void refreshItemImage() {
        if (itemImageView == null || auction == null || auction.getItem() == null) return;

        String imgPath = auction.getItem().getImagePath();
        if (imgPath != null && !imgPath.isBlank()) {
            String uri = ImageStorageService.toImageUrl(imgPath);
            if (uri != null) {
                itemImageView.setImage(new Image(uri, 360, 0, true, true));
                itemImageView.setVisible(true);
                if (noImagePlaceholder != null) noImagePlaceholder.setVisible(false);
                return;
            }
        }
        itemImageView.setImage(null);
        itemImageView.setVisible(false);
        if (noImagePlaceholder != null) noImagePlaceholder.setVisible(true);
    }

    private void renderBidHistory(List<BidTransaction> history) {
        List<BidTransaction> newestFirst = new ArrayList<>(history);
        Collections.reverse(newestFirst);
        bidRows.setAll(newestFirst);
        scrollBidHistoryToTop();
    }

    /**
     * Cuộn lên đầu danh sách để luôn thấy bid mới nhất.
     */
    private void scrollBidHistoryToTop() {
        if (bidHistoryList == null) return;

        bidHistoryList.getSelectionModel().clearSelection();
        bidHistoryList.scrollTo(0);

        Platform.runLater(() -> {
            bidHistoryList.getSelectionModel().clearSelection();
            bidHistoryList.scrollTo(0);
        });
    }

    private void renderBidChart(List<BidTransaction> history) {
        if (bidChart == null || bidXAxis == null || bidYAxis == null || auction == null) return;

        List<BidTransaction> visibleHistory = getRecentBidsForChart(history);
        double startingPrice = auction.getItem().getStartingPrice();
        double maxPrice = Math.max(startingPrice, auction.getCurrentPrice());
        for (BidTransaction bid : visibleHistory) {
            maxPrice = Math.max(maxPrice, bid.getBidAmount());
        }

        configureYAxis(startingPrice, maxPrice);
        configureXAxis(visibleHistory.size());

        bidChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();

        // Điểm 0: giá khởi điểm khi tạo phiên
        XYChart.Data<Number, Number> startPoint = new XYChart.Data<>(0, startingPrice);
        startPoint.setNode(createStartingPoint(startingPrice));
        series.getData().add(startPoint);

        for (int i = 0; i < visibleHistory.size(); i++) {
            BidTransaction bid = visibleHistory.get(i);
            XYChart.Data<Number, Number> point = new XYChart.Data<>(i + 1, bid.getBidAmount());
            point.setNode(createBidPoint(bid));
            series.getData().add(point);
        }
        bidChart.getData().add(series);
    }

    /**
     * Biểu đồ chỉ theo dõi tối đa 10 bid gần nhất; bảng lịch sử vẫn giữ toàn bộ dữ liệu.
     */
    private List<BidTransaction> getRecentBidsForChart(List<BidTransaction> history) {
        if (history.size() <= MAX_VISIBLE_BIDS_ON_CHART) {
            return history;
        }
        return history.subList(history.size() - MAX_VISIBLE_BIDS_ON_CHART, history.size());
    }

    private void configureXAxis(int bidCount) {
        bidXAxis.setLowerBound(0);
        bidXAxis.setUpperBound(Math.max(1, bidCount));
        bidXAxis.setTickUnit(1);
    }

    private void configureYAxis(double startingPrice, double maxPrice) {
        double tickUnit = calculateTickUnit(startingPrice, maxPrice);
        double upperBound = roundUp(maxPrice + tickUnit, tickUnit);
        while ((upperBound - startingPrice) / tickUnit < 4) {
            upperBound += tickUnit;
        }

        bidYAxis.setLowerBound(startingPrice);
        bidYAxis.setUpperBound(upperBound);
        bidYAxis.setTickUnit(tickUnit);
    }

    /**
     * Chọn bước chia "đẹp" theo độ lớn hiện tại của phiên.
     * Ví dụ vùng giá quanh 5,000 sẽ thường cho các nấc cỡ 200.
     */
    private double calculateTickUnit(double startingPrice, double maxPrice) {
        double span = Math.max(maxPrice - startingPrice, Math.max(maxPrice * 0.12, 1));
        double roughStep = span / 4.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(roughStep)));
        double normalized = roughStep / magnitude;

        double niceNormalized;
        if (normalized <= 1) {
            niceNormalized = 1;
        } else if (normalized <= 2) {
            niceNormalized = 2;
        } else if (normalized <= 5) {
            niceNormalized = 5;
        } else {
            niceNormalized = 10;
        }
        return niceNormalized * magnitude;
    }

    private double roundUp(double value, double step) {
        return Math.ceil(value / step) * step;
    }

    private StackPane createStartingPoint(double startingPrice) {
        StackPane point = new StackPane();
        point.getStyleClass().add("start-point");
        point.setPrefSize(10, 10);
        point.setMinSize(10, 10);
        point.setMaxSize(10, 10);
        Tooltip.install(point, new Tooltip(
                "Giá khởi điểm\n" + String.format("%,.0f ₫", startingPrice)
        ));
        return point;
    }

    private StackPane createBidPoint(BidTransaction bid) {
        StackPane point = new StackPane();
        point.getStyleClass().add("bid-point");
        point.setPrefSize(10, 10);
        point.setMinSize(10, 10);
        point.setMaxSize(10, 10);

        String bidderName = bid.getBidder() != null ? bid.getBidder().getUsername() : "Ẩn danh";
        Tooltip.install(point, new Tooltip(
                bidderName + "\n"
                        + String.format("%,.0f ₫", bid.getBidAmount()) + "\n"
                        + bid.getTimestamp().format(dateTimeFormatter) + "\n"
                        + bid.getBidType().getDisplayName()
        ));
        return point;
    }

    /**
     * Kiểm tra quyền để hiển thị các nút chức năng.
     */
    private void setupPermissions() {
        User currentUser = AppState.getInstance().getCurrentUser();
        boolean isOwnAuction = currentUser != null
                && currentUser.getUserId().equals(auction.getSellerId());
        boolean isBidder = (currentUser instanceof Bidder) && !isOwnAuction;
        String status = auction.getStatus();
        boolean canBid = isBidder && "RUNNING".equals(status);
        boolean canEdit = SessionPermission.canEdit(auction);
        boolean canDelete = SessionPermission.canDelete(auction);
        boolean canCancel = SessionPermission.canCancel(auction);

        if (biddingContent != null && paneNoPermission != null && paneStatusMessage != null) {
            if (!isBidder) {
                biddingContent.setVisible(false);
                biddingContent.setManaged(false);
                paneNoPermission.setVisible(true);
                paneNoPermission.setManaged(true);
                paneStatusMessage.setVisible(false);
                paneStatusMessage.setManaged(false);
                if (lblNoPermission != null) {
                    if (currentUser instanceof Seller && isOwnAuction) {
                        lblNoPermission.setText(
                                "Bạn không thể đặt giá cho sản phẩm của chính mình.");
                    } else {
                        lblNoPermission.setText(
                                "Chỉ người mua (Bidder) mới có quyền tham gia đấu giá tại phiên này.");
                    }
                }
            } else {
                paneNoPermission.setVisible(false);
                paneNoPermission.setManaged(false);
                
                if (canBid) {
                    biddingContent.setVisible(true);
                    biddingContent.setManaged(true);
                    paneStatusMessage.setVisible(false);
                    paneStatusMessage.setManaged(false);
                } else {
                    biddingContent.setVisible(false);
                    biddingContent.setManaged(false);
                    paneStatusMessage.setVisible(true);
                    paneStatusMessage.setManaged(true);
                    
                    if ("OPEN".equals(status)) {
                        lblStatusMessage.setText("Phiên đấu giá chưa bắt đầu. Vui lòng quay lại sau.");
                    } else {
                        lblStatusMessage.setText("Phiên đấu giá này đã kết thúc.");
                    }
                }
            }
        }

        String winnerId = (auction.getHighestBid() != null
                && auction.getHighestBid().getBidder() != null)
                ? auction.getHighestBid().getBidder().getUserId()
                : null;
        boolean canPay = SessionPermission.canPay(auction, winnerId);

        if (btnOpenBid != null) {
            btnOpenBid.setVisible(canBid);
            btnOpenBid.setManaged(canBid);
        }
        if (btnEdit != null) {
            btnEdit.setVisible(canEdit);
            btnEdit.setManaged(canEdit);
        }
        if (btnDelete != null) {
            btnDelete.setVisible(canDelete);
            btnDelete.setManaged(canDelete);
        }
        if (btnCancel != null) {
            btnCancel.setVisible(canCancel);
            btnCancel.setManaged(canCancel);
        }

        if (btnChatSeller != null && btnAddFriend != null) {
            model.user.User cu = model.manager.AppState.getInstance().getCurrentUser();
            String sellerId = auction.getSellerId();
            boolean isOtherUser = cu != null && sellerId != null && !cu.getUserId().equals(sellerId);
            if (isOtherUser) {
                // Start async friend-status check; buttons appear after response
                sendFriendStatusRequest(sellerId);
            } else {
                btnChatSeller.setVisible(false); btnChatSeller.setManaged(false);
                btnAddFriend.setVisible(false); btnAddFriend.setManaged(false);
            }
        }

        if (panePayWinner != null) {
            panePayWinner.setVisible(canPay);
            panePayWinner.setManaged(canPay);
        }
    }

    @FXML
    public void handleSetAutoBid() {
        String maxStr = txtAutoMaxBid.getText().trim();
        String incStr = txtAutoIncrement.getText().trim();

        if (maxStr.isEmpty() || incStr.isEmpty()) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Thiếu thông tin", "Vui lòng nhập giá tối đa và bước giá.");
            return;
        }

        try {
            double maxBid = Double.parseDouble(maxStr);
            double increment = Double.parseDouble(incStr);
            double currentPrice = auction.getCurrentPrice();

            if (maxBid <= currentPrice) {
                AlertHelper.show(AlertHelper.Type.ERROR, "Giá không hợp lệ", "Giá tối đa phải lớn hơn giá hiện tại.");
                return;
            }
            if (increment <= 0) {
                AlertHelper.show(AlertHelper.Type.ERROR, "Bước giá không hợp lệ", "Bước giá phải lớn hơn 0.");
                return;
            }

            var user = AppState.getInstance().getCurrentUser();
            String cmd = String.format("SET_AUTOBID:%s:%s:%.2f:%.2f",
                    auction.getAuctionId(), user.getUserId(), maxBid, increment);

            AppState.getInstance().getClient().setStringMessageCallback(this::handleAutoBidResponse);
            AppState.getInstance().getClient().send(cmd);

            btnSetAutoBid.setDisable(true);
            btnSetAutoBid.setText("Đang xử lý...");

        } catch (NumberFormatException e) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi định dạng", "Vui lòng nhập số hợp lệ.");
        }
    }

    @FXML
    public void handleCancelAutoBid() {
        boolean confirm = AlertHelper.showConfirm("Xác nhận hủy",
                "Bạn có chắc chắn muốn hủy đấu giá tự động không?\n"
                        + "Số tiền đang bị khóa sẽ được hoàn trả vào ví của bạn.");

        if (!confirm) return;

        var user = AppState.getInstance().getCurrentUser();
        String cmd = "CANCEL_AUTOBID:" + auction.getAuctionId() + ":" + user.getUserId();

        AppState.getInstance().getClient().setStringMessageCallback(this::handleAutoBidResponse);
        AppState.getInstance().getClient().send(cmd);

        btnCancelAutoBid.setDisable(true);
    }

    private void handleAutoBidResponse(String msg) {
        Platform.runLater(() -> {
            if (msg.contains(":") && !msg.startsWith("AUTOBID_FAILED")) {
                String[] parts = msg.split(":");
                if (parts.length > 1 && !parts[1].equals(auction.getAuctionId())) return;
            }

            if (msg.startsWith("AUTOBID_OK")) {
                String[] parts = msg.split(":");
                if (parts.length >= 3) {
                    try {
                        double balance = Double.parseDouble(parts[2]);
                        AppState.getInstance().getCurrentUser().setBalance(balance);
                    } catch (NumberFormatException ignore) {
                    }
                }
                AppState.getInstance().setMyAutoBid(auction.getAuctionId(), true);
                AlertHelper.show(AlertHelper.Type.SUCCESS, "Thành công", "Đã thiết lập đấu giá tự động!");
                updateAutoBidUI(true, txtAutoMaxBid.getText(), txtAutoIncrement.getText());

            } else if (msg.startsWith("MY_AUTOBID:")) {
                String[] parts = msg.split(":");
                if (parts.length >= 4) {
                    AppState.getInstance().setMyAutoBid(auction.getAuctionId(), true);
                    updateAutoBidUI(true, parts[2], parts[3]);
                }

            } else if (msg.startsWith("MY_AUTOBID_NONE")) {
                AppState.getInstance().setMyAutoBid(auction.getAuctionId(), false);
                updateAutoBidUI(false, "", "");

            } else if (msg.startsWith("CANCEL_AUTOBID_OK")) {
                String[] parts = msg.split(":");
                if (parts.length >= 3) {
                    try {
                        double balance = Double.parseDouble(parts[2]);
                        AppState.getInstance().getCurrentUser().setBalance(balance);
                    } catch (NumberFormatException ignore) {
                    }
                }
                AppState.getInstance().setMyAutoBid(auction.getAuctionId(), false);
                AlertHelper.show(AlertHelper.Type.INFO, "Đã hủy",
                        "Đã hủy đấu giá tự động và hoàn lại tiền khóa.");
                updateAutoBidUI(false, "", "");

            } else if (msg.startsWith("AUTOBID_FAILED")) {
                String reason = msg.substring("AUTOBID_FAILED:".length());
                AlertHelper.show(AlertHelper.Type.ERROR, "Thất bại",
                        "Không thể thiết lập Auto-Bid: " + reason);
                btnSetAutoBid.setDisable(false);
                btnSetAutoBid.setText("Thiết lập Auto-Bid");
            }
        });
    }

    private void updateAutoBidUI(boolean active, String max, String inc) {
        btnSetAutoBid.setVisible(!active);
        btnSetAutoBid.setManaged(!active);
        btnSetAutoBid.setDisable(false);
        btnSetAutoBid.setText("Thiết lập Auto-Bid");

        btnCancelAutoBid.setVisible(active);
        btnCancelAutoBid.setManaged(active);
        btnCancelAutoBid.setDisable(false);

        txtAutoMaxBid.setText(max);
        txtAutoIncrement.setText(inc);
        txtAutoMaxBid.setEditable(!active);
        txtAutoIncrement.setEditable(!active);
    }

    @FXML
    public void handleOpenBidDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();

            BidController controller = loader.getController();
            controller.setAuctionData(this.auction);

            Stage stage = new Stage();
            stage.setTitle("Đặt giá cho " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleEdit() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/edit_session.fxml"));
            Parent root = loader.load();

            EditSessionController controller = loader.getController();
            controller.setAuction(this.auction);
            controller.setOnSavedCallback(this::closeWindow);

            Stage stage = new Stage();
            stage.setTitle("Sửa phiên: " + auction.getItemName());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleOpenDelete() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConfirmDelete.fxml"));
            Parent root = loader.load();

            ConfirmDeleteController controller = loader.getController();
            controller.setAuctionData(this.auction);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initOwner(lblItemName.getScene().getWindow());

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            stage.setOnShown(event -> centerOnOwner(stage));
            stage.showAndWait();

            if (controller.isConfirmed()) {
                executeDelete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeDelete() {
        if (this.auction == null) return;

        String auctionId = this.auction.getAuctionId();
        String requesterId = AppState.getInstance().getCurrentUser().getUserId();
        String command = "DELETE_AUCTION:" + auctionId + ":" + requesterId;
        AppState.getInstance().getClient().send(command);

        closeWindow();
        AlertHelper.show(AlertHelper.Type.INFO, "Yêu cầu xóa đã được gửi đi.");
    }

    @FXML
    void handleCancelAuction() {
        if (this.auction == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConfirmCancel.fxml"));
            Parent root = loader.load();

            ConfirmCancelController controller = loader.getController();
            controller.setAuctionData(this.auction);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initOwner(lblItemName.getScene().getWindow());

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            stage.setOnShown(event -> centerOnOwner(stage));
            stage.showAndWait();

            if (controller.isConfirmed()) {
                String auctionId = this.auction.getAuctionId();
                String requesterId = AppState.getInstance().getCurrentUser().getUserId();
                String command = "CANCEL_AUCTION:" + auctionId + ":" + requesterId;
                AppState.getInstance().getClient().send(command);

                closeWindow();
                AlertHelper.show(AlertHelper.Type.INFO, "Yêu cầu hủy phiên đã được gửi đi.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handlePay() {
        if (this.auction == null) return;
        if (this.auction.getHighestBid() == null) {
            AlertHelper.show(AlertHelper.Type.ERROR, "Phiên này không có người thắng");
            return;
        }

        var currentUser = AppState.getInstance().getCurrentUser();
        if (currentUser == null) return;

        double finalPrice = this.auction.getCurrentPrice();
        double currentBalance = currentUser.getBalance();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConfirmPay.fxml"));
            Parent root = loader.load();

            ConfirmPayController controller = loader.getController();
            controller.setPaymentData(finalPrice, currentBalance);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.initOwner(lblItemName.getScene().getWindow());

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            stage.setOnShown(event -> centerOnOwner(stage));
            stage.showAndWait();

            if (controller.isConfirmed()) {
                AppState.getInstance().getClient().setStringMessageCallback(this::handlePayResponse);
                String command = "PAY_AUCTION:" + auction.getAuctionId() + ":" + currentUser.getUserId();
                AppState.getInstance().getClient().send(command);

                if (btnPay != null) btnPay.setDisable(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePayResponse(String msg) {
        if (!msg.startsWith("PAY_OK") && !msg.startsWith("PAY_FAILED")) return;

        AppState.getInstance().getClient().setStringMessageCallback(null);
        if (btnPay != null) btnPay.setDisable(false);

        if (msg.startsWith("PAY_OK:")) {
            String[] parts = msg.split(":");
            if (parts.length >= 3) {
                try {
                    double newBalance = Double.parseDouble(parts[2]);
                    var user = AppState.getInstance().getCurrentUser();
                    if (user != null) user.setBalance(newBalance);
                } catch (NumberFormatException ignore) {
                }
            }

            AlertHelper.show(
                    AlertHelper.Type.SUCCESS,
                    "Thanh toán thành công",
                    "Bạn đã thanh toán thành công cho phiên này. Sản phẩm chính thức thuộc về bạn!"
            );
            closeWindow();
        } else {
            String reason = msg.substring("PAY_FAILED:".length()).trim();
            AlertHelper.show(AlertHelper.Type.ERROR, "Thanh toán thất bại", reason);
        }
    }

    private void centerOnOwner(Stage stage) {
        Stage owner = (Stage) stage.getOwner();
        if (owner == null) return;

        double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0;
        double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0;
        stage.setX(x);
        stage.setY(y);
    }

    @FXML
    void handleCancel() {
        closeWindow();
    }

    @FXML
    void handleChatSeller() {
        if (auction == null || auction.getSellerId() == null) return;
        String sellerId = auction.getSellerId();
        model.user.User seller = new database.UserDAO().findById(sellerId);
        String name = seller != null ? seller.getUsername() : sellerId;
        String avatar = seller != null ? seller.getAvatarPath() : null;
        AppState.getInstance().requestOpenChat(sellerId, name, avatar);
        closeWindow();
    }

    private void sendFriendStatusRequest(String sellerId) {
        User cu = AppState.getInstance().getCurrentUser();
        if (cu == null) return;
        if (friendStatusListener != null) {
            AppState.getInstance().getClient().removeStringMessageListener(friendStatusListener);
        }
        final String prefix = "FRIEND_STATUS_RESULT:" + sellerId + ":";
        friendStatusListener = msg -> {
            if (!msg.startsWith(prefix)) return;
            String status = msg.substring(prefix.length());
            currentFriendStatus = status;
            Platform.runLater(() -> applyFriendButtons(status));
        };
        AppState.getInstance().getClient().addStringMessageListener(friendStatusListener);
        AppState.getInstance().getClient().send("FRIEND_STATUS:" + cu.getUserId() + ":" + sellerId);
    }

    private void applyFriendButtons(String status) {
        switch (status) {
            case "ACCEPTED" -> {
                btnChatSeller.setVisible(true); btnChatSeller.setManaged(true);
                btnAddFriend.setVisible(false); btnAddFriend.setManaged(false);
            }
            case "PENDING_SENT" -> {
                btnChatSeller.setVisible(false); btnChatSeller.setManaged(false);
                btnAddFriend.setText("Đã gửi lời mời");
                btnAddFriend.setDisable(true);
                btnAddFriend.setVisible(true); btnAddFriend.setManaged(true);
            }
            case "PENDING_RECEIVED" -> {
                btnChatSeller.setVisible(false); btnChatSeller.setManaged(false);
                btnAddFriend.setText("Chấp nhận lời mời");
                btnAddFriend.setDisable(false);
                btnAddFriend.setVisible(true); btnAddFriend.setManaged(true);
            }
            default -> {
                btnChatSeller.setVisible(false); btnChatSeller.setManaged(false);
                btnAddFriend.setText("+ Kết bạn");
                btnAddFriend.setDisable(false);
                btnAddFriend.setVisible(true); btnAddFriend.setManaged(true);
            }
        }
    }

    @FXML
    void handleFriendAction() {
        if (auction == null || auction.getSellerId() == null) return;
        String sellerId = auction.getSellerId();
        if ("PENDING_RECEIVED".equals(currentFriendStatus)) {
            FriendCenter.accept(sellerId);
        } else {
            FriendCenter.sendRequest(sellerId);
            btnAddFriend.setText("Đã gửi lời mời");
            btnAddFriend.setDisable(true);
        }
    }

    private void closeWindow() {
        if (antiSnipeListener != null) {
            AppState.getInstance().getClient().removeStringMessageListener(antiSnipeListener);
            antiSnipeListener = null;
        }
        if (friendStatusListener != null) {
            AppState.getInstance().getClient().removeStringMessageListener(friendStatusListener);
            friendStatusListener = null;
        }
        stopPulseAnimation();
        if (lblItemName != null && lblItemName.getScene() != null) {
            Stage stage = (Stage) lblItemName.getScene().getWindow();
            stage.close();
        }
    }
}
