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
import model.user.Admin;
import model.user.Seller;
import utils.AlertHelper;
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
    private javafx.collections.ListChangeListener<model.auction.Auction> statsListener;

    public void setOnAvatarChanged(Runnable callback) {
        this.onAvatarChanged = callback;
    }

    public void setUserData(User user) {
        setUserData(user, false);
    }

    public void setUserData(User user, boolean autoApprove) {
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
        
        boolean isAdmin = AppState.getInstance().getCurrentUser() instanceof Admin;
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

        if (btnRevokeSeller != null) {
            boolean isSelf = AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId());
            boolean canRevoke = isAdmin && !isSelf && (user instanceof Seller);
            btnRevokeSeller.setVisible(canRevoke);
            btnRevokeSeller.setManaged(canRevoke);
        }

        setupAvatarEffects();
        refreshBalanceLabels();
        refreshAvatar();
        loadStats();
        attachRealtimeStats();

        if (autoApprove && user.isPendingSeller() && AppState.getInstance().getCurrentUser() instanceof Admin) {
            javafx.application.Platform.runLater(this::handleApproveSeller);
        }
    }

    @FXML
    void handleApproveSeller() {
        if (user == null) return;

        boolean confirm = AlertHelper.showConfirm(
            "Xác nhận phê duyệt",
            "Bạn có chắc chắn muốn phê duyệt người dùng này làm Seller không?"
        );

        if (confirm) {
            String cmd = "APPROVE_SELLER:" + user.getUserId();
            AppState.getInstance().getClient().send(cmd);
            user.setPendingSeller(false);
            btnApproveSeller.setVisible(false);
            lblPendingStatus.setVisible(false);
            lblPendingStatusLabel.setVisible(false);
            lblRoleBadge.setText("SELLER");
        }
    }

    @FXML
    void handleRevokeSeller() {
        if (user == null) return;
        boolean confirm = AlertHelper.showConfirm("Xác nhận hủy quyền Seller", "Bạn có chắc chắn muốn hủy quyền Seller?");
        if (!confirm) return;

        String cmd = "REVOKE_SELLER:" + user.getUserId();
        AppState.getInstance().getClient().send(cmd);
        btnRevokeSeller.setVisible(false);
        lblRoleBadge.setText("BIDDER");
    }

    private void setupAvatarEffects() {
        if (imgAvatar != null && avatarClip != null) imgAvatar.setClip(avatarClip);
        if (avatarContainer != null && cameraOverlay != null) {
            boolean isOwnProfile = AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId());
            avatarContainer.setOnMouseEntered(e -> {
                if (isOwnProfile && !isEditing) {
                    cameraOverlay.setVisible(true);
                }
            });
            avatarContainer.setOnMouseExited(e -> cameraOverlay.setVisible(false));
        }
        if (btnEditProfile != null) {
            boolean isOwnProfile = AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId());
            btnEditProfile.setVisible(isOwnProfile);
            if (btnChangePassword != null) btnChangePassword.setVisible(isOwnProfile);
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
        String uid = user.getUserId();
        int created = 0;
        int bidCount = 0;
        int won = 0;
        for (model.auction.Auction a : AppState.getInstance().getAuctionList()) {
            if (uid.equals(a.getSellerId())) created++;
            for (model.auction.BidTransaction tx : a.getBidHistory()) {
                if (tx.getBidder() != null && uid.equals(tx.getBidder().getUserId())) {
                    bidCount++;
                }
            }
            String st = a.getStatus();
            boolean ended = "FINISHED".equals(st) || "PAID".equals(st);
            if (ended && a.getHighestBid() != null && a.getHighestBid().getBidder() != null
                    && uid.equals(a.getHighestBid().getBidder().getUserId())) {
                won++;
            }
        }
        lblStat1Value.setText(String.valueOf(created));
        lblStat2Value.setText(String.valueOf(bidCount));
        lblStat3Value.setText(String.valueOf(won));
    }

    @FXML
    void handleAvatarClick() {
        if (isEditing) return;
        if (user == null || !AppState.getInstance().getCurrentUser().getUserId().equals(user.getUserId())) return;
        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(lblUsername.getScene().getWindow());
        if (selectedFile == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/avatar_crop_dialog.fxml"));
            Parent root = loader.load();
            AvatarCropController cropCtrl = loader.getController();
            cropCtrl.setImage(new Image(selectedFile.toURI().toString()), this::uploadCroppedAvatar);
            Stage cropStage = new Stage();
            cropStage.setScene(new javafx.scene.Scene(root));
            cropStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
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
                        AlertHelper.show(AlertHelper.Type.SUCCESS, "Đã cập nhật ảnh đại diện!");
                    }
                });
            });
            AppState.getInstance().getClient().send("UPDATE_AVATAR:" + user.getUserId() + ":" + newAvatarPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleEditProfile() {
        if (!isEditing) {
            isEditing = true;
            btnEditProfile.setText("Lưu thay đổi");
            txtEmail.setText(user.getEmail());
            txtPhone.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
            dpDOB.setValue(user.getDateOfBirth());
            toggleEditUI(true);
        } else {
            String email = txtEmail.getText().trim();
            String phone = txtPhone.getText().trim();
            String dob = (dpDOB.getValue() != null) ? dpDOB.getValue().toString() : "NULL";
            if (email.isEmpty()) return;
            String cmd = String.format("UPDATE_PROFILE:%s:%s:%s:%s", user.getUserId(), email, phone, dob);
            AppState.getInstance().getClient().setStringMessageCallback(msg -> {
                javafx.application.Platform.runLater(() -> {
                    if (msg.equals("UPDATE_PROFILE_OK")) {
                        user.setEmail(email); user.setPhoneNumber(phone); user.setDateOfBirth(dpDOB.getValue());
                        setUserData(user); isEditing = false; btnEditProfile.setText("Sửa hồ sơ"); toggleEditUI(false);
                        AlertHelper.show(AlertHelper.Type.SUCCESS, "Đã cập nhật hồ sơ!");
                    }
                });
            });
            AppState.getInstance().getClient().send(cmd);
        }
    }
    
    private void toggleEditUI(boolean editing) {
        lblEmail.setVisible(!editing); txtEmail.setVisible(editing);
        lblPhone.setVisible(!editing); txtPhone.setVisible(editing);
        lblDOB.setVisible(!editing); dpDOB.setVisible(editing);
        if (btnChangePassword != null) btnChangePassword.setDisable(editing);
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

    private void attachRealtimeStats() {
        javafx.collections.ObservableList<model.auction.Auction> list = AppState.getInstance().getAuctionList();
        if (statsListener != null) list.removeListener(statsListener);
        statsListener = c -> javafx.application.Platform.runLater(() -> {
            if (user != null && lblStat1Value != null && lblStat1Value.getScene() != null) {
                loadStats();
                refreshBalanceLabels();
            }
        });
        list.addListener(statsListener);
        if (lblUsername.getScene() != null) {
            bindCleanup();
        } else {
            lblUsername.sceneProperty().addListener((obs, oldS, newS) -> {
                if (newS != null) bindCleanup();
            });
        }
    }

    private void bindCleanup() {
        javafx.stage.Window win = lblUsername.getScene().getWindow();
        if (win != null) {
            win.setOnHidden(e -> {
                if (statsListener != null) {
                    AppState.getInstance().getAuctionList().removeListener(statsListener);
                    statsListener = null;
                }
            });
        }
    }
}
