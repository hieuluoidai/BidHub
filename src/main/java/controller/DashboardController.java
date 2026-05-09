package controller;

import java.io.IOException;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
    @FXML private TextField textSearch;
    @FXML private ComboBox<String> comboFilter;
    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> colId;
    @FXML private TableColumn<Auction, String> colItemName;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, String> colCategory;
    @FXML private Button bidButton;
    @FXML private Button createSessionButton;
    @FXML private Button btnTopUp;          // nút nạp tiền

    /**
     * Khởi tạo cấu hình bảng, bộ lọc và thiết lập các bộ lắng nghe sự kiện (Listeners).
     */
    public void initialize() {
        // Cấu hình cách hiển thị dữ liệu cho từng cột trong TableView
        colId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAuctionId()));
        colItemName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getItemName()));
        colCurrentPrice.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getCurrentPrice()));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getItem() != null ? data.getValue().getItem().getItemType() : "N/A"
        ));

        // Khởi tạo các giá trị cho bộ lọc danh mục
        comboFilter.setItems(FXCollections.observableArrayList("All", "Electronics", "Art", "Vehicle"));
        comboFilter.setValue("All");

        // Lấy danh sách từ AppState để hiển thị lên Table
        ObservableList<Auction> masterData = AppState.getInstance().getAuctionList();
        FilteredList<Auction> filteredData = new FilteredList<>(masterData, p -> true);

        // Thêm ListChangeListener để FORCE REFRESH TableView mỗi khi
        masterData.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            auctionTable.refresh();
        });

        // Khi tìm kiếm or đổi loại hàng, bảng sẽ tự động update
        textSearch.textProperty().addListener((obs, old, newVal) -> updatePredicate(filteredData));
        comboFilter.valueProperty().addListener((obs, old, newVal) -> updatePredicate(filteredData));

        auctionTable.setItems(filteredData);

        // Kích hoạt các thiết lập bổ trợ cho giao diện
        setupPermissions();
        setupTableEvents();
        setupBidButtonLogic();
        loadAuctionData();
    }

    /**
     * Xác định hiển thị món hàng nào
     */
    private void updatePredicate(FilteredList<Auction> filteredData) {
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
            titleLabel.setText("Welcome, " + user.getUsername() + " (" + user.getClass().getSimpleName() + ")");
            boolean canCreate = (user instanceof Admin || user instanceof Seller);
            createSessionButton.setVisible(canCreate);
            createSessionButton.setManaged(canCreate);
        }
        refreshBalanceLabel();
    }

    /**
     * Cập nhật label hiển thị số dư hiện tại.
     * Gọi sau khi nạp tiền hoặc thanh toán xong để UI khớp với DB.
     */
    public void refreshBalanceLabel() {
        if (lblBalance == null) return;
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        lblBalance.setText(String.format("$%,.2f", user.getBalance()));
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

    /**
     * Nhấn đúp chuột vào 1 dòng để viewDetails
     */
    private void setupTableEvents() {
        auctionTable.setOnMouseClicked(e -> {
           if (e.getClickCount() == 2 && auctionTable.getSelectionModel().getSelectedItem() != null) {
                handleViewDetails();
            }
        });
    }

    /**
     * Điều chỉnh trạng thái nút Bid dựa trên dòng được chọn và quyền hạn người dùng.
     */
    private void setupBidButtonLogic() {
        boolean isBidder = (AppState.getInstance().getCurrentUser() instanceof Bidder);
        auctionTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            boolean active = (newVal != null && isBidder);
            bidButton.setDisable(!active);
            bidButton.setStyle(active ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #CCC;");
        });
    }

    /**
     * Xóa trạng thái đăng nhập và sử dụng SceneManager để quay lại Login.
     */
    @FXML
    void handleLogout() {
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
     * Mở hộp thoại bid phiên đấu giá đang được chọn trong bảng.
     */
    @FXML
    void handleBid() {
        Auction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();
            ((BidController)loader.getController()).setAuctionData(selected);
            showStage(root, "Place Your Bid");
        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Hiển thị màn hình chi tiết sản phẩm.
     */
    @FXML
    void handleViewDetails() {
        Auction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();
            ((ItemDetailsController)loader.getController()).setItemData(selected);
            showStage(root, "Item Details");
        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Tìm và mở file giao diện (FXML) dưới dạng pop-up
     */
    private void openPopup(String fxml, String title) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            showStage(root, title);
        } catch (IOException e) { e.printStackTrace(); }
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
        auctionTable.refresh();
    }
}