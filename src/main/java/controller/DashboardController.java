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
    @FXML private Label lblLockedBalance;  // hiển thị số dư bị khóa
    @FXML private TextField textSearch;
    @FXML private ComboBox<String> comboFilter;
    @FXML private javafx.scene.layout.FlowPane flowPaneAuctions;
    
    @FXML private Button createSessionButton;
    @FXML private Button btnTopUp;          // nút nạp tiền
    @FXML private Label sidebarUserLabel;
    @FXML private Label lblSidebarAvatar;
    @FXML private Label lblSidebarUsername;
    @FXML private Label lblSidebarRole;
    @FXML private javafx.scene.layout.VBox paneUserProfile;

    private FilteredList<Auction> filteredData;

    /**
     * Khởi tạo bộ lọc và thiết lập các bộ lắng nghe sự kiện (Listeners).
     */
    public void initialize() {
        // Khởi tạo các giá trị cho bộ lọc danh mục
        comboFilter.setItems(FXCollections.observableArrayList("All", "Electronics", "Art", "Vehicle"));
        comboFilter.setValue("All");

        // Lấy danh sách từ AppState để hiển thị
        ObservableList<Auction> masterData = AppState.getInstance().getAuctionList();
        filteredData = new FilteredList<>(masterData, p -> true);

        // Thêm ListChangeListener để cập nhật giao diện thông minh
        masterData.addListener((javafx.collections.ListChangeListener<Auction>) c -> {
            javafx.application.Platform.runLater(() -> {
                while (c.next()) {
                    if (c.wasUpdated() || c.wasReplaced()) {
                        // Cập nhật từng thẻ bị thay đổi thay vì vẽ lại toàn bộ
                        for (int i = c.getFrom(); i < c.getTo(); i++) {
                            updateSpecificCard(masterData.get(i));
                        }
                    } else {
                        // Nếu là thêm/xóa/reset thì vẽ lại toàn bộ cho chắc chắn
                        renderAuctions();
                    }
                }
                updateOpenDetailWindow();
            });
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
                    } catch (Exception ignore) {}
                }
            }
        });

        // Kích hoạt các thiết lập bổ trợ cho giao diện
        setupPermissions();
        loadAuctionData();
        
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

    /**
     * Cập nhật label hiển thị số dư khả dụng và số dư bị khóa.
     * Gọi sau khi nạp tiền, bid hoặc thanh toán xong để UI khớp với DB.
     */
    public void refreshBalanceLabel() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        
        if (lblBalance != null) {
            lblBalance.setText(String.format("$%,.2f", user.getBalance()));
        }
        if (lblLockedBalance != null) {
            lblLockedBalance.setText(String.format("$%,.2f", user.getLockedBalance()));
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

            currentDetailStage.setOnHidden(e -> {
                currentDetailController = null;
                currentDetailStage = null;
            });

            currentDetailStage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Mở dialog xem thông tin cá nhân của chính mình.
     */
    @FXML
    void handleViewProfile() {
        try {
            System.out.println(">>> Attempting to open profile for user: " + AppState.getInstance().getCurrentUser().getUsername());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_details.fxml"));
            Parent root = loader.load();
            
            UserDetailsController controller = loader.getController();
            controller.setUserData(AppState.getInstance().getCurrentUser());
            
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
        renderAuctions();
    }
}