package controller;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.manager.AppState;
import utils.ImageStorageService;

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

    // Các control để chọn ngày + giờ kết thúc
    @FXML private DatePicker datePickerEndDate;
    @FXML private ComboBox<String> cbEndHour;
    @FXML private ComboBox<String> cbEndMinute;
    @FXML private TextField textStartDelay;       // Số giây đợi trước khi phiên RUNNING

    // Các control cho ảnh sản phẩm
    @FXML private ImageView imagePreview;
    @FXML private Button    btnChooseImage;
    @FXML private Button    btnRemoveImage;
    @FXML private Label     lblImageStatus;

    /** File ảnh user đã chọn (chưa copy vào uploads). Null nếu chưa chọn. */
    private File selectedImageFile;

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

        // Nạp giờ (00–23) và phút (00, 05, 10, ..., 55) dạng String 2 chữ số
        for (int h = 0; h < 24; h++) cbEndHour.getItems().add(String.format("%02d", h));
        for (int m = 0; m < 60; m += 5) cbEndMinute.getItems().add(String.format("%02d", m));

        // Mặc định: 7 ngày sau, lúc 23:55
        datePickerEndDate.setValue(LocalDate.now().plusDays(7));
        cbEndHour.setValue("23");
        cbEndMinute.setValue("55");
    }

    /**
     * Xử lý khi Lưu: Thu thập dữ liệu, tạo phiên và gửi đi khắp hệ thống.
     */
    @FXML
    void handleSave() {
        try {
            // 1. Thu thập dữ liệu
            String type = cbItemType.getValue();
            String name = textItemName.getText();
            String priceStr = textStartingPrice.getText();
            String desc = textDescription.getText();
            String extraInfo = textExtraInfo.getText();
            LocalDate endDate = datePickerEndDate.getValue();
            String endHourStr = cbEndHour.getValue();
            String endMinStr  = cbEndMinute.getValue();

            // 2. Validation
            if (type == null) { showError("Vui lòng chọn loại sản phẩm!"); return; }
            if (name.isEmpty() || priceStr.isEmpty() || extraInfo.isEmpty()) {
                showError("Vui lòng điền đầy đủ các thông tin bắt buộc!");
                return;
            }
            if (endDate == null || endHourStr == null || endMinStr == null) {
                showError("Vui lòng chọn đầy đủ ngày, giờ và phút kết thúc!");
                return;
            }

            double startingPrice = Double.parseDouble(priceStr);
            if (startingPrice <= 0) { showError("Giá khởi điểm phải lớn hơn 0!"); return; }

            // 3. Ghép ngày + giờ + phút thành LocalDateTime
            int endHour = Integer.parseInt(endHourStr);
            int endMin  = Integer.parseInt(endMinStr);
            LocalDateTime endTime   = LocalDateTime.of(endDate, LocalTime.of(endHour, endMin));

            // Thời gian đợi trước khi phiên RUNNING — user tự nhập (mặc định 15s)
            int startDelaySeconds = parseStartDelay();
            if (startDelaySeconds < 0) {
                showError("Thời gian bắt đầu phải là số nguyên dương (giây)!");
                return;
            }
            LocalDateTime startTime = LocalDateTime.now().plusSeconds(startDelaySeconds);

            if (!endTime.isAfter(startTime)) {
                showError("Thời điểm kết thúc phải sau thời điểm bắt đầu ("
                        + startDelaySeconds + "s từ bây giờ)!");
                return;
            }

            // 4. Tạo Item & Auction (Factory Pattern)
            String itemId = "ITEM_" + System.currentTimeMillis();
            Item newItem = ItemFactory.createItem(type, itemId, name, desc, startingPrice, extraInfo);

            // 4b. Nếu user có chọn ảnh, copy vào uploads/ và set imagePath
            if (selectedImageFile != null) {
                try {
                    String imagePath = ImageStorageService.saveImage(selectedImageFile, itemId);
                    newItem.setImagePath(imagePath);
                } catch (IOException | IllegalArgumentException ex) {
                    showError("Lỗi lưu ảnh: " + ex.getMessage());
                    return;
                }
            }

            String auctionId = "AUC_" + System.currentTimeMillis();
            Auction newAuction = new Auction(auctionId, newItem, startTime, endTime);
            String sellerId = AppState.getInstance().getCurrentUser().getUserId();

            // 5. Lưu DB
            new database.ItemDAO().save(newItem, sellerId);
            new database.AuctionDAO().save(newAuction);

            // 6. Broadcast cho mọi client
            AppState.getInstance().getClient().send(newAuction);

            // Phiên mới được tạo → cache permission cũ đã không còn đúng
            utils.SessionPermission.invalidateCache();

            System.out.println(">>> Đã tạo phiên: " + auctionId
                    + " | kết thúc: " + endTime
                    + " | ảnh: " + (newItem.getImagePath() != null ? newItem.getImagePath() : "(không có)"));
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
     * Đọc & validate trường "Bắt đầu sau (giây)" — mặc định 15s nếu để trống.
     * Giới hạn 1–86400 giây (1 ngày) để tránh giá trị vô lý.
     *
     * @return số giây ≥ 0, hoặc -1 nếu input không hợp lệ
     */
    private int parseStartDelay() {
        if (textStartDelay == null) return 15;   // FXML cũ chưa có field → giữ default 15s

        String raw = textStartDelay.getText() == null ? "" : textStartDelay.getText().trim();
        if (raw.isEmpty()) return 15;            // Để trống → mặc định 15s

        try {
            int sec = Integer.parseInt(raw);
            if (sec < 1 || sec > 86400) return -1;
            return sec;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Tìm và đóng cửa sổ hiện tại.
     */
    /**
     * Mở FileChooser cho user chọn ảnh, sau đó hiển thị preview.
     */
    @FXML
    void handleChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ảnh (JPG, PNG, GIF)",
                        "*.jpg", "*.jpeg", "*.png", "*.gif"));

        Stage owner = (Stage) textItemName.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        if (!ImageStorageService.isValidImageExtension(file.getName())) {
            showError("Định dạng không hợp lệ. Chỉ chấp nhận JPG, PNG, GIF.");
            return;
        }

        // Preview — load trực tiếp từ file, chưa copy
        try {
            Image img = new Image(file.toURI().toString(), 200, 0, true, true);
            imagePreview.setImage(img);
            selectedImageFile = file;
            if (lblImageStatus != null) {
                lblImageStatus.setText("Đã chọn: " + file.getName());
                lblImageStatus.setTextFill(Color.web("#10B981"));
            }
            if (btnRemoveImage != null) {
                btnRemoveImage.setVisible(true);
                btnRemoveImage.setManaged(true);
            }
        } catch (Exception e) {
            showError("Không đọc được ảnh: " + e.getMessage());
        }
    }

    /**
     * Bỏ chọn ảnh — về trạng thái không có ảnh.
     */
    @FXML
    void handleRemoveImage() {
        selectedImageFile = null;
        imagePreview.setImage(null);
        if (lblImageStatus != null) {
            lblImageStatus.setText("");
        }
        if (btnRemoveImage != null) {
            btnRemoveImage.setVisible(false);
            btnRemoveImage.setManaged(false);
        }
    }

    private void closeWindow() {
        // Lấy Stage thông qua ô nhập tên item.
        Stage stage = (Stage) textItemName.getScene().getWindow();
        stage.close();
    }
}