package controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.auction.Auction;
import model.manager.AuctionManager;
import model.manager.SessionManager;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import utils.SceneSwitcher;
import java.io.IOException;

public class DashboardController {

    @FXML private Label titleLabel;
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

    private ObservableList<Auction> masterData = FXCollections.observableArrayList();

    // Hàm khởi tạo: Thiết lập cấu hình bảng, nạp dữ liệu và kích hoạt các bộ lọc ban đầu
    public void initialize() {
        colId.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAuctionId()));
        colItemName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getItem().getItemName()));
        colCurrentPrice.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getCurrentPrice()));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().toString()));
        colCategory.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getItem().getItemType()));

        comboFilter.setItems(FXCollections.observableArrayList("All", "Electronics", "Art", "Vehicle"));
        comboFilter.setValue("All");

        loadAuctionData();

        FilteredList<Auction> filteredData = new FilteredList<>(masterData, p -> true);
        autoFilter(filteredData);
        auctionTable.setItems(filteredData);

        setupPermissions();
        setupTableEvents();
        setupBidButtonLogic();
    }

    // Thiết lập bộ lắng nghe: Tự động chạy lọc khi nội dung ô tìm kiếm hoặc ComboBox thay đổi
    private void autoFilter(FilteredList<Auction> filteredData) {
        textSearch.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(filteredData));
        comboFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter(filteredData));
    }

    // Logic lọc: Kiểm tra từng sản phẩm theo từ khóa tìm kiếm và thể loại được chọn
    private void applyFilter(FilteredList<Auction> filteredData) {
        filteredData.setPredicate(auction -> {
            String searchText = textSearch.getText() == null ? "" : textSearch.getText().toLowerCase();
            String filterType = comboFilter.getValue();

            boolean matchesCategory = (filterType == null || filterType.equals("All") ||
                    auction.getItem().getItemType().equalsIgnoreCase(filterType));
            boolean matchesSearch = auction.getItem().getItemName().toLowerCase().contains(searchText);

            return matchesCategory && matchesSearch;
        });
    }

    // Làm mới dữ liệu: Lấy danh sách phiên đấu giá mới nhất từ Manager đổ vào danh sách gốc
    @FXML
    private void loadAuctionData() {
        masterData.setAll(AuctionManager.getInstance().getAllAuctions());
    }

    // Phân quyền: Kiểm tra vai trò người dùng để ẩn hoặc hiện nút tạo phiên đấu giá
    private void setupPermissions() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            titleLabel.setText("Welcome, " + currentUser.getUsername() + " (" + currentUser.getClass().getSimpleName() + ")");
            boolean canCreate = (currentUser instanceof Admin || currentUser instanceof Seller);
            createSessionButton.setVisible(canCreate);
            createSessionButton.setManaged(canCreate);
        }
    }

    // Sự kiện bảng: Xử lý khi người dùng nháy đúp chuột vào một dòng để xem chi tiết
    private void setupTableEvents() {
        auctionTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && auctionTable.getSelectionModel().getSelectedItem() != null) {
                handleViewDetails(null);
            }
        });
    }

    // Logic nút Bid: Khóa/mở và đổi màu nút dựa trên việc người dùng có chọn dòng hay không
    private void setupBidButtonLogic() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        boolean isBidder = (currentUser instanceof Bidder);

        bidButton.setDisable(true);
        bidButton.setStyle("-fx-background-color: #CCCCCC; -fx-text-fill: #666666; -fx-font-weight: bold;");

        auctionTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && isBidder) {
                bidButton.setDisable(false);
                bidButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
            } else {
                bidButton.setDisable(true);
                bidButton.setStyle("-fx-background-color: #CCCCCC; -fx-text-fill: #666666; -fx-font-weight: bold;");
            }
        });
    }

    // Đăng xuất: Xóa thông tin phiên đăng nhập và chuyển hướng về màn hình Login
    @FXML
    void handleLogout(ActionEvent event) {
        SessionManager.getInstance().logout();
        SceneSwitcher.switchScene(event, "/view/login.fxml");
    }

    // Tạo phiên mới: Mở cửa sổ popup để nhập thông tin sản phẩm đấu giá mới
    @FXML
    void handleCreateNewSession(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/create_session.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Create New Auction Session");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            loadAuctionData();
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Đặt giá: Mở popup cho phép người dùng nhập giá muốn đấu thầu cho sản phẩm đã chọn
    @FXML
    void handleBid(ActionEvent event) {
        Auction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bid_dialog.fxml"));
            Parent root = loader.load();
            ((BidController)loader.getController()).setAuctionData(selected);
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            auctionTable.refresh();
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Chi tiết: Hiển thị thông tin đầy đủ về món hàng và thời gian đấu giá còn lại
    @FXML
    void handleViewDetails(ActionEvent event) {
        Auction selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_details.fxml"));
            Parent root = loader.load();
            ((ItemDetailsController)loader.getController()).setItemData(selected);
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            auctionTable.refresh();
        } catch (IOException e) { e.printStackTrace(); }
    }
}