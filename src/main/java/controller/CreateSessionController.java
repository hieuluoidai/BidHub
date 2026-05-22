package controller;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import javafx.concurrent.Task;
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
import service.AuctionService;
import exception.ValidationException;

/**
 * Điều khiển màn hình tạo phiên đấu giá mới.
 * Giúp seller nhập thông tin sản phẩm và kích hoạt phiên đấu giá.
 */
public class CreateSessionController {

    private final AuctionService auctionService = new AuctionService();

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
        for (int h = 0; h < 24; h++) {
            cbEndHour.getItems().add(String.format("%02d", h));
        }
        for (int m = 0; m < 60; m += 5) {
            cbEndMinute.getItems().add(String.format("%02d", m));
        }

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
            int startDelaySeconds = parseStartDelay();

            // 2. Validation qua Service
            auctionService.validateAuctionCreation(type, name, priceStr, extraInfo, 
                                                  endDate, endHourStr, endMinStr, startDelaySeconds);

            double startingPrice = Double.parseDouble(priceStr);
            LocalDateTime startTime = LocalDateTime.now().plusSeconds(startDelaySeconds);
            int endHour = Integer.parseInt(endHourStr);
            int endMin  = Integer.parseInt(endMinStr);
            LocalDateTime endTime   = LocalDateTime.of(endDate, LocalTime.of(endHour, endMin));

            // 4. Tạo Item & Auction (Factory Pattern)
            String itemId = "ITEM_" + System.currentTimeMillis();
            Item newItem = ItemFactory.createItem(type, itemId, name, desc, startingPrice, extraInfo);

            String auctionId = "AUC_" + System.currentTimeMillis();
            Auction newAuction = new Auction(auctionId, newItem, startTime, endTime);
            String sellerId = AppState.getInstance().getCurrentUser().getUserId();
            newAuction.setSellerId(sellerId);

            // 4b. Upload ảnh lên server rồi mới lưu DB & broadcast
            if (selectedImageFile != null) {
                btnChooseImage.setDisable(true);
                if (lblImageStatus != null) {
                    lblImageStatus.setText("Đang tải ảnh lên server...");
                }
                File fileToUpload = selectedImageFile;
                Task<String> uploadTask = new Task<>() {
                    @Override
                    protected String call() throws Exception {
                        return ImageStorageService.uploadToServer(fileToUpload, itemId);
                    }
                };
                uploadTask.setOnSucceeded(e -> {
                    newItem.setImagePath(uploadTask.getValue());
                    finishCreation(newItem, newAuction, sellerId);
                });
                uploadTask.setOnFailed(e -> {
                    btnChooseImage.setDisable(false);
                    showError("Lỗi tải ảnh: " + uploadTask.getException().getMessage());
                });
                new Thread(uploadTask).start();
            } else {
                finishCreation(newItem, newAuction, sellerId);
            }

        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Lỗi: Giá tiền phải là một con số hợp lệ!");
        } catch (IllegalArgumentException e) {
            showError("Lỗi: Không nhận diện được loại sản phẩm!");
        }
    }

    private void finishCreation(Item item, Auction auction, String sellerId) {
        new database.ItemDAO().save(item, sellerId);
        new database.AuctionDAO().save(auction);
        AppState.getInstance().getClient().send(auction);
        utils.SessionPermission.invalidateCache();
        System.out.println(">>> Đã tạo phiên: " + auction.getAuctionId()
                + " | kết thúc: " + auction.getEndTime()
                + " | ảnh: " + (item.getImagePath() != null ? item.getImagePath() : "(không có)"));
        closeWindow();
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
