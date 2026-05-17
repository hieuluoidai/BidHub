package controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import utils.AlertHelper;
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
    @FXML private Label lblEndTime;
    @FXML private Label lblExtraInfo;
    @FXML private Label lblHighestBidder;
    @FXML private Label lblBidTime;
    @FXML private ImageView itemImageView;

    @FXML private LineChart<Number, Number> bidChart;
    @FXML private NumberAxis bidXAxis;
    @FXML private NumberAxis bidYAxis;

    @FXML private TableView<BidTransaction> bidHistoryTable;
    @FXML private TableColumn<BidTransaction, String> colBidder;
    @FXML private TableColumn<BidTransaction, String> colAmount;
    @FXML private TableColumn<BidTransaction, String> colTimestamp;
    @FXML private TableColumn<BidTransaction, String> colBidType;

    @FXML private Button btnOpenBid;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnCancel;
    @FXML private Button btnPay;

    @FXML private VBox paneAutoBid;
    @FXML private TextField txtAutoMaxBid;
    @FXML private TextField txtAutoIncrement;
    @FXML private Button btnSetAutoBid;
    @FXML private Button btnCancelAutoBid;

    private final ObservableList<BidTransaction> bidRows = FXCollections.observableArrayList();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final DateTimeFormatter endTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private Auction auction;
    private String autoBidCheckedAuctionId;

    @FXML
    private void initialize() {
        setupBidHistoryTable();
        setupBidChart();
    }

    public Auction getAuction() {
        return auction;
    }

    /**
     * Khởi tạo hoặc refresh dữ liệu của phiên đang mở.
     */
    public void setItemData(Auction auction) {
        this.auction = auction;
        refreshUI();
        setupPermissions();
        scrollToTop();

        if (auction != null && !auction.getAuctionId().equals(autoBidCheckedAuctionId)) {
            autoBidCheckedAuctionId = auction.getAuctionId();
            checkExistingAutoBid();
        }
    }

    private void scrollToTop() {
        if (detailsScrollPane == null) return;

        // Gọi ngay để reset state hiện tại, rồi gọi lại sau layout pass đầu tiên
        // vì JavaFX đôi khi tự cuộn xuống control vừa nhận focus.
        detailsScrollPane.setVvalue(0);
        Platform.runLater(() -> detailsScrollPane.setVvalue(0));
    }

    private void setupBidHistoryTable() {
        if (bidHistoryTable == null) return;

        bidHistoryTable.setItems(bidRows);
        bidHistoryTable.setPlaceholder(new Label("Chưa có lượt đặt giá nào"));

        colBidder.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getBidder() != null
                        ? cell.getValue().getBidder().getUsername()
                        : "Ẩn danh"
        ));
        colAmount.setCellValueFactory(cell -> new SimpleStringProperty(
                String.format("$%,.2f", cell.getValue().getBidAmount())
        ));
        colTimestamp.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getTimestamp().format(dateTimeFormatter)
        ));
        colBidType.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getBidType().getDisplayName()
        ));

        // Bảng lịch sử hoạt động như một stack: bid mới nhất luôn ở trên cùng.
        // Tắt sort thủ công để TableView không tự phá vỡ thứ tự đó khi refresh.
        colBidder.setSortable(false);
        colAmount.setSortable(false);
        colTimestamp.setSortable(false);
        colBidType.setSortable(false);
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
                return String.format("$%,.0f", value.doubleValue());
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
        lblStartingPrice.setText(String.format("$%,.2f", auction.getItem().getStartingPrice()));
        lblCurrentPrice.setText(String.format("$%,.2f", auction.getCurrentPrice()));
        txtDescription.setText(auction.getItem().getDescription());
        lblEndTime.setText(auction.getEndTime().format(endTimeFormatter));

        String extraInfoText = "Không có thông tin chi tiết";
        var item = auction.getItem();
        if (item instanceof Electronics elec) {
            extraInfoText = "Hãng sản xuất: " + elec.getBrand();
        } else if (item instanceof Art art) {
            extraInfoText = "Tác giả: " + art.getArtist();
        } else if (item instanceof Vehicle veh) {
            extraInfoText = "Hãng xe: " + veh.getBrand();
        }
        lblExtraInfo.setText(extraInfoText);

        // `bidHistory` đã được giữ theo thứ tự phát sinh: DB load từ cũ -> mới,
        // bid realtime mới thì append vào cuối. Không sort lại theo timestamp nữa,
        // vì sau restart clock DB/JVM có thể lệch nhau và làm bid mới rơi xuống đáy.
        List<BidTransaction> history = new ArrayList<>(auction.getBidHistory());

        if (!history.isEmpty()) {
            BidTransaction latestBid = history.get(history.size() - 1);
            lblHighestBidder.setText(latestBid.getBidder() != null
                    ? latestBid.getBidder().getUsername()
                    : "Ẩn danh");
            lblBidTime.setText(latestBid.getTimestamp().format(dateTimeFormatter));
        } else {
            lblHighestBidder.setText("Chưa có ai dẫn đầu");
            lblBidTime.setText("-");
        }

        lblBidCount.setText(String.valueOf(history.size()));
        renderBidHistory(history);
        renderBidChart(history);
        refreshItemImage();
    }

    private void refreshItemImage() {
        if (itemImageView == null || auction == null || auction.getItem() == null) return;

        String imgPath = auction.getItem().getImagePath();
        if (imgPath != null && !imgPath.isBlank()) {
            String uri = ImageStorageService.toFileUri(imgPath);
            if (uri != null) {
                itemImageView.setImage(new Image(uri, 360, 0, true, true));
                return;
            }
        }
        itemImageView.setImage(null);
    }

    private void renderBidHistory(List<BidTransaction> history) {
        List<BidTransaction> newestFirst = new ArrayList<>(history);
        Collections.reverse(newestFirst);
        bidRows.setAll(newestFirst);
        scrollBidHistoryToTop();
    }

    /**
     * TableView có xu hướng giữ selection/viewport cũ sau khi setAll(), vì vậy khi
     * mở lại tab nó có thể tự kéo về cuối danh sách dù dữ liệu đã được sắp đúng.
     * Bỏ selection cũ và ép về index 0 để bảng luôn bắt đầu từ bid mới nhất.
     */
    private void scrollBidHistoryToTop() {
        if (bidHistoryTable == null) return;

        bidHistoryTable.getSelectionModel().clearSelection();
        bidHistoryTable.scrollTo(0);

        // Gọi lại sau layout pass vì VirtualFlow của JavaFX đôi khi khôi phục
        // viewport cũ sau khi TableView vừa nhận danh sách mới.
        Platform.runLater(() -> {
            bidHistoryTable.getSelectionModel().clearSelection();
            bidHistoryTable.scrollTo(0);
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
        if (visibleHistory.isEmpty()) return;

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
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
        bidXAxis.setLowerBound(1);
        bidXAxis.setUpperBound(Math.max(2, bidCount));
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

    private StackPane createBidPoint(BidTransaction bid) {
        StackPane point = new StackPane();
        point.getStyleClass().add("bid-point");
        point.setPrefSize(10, 10);
        point.setMinSize(10, 10);
        point.setMaxSize(10, 10);

        String bidderName = bid.getBidder() != null ? bid.getBidder().getUsername() : "Ẩn danh";
        Tooltip.install(point, new Tooltip(
                bidderName + "\n"
                        + String.format("$%,.2f", bid.getBidAmount()) + "\n"
                        + bid.getTimestamp().format(dateTimeFormatter) + "\n"
                        + bid.getBidType().getDisplayName()
        ));
        return point;
    }

    /**
     * Kiểm tra quyền để hiển thị các nút chức năng.
     */
    private void setupPermissions() {
        boolean isBidder = (AppState.getInstance().getCurrentUser() instanceof Bidder);
        boolean canBid = isBidder && "RUNNING".equals(auction.getStatus());
        boolean canEdit = SessionPermission.canEdit(auction);
        boolean canDelete = SessionPermission.canDelete(auction);
        boolean canCancel = SessionPermission.canCancel(auction);

        if (paneAutoBid != null) {
            paneAutoBid.setVisible(canBid);
            paneAutoBid.setManaged(canBid);
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
        if (btnPay != null) {
            btnPay.setVisible(canPay);
            btnPay.setManaged(canPay);
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
                AlertHelper.show(AlertHelper.Type.SUCCESS, "Thành công", "Đã thiết lập đấu giá tự động!");
                updateAutoBidUI(true, txtAutoMaxBid.getText(), txtAutoIncrement.getText());

            } else if (msg.startsWith("MY_AUTOBID:")) {
                String[] parts = msg.split(":");
                if (parts.length >= 4) {
                    updateAutoBidUI(true, parts[2], parts[3]);
                }

            } else if (msg.startsWith("MY_AUTOBID_NONE")) {
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

    private void closeWindow() {
        if (lblItemName != null && lblItemName.getScene() != null) {
            Stage stage = (Stage) lblItemName.getScene().getWindow();
            stage.close();
        }
    }
}
