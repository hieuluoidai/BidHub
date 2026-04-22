package controller;

import java.time.LocalDateTime;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.manager.AppState;

/**
 * Điều khiển màn hình tạo phiên đấu giá mới.
 * Giúp seller nhập thông tin sản phẩm và kích hoạt phiên đấu giá.
 */
public class CreateSessionController {

    @FXML private TextField textItemName;
    @FXML private TextField textStartingPrice;
    @FXML private TextField textDescription;
    @FXML private TextField textExtraInfo;
    @FXML private Label labelError;
    @FXML private ComboBox<String> cbItemType;
    @FXML private Label labelExtraInfo;
    @FXML private Label labelDescription;
    @FXML private Label labelStartingPrice;
    @FXML private Label labelItemType;
    @FXML private Label labelItemName;

    /**
     * Đổ dữ liệu vào ComboBox và thiết lập tính năng thay đổi Label.
     */
    @FXML
    public void initialize() {
        // Nạp danh sách các loại hàng hóa
        cbItemType.getItems().addAll("Electronics", "Art", "Vehicle");

        // Listener sự kiện chọn loại hàng.
        cbItemType.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            switch (newVal) {
                case "Electronics":
                		labelExtraInfo.setText("Hãng sản xuất (Brand)");
                    textExtraInfo.setPromptText("Ví dụ: Apple, Samsung...");
                    break;
                case "Art":
                    labelExtraInfo.setText("Tác giả (Author)");
                    textExtraInfo.setPromptText("Ví dụ: Picasso, Van Gogh...");
                    break;
                case "Vehicle":
                    labelExtraInfo.setText("Hãng xe (Manufacturer)");
                    textExtraInfo.setPromptText("Ví dụ: Toyota, Honda...");
                    break;
                default:
                    labelExtraInfo.setText("Thông tin thêm");
            }
        });
    }

    /**
     * Xử lý khi Lưu: Thu thập dữ liệu, tạo phiên và gửi đi khắp hệ thống.
     */
    @FXML
    void handleSave() {
        try {
            // 1. Thu thập dữ liệu từ các ô nhập thông tin
            String type = cbItemType.getValue();
            String name = textItemName.getText();
            String priceStr = textStartingPrice.getText();
            String desc = textDescription.getText();
            String extraInfo = textExtraInfo.getText();

            // 2. Kiểm tra tính hợp lệ (Validation)
            if (type == null) {
                showError("Vui lòng chọn loại sản phẩm!");
                return;
            }

            if (name.isEmpty() || priceStr.isEmpty() || extraInfo.isEmpty()) {
                showError("Vui lòng điền đầy đủ các thông tin bắt buộc!");
                return;
            }

            double startingPrice = Double.parseDouble(priceStr);
            if (startingPrice <= 0) {
                showError("Giá khởi điểm phải lớn hơn 0!");
                return;
            }

            // 3. Tạo đối tượng (Factory Pattern)
            // Tạo ID duy nhất dựa trên thời gian hiện tại để tránh trùng lặp
            String itemId = "ITEM_" + System.currentTimeMillis();
            Item newItem = ItemFactory.createItem(type, itemId, name, desc, startingPrice, extraInfo);

            // 4. Thiết lập phiên đấu giá sản phẩm này
            String auctionId = "AUC_" + System.currentTimeMillis();
            LocalDateTime startTime = LocalDateTime.now().plusSeconds(15); // Bắt đầu sau 15 giây kể từ lúc tạo phiên
            LocalDateTime endTime = startTime.plusDays(7); // Kéo dài trong 1 tuần

            Auction newAuction = new Auction(auctionId, newItem, startTime, endTime);
            String sellerId = AppState.getInstance().getCurrentUser().getUserId();

            // 5. Lưu trữ
            // Lưu vào MySQL thông qua các lớp DAO.
            new database.ItemDAO().save(newItem, sellerId);
            new database.AuctionDAO().save(newAuction);

            // Gửi đối tượng mới tạo lên Server để Server báo cho tất cả các Client khác.
            AppState.getInstance().getClient().send(newAuction);

            System.out.println(">>> Đã tạo và gửi phiên đấu giá thành công: " + auctionId);
            closeWindow();

        } catch (NumberFormatException e) {
            showError("Lỗi: Giá tiền phải là một con số hợp lệ!");
        } catch (IllegalArgumentException e) {
            showError("Lỗi: Không nhận diện được loại sản phẩm!");
        }
    }

    /**
     * Xử lý khi nhấn nút "Hủy": Đóng cửa sổ mà không làm gì cả.
     */
    @FXML
    void handleCancel() {
        closeWindow();
    }

    /**
     * Hiện thông báo lỗi màu đỏ.
     */
    private void showError(String msg) {
        labelError.setTextFill(Color.RED);
        labelError.setText(msg);
    }

    /**
     * Tìm và đóng cửa sổ hiện tại.
     */
    private void closeWindow() {
        // Lấy Stage thông qua ô nhập tên item.
        Stage stage = (Stage) textItemName.getScene().getWindow();
        stage.close();
    }
}