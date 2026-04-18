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
import javafx.scene.control.cell.PropertyValueFactory;
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
import model.manager.AppState;

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


    // Hàm khởi tạo: Thiết lập cấu hình bảng, nạp dữ liệu và kích hoạt các bộ lọc ban đầu
    public void initialize() {
        // 1. Thiết lập các cột bằng Lambda để đảm bảo tính ổn định và chính xác cao nhất
        colId.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getAuctionId()));
        colItemName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getItemName()));
        
        // Cột giá: Dùng SimpleObjectProperty cho Double
        colCurrentPrice.setCellValueFactory(cellData -> 
            new SimpleObjectProperty<>(cellData.getValue().getCurrentPrice()));
            
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        
        colCategory.setCellValueFactory(cellData -> {
            if (cellData.getValue().getItem() != null) {
                return new SimpleStringProperty(cellData.getValue().getItem().getItemType());
            }
            return new SimpleStringProperty("N/A");
        });

        // 2. Thiết lập ComboBox lọc
        comboFilter.setItems(FXCollections.observableArrayList("All", "Electronics", "Art", "Vehicle"));
        comboFilter.setValue("All");

        // 3. Kết nối dữ liệu: Sử dụng danh sách tập trung từ AppState
        ObservableList<Auction> masterData = AppState.getInstance().getAuctionList();
        FilteredList<Auction> filteredData = new FilteredList<>(masterData, p -> true);
        
        // Kích hoạt bộ lắng nghe lọc
        autoFilter(filteredData);
        
        // Gán dữ liệu vào bảng
        auctionTable.setItems(filteredData);

        // 4. Các thiết lập phụ trợ
        setupPermissions();
        setupTableEvents();
        setupBidButtonLogic();
        
        // 5. QUAN TRỌNG: Yêu cầu nạp dữ liệu ngay khi mở Dashboard
        loadAuctionData();
    }

    private void applyFilter(FilteredList<Auction> filteredData) {
        filteredData.setPredicate(auction -> {
            // Nếu không có dữ liệu hoặc item rỗng thì ẩn (tránh lỗi NullPointer)
            if (auction == null || auction.getItem() == null) return false;

            String searchText = (textSearch.getText() == null) ? "" : textSearch.getText().toLowerCase().trim();
            String filterType = (comboFilter.getValue() == null) ? "All" : comboFilter.getValue();

            // Kiểm tra Category
            boolean matchesCategory = filterType.equals("All") || 
                                     auction.getItem().getItemType().equalsIgnoreCase(filterType);
            
            // Kiểm tra Tên (Hỗ trợ tìm kiếm không dấu nếu cần, hiện tại là chứa chuỗi)
            boolean matchesSearch = auction.getItem().getItemName().toLowerCase().contains(searchText);

            return matchesCategory && matchesSearch;
        });
    }
    
    private void autoFilter(FilteredList<Auction> filteredData) {
        textSearch.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(filteredData));
        comboFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter(filteredData));
    }

    // Làm mới dữ liệu: Lấy danh sách phiên đấu giá mới nhất từ Manager đổ vào danh sách gốc
    @FXML
    private void loadAuctionData() {
        AppState.getInstance().getClient().send("REFRESH_DATA");
    }

    // Phân quyền: Kiểm tra vai trò người dùng để ẩn hoặc hiện nút tạo phiên đấu giá
    private void setupPermissions() {
        User currentUser = AppState.getInstance().getCurrentUser(); // Dùng AppState
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
    		User currentUser = AppState.getInstance().getCurrentUser();
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
        AppState.getInstance().setCurrentUser(null); // Xóa user hiện tại
        AppState.getInstance().getSceneManager().showLogin(); // Dùng SceneManager của Hiếu
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