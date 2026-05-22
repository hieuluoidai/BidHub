package controller;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javafx.concurrent.Task;

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
import service.AuctionService;
import exception.ValidationException;

/**
 * Điều khiển màn hình SỬA phiên đấu giá.
 * Cho phép Seller (chủ phiên) hoặc Admin chỉnh sửa thông tin sản phẩm
 * + thời gian kết thúc khi phiên còn ở trạng thái OPEN.
 */
public class EditSessionController {

    private final AuctionService auctionService = new AuctionService();

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

    private File selectedImageFile;
    private String originalImagePath;        // imagePath ban đầu của item
    private boolean userRemovedOriginal;     // true nếu user nhấn Bỏ chọn khi đã có ảnh sẵn

    public void setAuction(Auction auction) {
        this.auction = auction;
        prefillData();
    }

    public void setOnSavedCallback(Runnable callback) {
        this.onSavedCallback = callback;
    }

    private void prefillData() {
        if (auction == null) return;
        Item item = auction.getItem();

        labelHeader.setText("Sửa phiên: " + item.getItemName());
        labelItemType.setText(item.getItemType()); 
        textItemName.setText(item.getItemName());
        textStartingPrice.setText(String.valueOf(item.getStartingPrice()));
        textDescription.setText(item.getDescription());
        datePickerEndDate.setValue(auction.getEndTime().toLocalDate());

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

        originalImagePath = item.getImagePath();
        if (originalImagePath != null && !originalImagePath.isBlank()) {
            String uri = ImageStorageService.toImageUrl(originalImagePath);
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

    @FXML
    void handleSave() {
        try {
            String name      = textItemName.getText().trim();
            String priceStr  = textStartingPrice.getText().trim();
            String desc      = textDescription.getText().trim();
            String extra     = textExtraInfo.getText().trim();
            LocalDate endDate = datePickerEndDate.getValue();

            // Validation qua Service
            auctionService.validateAuctionEdit(name, priceStr, extra, endDate, auction.getStartTime());

            double startingPrice = Double.parseDouble(priceStr);
            LocalDateTime newEndTime = endDate.atTime(23, 59, 59);

            String itemId   = auction.getItem().getItemId();
            String itemType = auction.getItem().getItemType();
            Item updatedItem = ItemFactory.createItem(itemType, itemId, name, desc, startingPrice, extra);

            if (selectedImageFile != null) {
                btnChooseImage.setDisable(true);
                if (lblImageStatus != null) {
                    lblImageStatus.setText("Đang tải ảnh lên server...");
                }
                File fileToUpload = selectedImageFile;
                Item finalUpdatedItem = updatedItem;
                LocalDateTime finalEndTime = newEndTime;
                Task<String> uploadTask = new Task<>() {
                    @Override
                    protected String call() throws Exception {
                        return ImageStorageService.uploadToServer(fileToUpload, itemId);
                    }
                };
                uploadTask.setOnSucceeded(e -> {
                    finalUpdatedItem.setImagePath(uploadTask.getValue());
                    finishEdit(finalUpdatedItem, finalEndTime);
                });
                uploadTask.setOnFailed(e -> {
                    btnChooseImage.setDisable(false);
                    showError("Lỗi tải ảnh: " + uploadTask.getException().getMessage());
                });
                new Thread(uploadTask).start();
            } else {
                String finalImagePath = userRemovedOriginal ? null : originalImagePath;
                updatedItem.setImagePath(finalImagePath);
                finishEdit(updatedItem, newEndTime);
            }

        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            showError("Lỗi không xác định: " + e.getMessage());
        }
    }

    private void finishEdit(Item updatedItem, LocalDateTime newEndTime) {
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
        new AuctionDAO().updateTime(auction.getAuctionId(), auction.getStartTime(), newEndTime);
        AppState.getInstance().getClient().send("RELOAD_AUCTION:" + auction.getAuctionId());
        AlertHelper.show(AlertHelper.Type.SUCCESS, "Đã cập nhật phiên đấu giá!");
        if (onSavedCallback != null) onSavedCallback.run();
        closeWindow();
    }

    @FXML
    void handleCancel() {
        closeWindow();
    }

    @FXML
    void handleChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Ảnh (JPG, PNG, GIF)", "*.jpg", "*.jpeg", "*.png", "*.gif"));
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
            userRemovedOriginal = false;
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

    @FXML
    void handleRemoveImage() {
        if (selectedImageFile != null) {
            selectedImageFile = null;
            if (originalImagePath != null && !originalImagePath.isBlank()) {
                String uri = ImageStorageService.toImageUrl(originalImagePath);
                if (uri != null) imagePreview.setImage(new Image(uri, 200, 0, true, true));
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
