package controller;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

import database.AuctionDAO;
import database.ItemDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.auction.Auction;
import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.ItemFactory;
import model.item.Vehicle;
import model.manager.AppState;
import model.user.Admin;
import model.user.User;
import utils.AlertHelper;
import utils.ImageStorageService;

/**
 * Điều khiển màn hình SỬA phiên đấu giá.
 * Cho phép Seller (chủ phiên) hoặc Admin chỉnh sửa thông tin sản phẩm
 * + thời gian kết thúc khi phiên còn ở trạng thái OPEN.
 *
 * Lưu ý quan trọng: KHÔNG cho đổi item_type vì DB lưu Single Table Inheritance,
 * mỗi loại có cột đặc thù riêng (brand vs artist). Đổi type sẽ dẫn đến
 * dữ liệu rác trong các cột khác.
 */
public class EditSessionController {

    @FXML private Label labelHeader;
    @FXML private Label labelItemType;       // Hiển thị type, không cho sửa
    @FXML private TextField textItemName;
    @FXML private TextField textStartingPrice;
    @FXML private TextArea textDescription;
    @FXML private TextField textExtraInfo;
    @FXML private Label labelExtraInfo;
    @FXML private DatePicker datePickerEndDate;
    @FXML private Label labelError;

    // Các control cho ảnh sản phẩm
    @FXML private ImageView imagePreview;
    @FXML private Button    btnChooseImage;
    @FXML private Button    btnRemoveImage;
    @FXML private Label     lblImageStatus;

    private Auction auction;          // Phiên đang được sửa
    private Runnable onSavedCallback; // Callback chạy sau khi save thành công

    /**
     * Trạng thái ảnh — 3 case:
     *   - null + originalImagePath null      → không có ảnh, không đổi
     *   - null + originalImagePath != null   → user muốn XÓA ảnh hiện có
     *   - File != null                       → user chọn ảnh mới (thay thế hoặc thêm mới)
     */
    private File selectedImageFile;
    private String originalImagePath;        // imagePath ban đầu của item
    private boolean userRemovedOriginal;     // true nếu user nhấn Bỏ chọn khi đã có ảnh sẵn

    /**
     * Truyền phiên cần sửa vào controller. Phải gọi NGAY sau khi load FXML.
     */
    public void setAuction(Auction auction) {
        this.auction = auction;
        prefillData();
    }

    /**
     * Đăng ký callback để controller cha (vd ItemDetailsController) đóng cửa sổ
     * và refresh sau khi user save thành công.
     */
    public void setOnSavedCallback(Runnable callback) {
        this.onSavedCallback = callback;
    }

    /**
     * Đổ dữ liệu hiện tại của phiên lên các ô input.
     */
    private void prefillData() {
        if (auction == null) return;
        Item item = auction.getItem();

        labelHeader.setText("Sửa phiên: " + item.getItemName());
        labelItemType.setText(item.getItemType()); // Read-only
        textItemName.setText(item.getItemName());
        textStartingPrice.setText(String.valueOf(item.getStartingPrice()));
        textDescription.setText(item.getDescription());
        datePickerEndDate.setValue(auction.getEndTime().toLocalDate());

        // Hiển thị label + value của trường đặc thù theo type
        if (item instanceof Electronics e) {
            labelExtraInfo.setText("Hãng sản xuất (Brand)");
            textExtraInfo.setText(e.getBrand());
        } else if (item instanceof Art a) {
            labelExtraInfo.setText("Tác giả (Author)");
            textExtraInfo.setText(a.getArtist());
        } else if (item instanceof Vehicle v) {
            labelExtraInfo.setText("Hãng xe (Manufacturer)");
            textExtraInfo.setText(v.getBrand());
        }

        // Load ảnh hiện có (nếu có)
        originalImagePath = item.getImagePath();
        if (originalImagePath != null && !originalImagePath.isBlank()) {
            String uri = ImageStorageService.toFileUri(originalImagePath);
            if (uri != null && imagePreview != null) {
                imagePreview.setImage(new Image(uri, 200, 0, true, true));
                if (lblImageStatus != null) lblImageStatus.setText("Ảnh hiện tại");
                if (btnRemoveImage != null) {
                    btnRemoveImage.setVisible(true);
                    btnRemoveImage.setManaged(true);
                }
            }
        }
    }

    /**
     * Xử lý khi user nhấn Lưu.
     * 1. Validate input
     * 2. Tạo Item mới với dữ liệu đã sửa
     * 3. Update DB qua DAO (kiểm tra quyền sở hữu hoặc admin override)
     * 4. Update end_time nếu user đổi
     * 5. Gửi RELOAD_AUCTION lên server → server broadcast danh sách mới
     */
    @FXML
    void handleSave() {
        try {
            // 1. Validate
            String name      = textItemName.getText().trim();
            String priceStr  = textStartingPrice.getText().trim();
            String desc      = textDescription.getText().trim();
            String extra     = textExtraInfo.getText().trim();

            if (name.isEmpty() || priceStr.isEmpty() || extra.isEmpty()) {
                showError("Vui lòng điền đầy đủ các thông tin bắt buộc!");
                return;
            }
            if (datePickerEndDate.getValue() == null) {
                showError("Vui lòng chọn ngày kết thúc!");
                return;
            }

            double startingPrice;
            try {
                startingPrice = Double.parseDouble(priceStr);
            } catch (NumberFormatException ex) {
                showError("Giá khởi điểm phải là một con số!");
                return;
            }
            if (startingPrice <= 0) {
                showError("Giá khởi điểm phải lớn hơn 0!");
                return;
            }

            LocalDateTime newEndTime = datePickerEndDate.getValue().atTime(23, 59, 59);
            if (!newEndTime.isAfter(auction.getStartTime())) {
                showError("Thời điểm kết thúc phải sau thời điểm bắt đầu!");
                return;
            }

            // 2. Tạo Item mới với cùng ID, cùng type, dữ liệu mới
            String itemId   = auction.getItem().getItemId();
            String itemType = auction.getItem().getItemType();
            Item updatedItem = ItemFactory.createItem(
                    itemType, itemId, name, desc, startingPrice, extra
            );

            // 2b. Xử lý ảnh — 3 case:
            //   A. User chọn ảnh mới → copy vào uploads, set imagePath mới
            //   B. User nhấn "Bỏ chọn" với ảnh sẵn có → xóa file cũ, imagePath = null
            //   C. Không đổi gì → giữ nguyên originalImagePath
            String finalImagePath;
            if (selectedImageFile != null) {
                try {
                    finalImagePath = ImageStorageService.saveImage(selectedImageFile, itemId);
                } catch (IOException | IllegalArgumentException ex) {
                    showError("Lỗi lưu ảnh: " + ex.getMessage());
                    return;
                }
            } else if (userRemovedOriginal && originalImagePath != null) {
                ImageStorageService.deleteImage(originalImagePath);
                finalImagePath = null;
            } else {
                finalImagePath = originalImagePath;
            }
            updatedItem.setImagePath(finalImagePath);

            // 3. Update DB — phân biệt theo role để không cho seller "tampering"
            User currentUser = AppState.getInstance().getCurrentUser();
            ItemDAO itemDao = new ItemDAO();
            boolean itemOk;
            if (currentUser instanceof Admin) {
                itemOk = itemDao.updateAsAdmin(updatedItem);
            } else {
                itemOk = itemDao.update(updatedItem, currentUser.getUserId());
            }

            if (!itemOk) {
                showError("Không thể cập nhật sản phẩm. Có thể bạn không phải chủ phiên này.");
                return;
            }

            // 4. Update end_time của phiên (chỉ áp dụng được khi status = OPEN, AuctionDAO tự kiểm tra)
            new AuctionDAO().updateTime(
                    auction.getAuctionId(),
                    auction.getStartTime(),
                    newEndTime
            );

            // 5. Báo server reload phiên này từ DB và broadcast cho mọi client
            AppState.getInstance().getClient()
                    .send("RELOAD_AUCTION:" + auction.getAuctionId());

            System.out.println(">>> Đã gửi RELOAD_AUCTION cho phiên " + auction.getAuctionId());

            AlertHelper.show(AlertHelper.Type.SUCCESS, "Đã cập nhật phiên đấu giá!");

            if (onSavedCallback != null) onSavedCallback.run();
            closeWindow();

        } catch (IllegalArgumentException e) {
            showError("Lỗi tạo sản phẩm: " + e.getMessage());
        } catch (Exception e) {
            showError("Lỗi không xác định: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void handleCancel() {
        closeWindow();
    }

    /**
     * Mở FileChooser cho user chọn ảnh mới.
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

        try {
            Image img = new Image(file.toURI().toString(), 200, 0, true, true);
            imagePreview.setImage(img);
            selectedImageFile = file;
            userRemovedOriginal = false;     // chọn ảnh mới → reset cờ remove
            if (lblImageStatus != null) {
                lblImageStatus.setText("Sẽ thay bằng: " + file.getName());
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
     * Bỏ chọn ảnh.
     * Nếu item có ảnh gốc → đánh dấu để xóa khi save.
     * Nếu chỉ là ảnh user vừa chọn → bỏ chọn local.
     */
    @FXML
    void handleRemoveImage() {
        if (selectedImageFile != null) {
            // User vừa chọn ảnh mới rồi đổi ý → hủy chọn, có thể vẫn còn ảnh gốc
            selectedImageFile = null;
            if (originalImagePath != null && !originalImagePath.isBlank()) {
                // Quay lại hiển thị ảnh gốc
                String uri = ImageStorageService.toFileUri(originalImagePath);
                if (uri != null) {
                    imagePreview.setImage(new Image(uri, 200, 0, true, true));
                }
                if (lblImageStatus != null) lblImageStatus.setText("Ảnh hiện tại");
            } else {
                imagePreview.setImage(null);
                if (lblImageStatus != null) lblImageStatus.setText("");
                if (btnRemoveImage != null) {
                    btnRemoveImage.setVisible(false);
                    btnRemoveImage.setManaged(false);
                }
            }
        } else {
            // User muốn xóa ảnh gốc đang có
            userRemovedOriginal = true;
            imagePreview.setImage(null);
            if (lblImageStatus != null) {
                lblImageStatus.setText("Ảnh sẽ bị xóa khi lưu");
                lblImageStatus.setTextFill(Color.web("#EF4444"));
            }
            if (btnRemoveImage != null) {
                btnRemoveImage.setVisible(false);
                btnRemoveImage.setManaged(false);
            }
        }
    }

    private void showError(String msg) {
        labelError.setTextFill(Color.RED);
        labelError.setText(msg);
    }

    private void closeWindow() {
        Stage stage = (Stage) textItemName.getScene().getWindow();
        stage.close();
    }
}