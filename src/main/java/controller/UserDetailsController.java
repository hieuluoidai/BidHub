package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.manager.AppState;
import model.user.User;
import utils.ImageStorageService;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

public class UserDetailsController {

    @FXML private Label lblUsername;
    @FXML private Label lblEmail;
    @FXML private Label lblRoleBadge;
    @FXML private Label lblBalance;
    @FXML private Label lblLockedBalance;
    @FXML private Label lblUserId;
    @FXML private Label lblPhone;
    @FXML private Label lblDOB;
    @FXML private Label lblAvatar;
    @FXML private ImageView imgAvatar;
    
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private DatePicker dpDOB;
    
    @FXML private Button btnEditProfile;
    @FXML private Button btnChangePassword;
    @FXML private Button btnApproveSeller;
    @FXML private Button btnRevokeSeller;
    @FXML private Label lblPendingStatusLabel;
    @FXML private Label lblPendingStatus;
    
    @FXML private Label lblStat1Value;
    @FXML private Label lblStat1Label;
    @FXML private Label lblStat2Value;
    @FXML private Label lblStat2Label;
    @FXML private Label lblStat3Value;
    @FXML private Label lblStat3Label;

    @FXML private javafx.scene.shape.Circle avatarClip;
    @FXML private javafx.scene.layout.StackPane cameraOverlay;
    @FXML private javafx.scene.layout.StackPane avatarContainer;

    private User user;
    private boolean isEditing = false;
    private Runnable onAvatarChanged;

    public void setOnAvatarChanged(Runnable callback) {
        this.onAvatarChanged = callback;
    }

    public void setUserData(User user) {
        this.user = user;
        lblUsername.setText(user.getUsername());
        lblEmail.setText(user.getEmail());
        lblRoleBadge.setText(user.getClass().getSimpleName().toUpperCase());
        lblUserId.setText(user.getUserId());
        
        lblPhone.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "Chưa cập nhật");
        if (user.getDateOfBirth() != null) {
            lblDOB.setText(user.getDateOfBirth().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        } else {
            lblDOB.setText("Chưa cập nhật");
        }
        
        // Cập nhật trạng thái duyệt Seller
        boolean isAdmin = AppState.getInstance().getCurrentUser() instanceof model.user.Admin;
        boolean isPending = user.isPendingSeller();
        
        if (lblPendingStatus != null) {
            lblPendingStatus.setVisible(isPending);
            lblPendingStatusLabel.setVisible(isPending);
        }
        
        if (btnApproveSeller != null) {
            boolean canApprove = isAdmin && isPending;
            btnApproveSeller.setVisible(canApprove);
            btnApproveSeller.setManaged(canApprove);
        }

        // Nút "Hủy quyền Seller": chỉ hiện khi Admin xem 1 user đang là Seller
        // (không cho phép tự hủy của chính mình; Admin không có vai trò Seller).
        if (btnRevokeSeller != null) {
            boolean isSelf = AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId());
            boolean canRevoke = isAdmin && !isSelf && (user instanceof model.user.Seller);
            btnRevokeSeller.setVisible(canRevoke);
            btnRevokeSeller.setManaged(canRevoke);
        }

        setupAvatarEffects();
        refreshBalanceLabels();
        refreshAvatar();
        loadStats();
    }

    @FXML
    void handleApproveSeller() {
        if (user == null) return;

        boolean confirm = utils.AlertHelper.showConfirm(
            "Xác nhận phê duyệt",
            "Bạn có chắc chắn muốn phê duyệt người dùng này làm Seller không? "
                    + "Người dùng sẽ có quyền đăng bán sản phẩm ngay lập tức."
        );

        if (confirm) {
            String cmd = "APPROVE_SELLER:" + user.getUserId();
            AppState.getInstance().getClient().send(cmd);
            
            // Tạm thời giả định thành công hoặc chờ message SELLER_APPROVED từ server nếu server broadcast
            // Tuy nhiên ở đây UserDetailsController thường dùng cho 1 user cụ thể, 
            // ta có thể cập nhật UI local luôn.
            user.setPendingSeller(false);
            // Lưu ý: class của 'user' vẫn là Bidder trong memory client cho đến khi reload
            // Nhưng ta có thể ẩn nút đi.
            btnApproveSeller.setVisible(false);
            lblPendingStatus.setVisible(false);
            lblPendingStatusLabel.setVisible(false);
            lblRoleBadge.setText("SELLER"); // Mock UI update
        }
    }

    @FXML
    void handleRevokeSeller() {
        if (user == null) return;

        boolean confirm = utils.AlertHelper.showConfirm(
            "Xác nhận hủy quyền Seller",
            "Bạn có chắc chắn muốn hủy quyền Seller của \"" + user.getUsername() + "\"?\n\n"
                + "• Tài khoản sẽ chuyển về vai trò BIDDER.\n"
                + "• Các phiên đấu giá / sản phẩm đã đăng trước đó vẫn được giữ nguyên.\n"
                + "• Người dùng sẽ phải gửi lại yêu cầu nâng cấp nếu muốn trở thành Seller."
        );

        if (!confirm) return;

        String cmd = "REVOKE_SELLER:" + user.getUserId();
        AppState.getInstance().getClient().send(cmd);

        // Cập nhật UI tạm thời — bảng admin sẽ được refresh khi nhận USERS_UPDATED
        btnRevokeSeller.setVisible(false);
        btnRevokeSeller.setManaged(false);
        lblRoleBadge.setText("BIDDER");
    }

    private void setupAvatarEffects() {
        if (imgAvatar != null && avatarClip != null) {
            imgAvatar.setClip(avatarClip);
        }
        
        if (avatarContainer != null && cameraOverlay != null) {
            boolean isOwnProfile = AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId());
            
            avatarContainer.setOnMouseEntered(e -> {
                if (isOwnProfile && !isEditing) cameraOverlay.setVisible(true);
            });
            avatarContainer.setOnMouseExited(e -> {
                cameraOverlay.setVisible(false);
            });
        }
        
        // Chỉ cho phép sửa profile của chính mình
        if (btnEditProfile != null) {
            boolean isOwnProfile = AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId());
            btnEditProfile.setVisible(isOwnProfile);
            btnEditProfile.setManaged(isOwnProfile);
            
            // CŨNG CHỈ cho phép đổi mật khẩu của chính mình
            if (btnChangePassword != null) {
                btnChangePassword.setVisible(isOwnProfile);
                btnChangePassword.setManaged(isOwnProfile);
            }
        }
    }

    private void refreshBalanceLabels() {
        lblBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        lblLockedBalance.setText(String.format("%,.0f ₫", user.getLockedBalance()));
    }

    private void refreshAvatar() {
        if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            String uri = ImageStorageService.toFileUri(user.getAvatarPath());
            if (uri != null) {
                imgAvatar.setImage(new Image(uri));
                imgAvatar.setVisible(true);
                lblAvatar.setVisible(false);
            } else {
                imgAvatar.setVisible(false);
                lblAvatar.setVisible(true);
            }
        } else {
            imgAvatar.setVisible(false);
            lblAvatar.setVisible(true);
            String firstChar = user.getUsername().isEmpty() ? "?" : user.getUsername().substring(0, 1).toUpperCase();
            lblAvatar.setText(firstChar);
        }
    }

    private void loadStats() {
        // Stats are managed by Server, can be expanded later
        lblStat1Value.setText("0");
        lblStat2Value.setText("0");
        lblStat3Value.setText("0");
    }

    @FXML
    void handleAvatarClick() {
        if (isEditing) return;
        if (user == null || !AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId())) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh đại diện");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File selectedFile = fileChooser.showOpenDialog(lblUsername.getScene().getWindow());
        if (selectedFile == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/avatar_crop_dialog.fxml"));
            Parent root = loader.load();
            AvatarCropController cropCtrl = loader.getController();
            cropCtrl.setImage(
                new javafx.scene.image.Image(selectedFile.toURI().toString()),
                this::uploadCroppedAvatar
            );
            Stage cropStage = new Stage();
            cropStage.setTitle("Chỉnh sửa ảnh đại diện");
            cropStage.setScene(new javafx.scene.Scene(root));
            cropStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            cropStage.setResizable(false);
            cropStage.showAndWait();
        } catch (Exception e) {
            utils.AlertHelper.show(utils.AlertHelper.Type.ERROR,
                    "Lỗi", "Không thể mở trình chỉnh sửa: " + e.getMessage());
        }
    }

    private void uploadCroppedAvatar(File croppedFile) {
        try {
            String newAvatarPath = ImageStorageService.saveAvatar(croppedFile, user.getUserId());
            AppState.getInstance().getClient().setStringMessageCallback(msg -> {
                javafx.application.Platform.runLater(() -> {
                    if (msg.equals("UPDATE_AVATAR_OK")) {
                        user.setAvatarPath(newAvatarPath);
                        refreshAvatar();
                        if (onAvatarChanged != null) onAvatarChanged.run();
                        utils.AlertHelper.show(utils.AlertHelper.Type.SUCCESS,
                                "Thành công", "Đã cập nhật ảnh đại diện!");
                    } else {
                        utils.AlertHelper.show(utils.AlertHelper.Type.ERROR, "Thất bại", msg);
                    }
                });
            });
            AppState.getInstance().getClient().send("UPDATE_AVATAR:" + user.getUserId() + ":" + newAvatarPath);
        } catch (Exception e) {
            utils.AlertHelper.show(utils.AlertHelper.Type.ERROR,
                    "Lỗi", "Không thể lưu ảnh: " + e.getMessage());
        }
    }

    @FXML
    void handleEditProfile() {
        if (!isEditing) {
            // Chuyển sang chế độ Sửa
            isEditing = true;
            btnEditProfile.setText("Lưu thay đổi");
            btnEditProfile.getStyleClass().add("button-primary");
            
            txtEmail.setText(user.getEmail());
            txtPhone.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
            dpDOB.setValue(user.getDateOfBirth());

            toggleEditUI(true);
        } else {
            // Thực hiện Lưu
            String email = txtEmail.getText().trim();
            String phone = txtPhone.getText().trim();
            String dob = (dpDOB.getValue() != null) ? dpDOB.getValue().toString() : "NULL";

            if (email.isEmpty()) {
                utils.AlertHelper.show(utils.AlertHelper.Type.WARNING, "Email không được để trống");
                return;
            }

            String cmd = String.format("UPDATE_PROFILE:%s:%s:%s:%s", user.getUserId(), email, phone, dob);
            
            AppState.getInstance().getClient().setStringMessageCallback(msg -> {
                javafx.application.Platform.runLater(() -> {
                    if (msg.equals("UPDATE_PROFILE_OK")) {
                        user.setEmail(email);
                        user.setPhoneNumber(phone);
                        user.setDateOfBirth(dpDOB.getValue());
                        
                        setUserData(user); // Refresh UI labels
                        
                        isEditing = false;
                        btnEditProfile.setText("Sửa hồ sơ");
                        btnEditProfile.getStyleClass().remove("button-primary");
                        toggleEditUI(false);
                        
                        utils.AlertHelper.show(utils.AlertHelper.Type.SUCCESS,
                                "Thành công", "Đã cập nhật thông tin hồ sơ!");
                    } else {
                        utils.AlertHelper.show(utils.AlertHelper.Type.ERROR, "Thất bại", msg);
                    }
                });
            });
            
            AppState.getInstance().getClient().send(cmd);
        }
    }
    
    private void toggleEditUI(boolean editing) {
        lblEmail.setVisible(!editing);
        txtEmail.setVisible(editing);
        
        lblPhone.setVisible(!editing);
        txtPhone.setVisible(editing);
        
        lblDOB.setVisible(!editing);
        dpDOB.setVisible(editing);
        
        if (btnChangePassword != null) {
            btnChangePassword.setDisable(editing);
        }
    }

    @FXML
    void handleChangePassword() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/change_password.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("Đổi mật khẩu");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleClose() {
        ((Stage) lblUsername.getScene().getWindow()).close();
    }
}
