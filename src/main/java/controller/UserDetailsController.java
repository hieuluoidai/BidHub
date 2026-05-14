package controller;

import database.AuctionDAO;
import database.BidTransactionDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.DatePicker;
import javafx.stage.Stage;
import model.auction.Auction;
import model.manager.AppState;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UserDetailsController {

    @FXML private Label lblAvatar;
    @FXML private Label lblUsername;
    @FXML private Label lblRoleBadge;
    @FXML private Label lblUserId;
    
    @FXML private Label lblEmail;
    @FXML private TextField txtEmail;
    
    @FXML private Label lblPhone;
    @FXML private TextField txtPhone;
    
    @FXML private Label lblDOB;
    @FXML private DatePicker dpDOB;
    
    @FXML private Label lblBalance;
    @FXML private Label lblLockedBalance;

    @FXML private Label lblStat1Value;
    @FXML private Label lblStat1Label;
    @FXML private Label lblStat2Value;
    @FXML private Label lblStat2Label;
    @FXML private Label lblStat3Value;
    @FXML private Label lblStat3Label;
    
    @FXML private javafx.scene.control.Button btnEditProfile;
    @FXML private javafx.scene.control.Button btnChangePassword;

    private User user;

    public void setUserData(User user) {
        if (user == null) return;
        this.user = user;

        String firstChar = user.getUsername().substring(0, 1).toUpperCase();
        lblAvatar.setText(firstChar);

        lblUsername.setText(user.getUsername());
        lblUserId  .setText(user.getUserId());
        
        refreshProfileData();

        // Hiển thị số dư
        lblBalance.setText(String.format("$%,.2f", user.getBalance()));
        lblLockedBalance.setText(String.format("$%,.2f", user.getLockedBalance()));

        // Chỉ hiện nút "Sửa hồ sơ" & "Đổi mật khẩu" nếu là chính chủ
        User currentUser = AppState.getInstance().getCurrentUser();
        boolean isSelf = (currentUser != null && currentUser.getUserId().equals(user.getUserId()));
        
        if (btnEditProfile != null) {
            btnEditProfile.setVisible(isSelf);
            btnEditProfile.setManaged(isSelf);
        }
        if (btnChangePassword != null) {
            btnChangePassword.setVisible(isSelf);
            btnChangePassword.setManaged(isSelf);
        }

        // Role + màu badge + thống kê
        updateRoleUI();
    }

    private void refreshProfileData() {
        lblEmail.setText(user.getEmail());
        txtEmail.setText(user.getEmail());
        
        String phone = user.getPhoneNumber();
        lblPhone.setText((phone == null || phone.isBlank()) ? "Chưa cập nhật" : phone);
        txtPhone.setText(phone);
        
        if (user.getDateOfBirth() != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            lblDOB.setText(user.getDateOfBirth().format(fmt));
            dpDOB.setValue(user.getDateOfBirth());
        } else {
            lblDOB.setText("Chưa cập nhật");
            dpDOB.setValue(null);
        }
    }

    private void updateRoleUI() {
        if (user instanceof Admin) {
            lblRoleBadge.setText("ADMIN");
            loadAdminStats();
        } else if (user instanceof Seller) {
            lblRoleBadge.setText("SELLER");
            loadSellerStats();
        } else {
            lblRoleBadge.setText("BIDDER");
            loadBidderStats();
        }
    }

    private void loadSellerStats() {
        java.util.Set<String> ownedIds = new database.AuctionDAO().getAuctionIdsBySeller(user.getUserId());
        List<Auction> all = AppState.getInstance().getAuctionList();
        long running = all.stream().filter(a -> ownedIds.contains(a.getAuctionId()) && "RUNNING".equals(a.getStatus())).count();
        long finished = all.stream().filter(a -> ownedIds.contains(a.getAuctionId()) && "FINISHED".equals(a.getStatus())).count();

        lblStat1Label.setText("Phiên đã tạo");
        lblStat1Value.setText(String.valueOf(ownedIds.size()));
        lblStat2Label.setText("Đang chạy");
        lblStat2Value.setText(String.valueOf(running));
        lblStat3Label.setText("Đã kết thúc");
        lblStat3Value.setText(String.valueOf(finished));
    }

    private void loadBidderStats() {
        BidTransactionDAO bidDao = new BidTransactionDAO();
        lblStat1Label.setText("Lượt bid");
        lblStat1Value.setText(String.valueOf(bidDao.countBidsByBidderId(user.getUserId())));
        lblStat2Label.setText("Phiên thắng");
        lblStat2Value.setText(String.valueOf(bidDao.countWinsByBidderId(user.getUserId())));
        lblStat3Label.setText("Đang tham gia");
        lblStat3Value.setText(String.valueOf(bidDao.countActiveParticipations(user.getUserId())));
    }

    private void loadAdminStats() {
        List<Auction> all = AppState.getInstance().getAuctionList();
        lblStat1Label.setText("Tổng phiên");
        lblStat1Value.setText(String.valueOf(all.size()));
        lblStat2Label.setText("Đang chạy");
        lblStat2Value.setText(String.valueOf(all.stream().filter(a -> "RUNNING".equals(a.getStatus())).count()));
        lblStat3Label.setText("Đã kết thúc");
        lblStat3Value.setText(String.valueOf(all.stream().filter(a -> "FINISHED".equals(a.getStatus())).count()));
    }

    private boolean isEditMode = false;
    @FXML
    void handleEditProfile() {
        if (!isEditMode) {
            isEditMode = true;
            toggleEditUI(true);
            btnEditProfile.setText("Lưu hồ sơ");
            btnEditProfile.setStyle("-fx-background-color: #10B981; -fx-text-fill: white;");
        } else {
            String newEmail = txtEmail.getText().trim();
            String newPhone = txtPhone.getText().trim();
            LocalDate newDOB = dpDOB.getValue();

            if (newEmail.isEmpty()) {
                utils.AlertHelper.show(utils.AlertHelper.Type.ERROR, "Lỗi", "Email không được để trống.");
                return;
            }

            String dobStr = (newDOB != null) ? newDOB.toString() : "NULL";
            String cmd = String.format("UPDATE_PROFILE:%s:%s:%s:%s", user.getUserId(), newEmail, newPhone, dobStr);

            AppState.getInstance().getClient().setStringMessageCallback(msg -> {
                javafx.application.Platform.runLater(() -> {
                    if (msg.equals("UPDATE_PROFILE_OK")) {
                        user.setPhoneNumber(newPhone);
                        user.setDateOfBirth(newDOB);
                        // Email update logic could be here if needed
                        utils.AlertHelper.show(utils.AlertHelper.Type.SUCCESS, "Thành công", "Đã cập nhật hồ sơ!");
                        isEditMode = false;
                        toggleEditUI(false);
                        btnEditProfile.setText("Sửa hồ sơ");
                        btnEditProfile.setStyle("");
                        refreshProfileData();
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
    }

    @FXML
    void handleChangePassword() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/view/change_password.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Đổi mật khẩu");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    void handleClose() {
        ((Stage) lblUsername.getScene().getWindow()).close();
    }
}
