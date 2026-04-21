package controller;

import database.AuctionDAO;
import database.BidTransactionDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import model.auction.Auction;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

import java.util.List;

public class UserDetailsController {

    @FXML private Label lblAvatar;
    @FXML private Label lblUsername;
    @FXML private Label lblRoleBadge;
    @FXML private Label lblUserId;
    @FXML private Label lblEmail;
    @FXML private Label lblRole;

    @FXML private Label lblStat1Value;
    @FXML private Label lblStat1Label;
    @FXML private Label lblStat2Value;
    @FXML private Label lblStat2Label;
    @FXML private Label lblStat3Value;
    @FXML private Label lblStat3Label;

    public void setUserData(User user) {
        if (user == null) return;

        // Avatar: chữ cái đầu của username
        String firstChar = user.getUsername().substring(0, 1).toUpperCase();
        lblAvatar.setText(firstChar);

        lblUsername.setText(user.getUsername());
        lblUserId  .setText(user.getUserId());
        lblEmail   .setText(user.getEmail());

        // Role + màu badge + thống kê theo role
        if (user instanceof Admin) {
            lblRole.setText("Quản trị viên (ADMIN)");
            lblRoleBadge.setText("ADMIN");
            lblRoleBadge.setStyle(lblRoleBadge.getStyle()
                .replace("#EFF6FF", "#FEF2F2").replace("#1D4ED8", "#B91C1C"));
            lblAvatar.setStyle(lblAvatar.getStyle()
                .replace("#3B82F6", "#EF4444"));
            loadAdminStats(user);
        } else if (user instanceof Seller) {
            lblRole.setText("Người bán (SELLER)");
            lblRoleBadge.setText("SELLER");
            lblRoleBadge.setStyle(lblRoleBadge.getStyle()
                .replace("#EFF6FF", "#FDF4FF").replace("#1D4ED8", "#7E22CE"));
            lblAvatar.setStyle(lblAvatar.getStyle()
                .replace("#3B82F6", "#A855F7"));
            loadSellerStats(user);
        } else if (user instanceof Bidder) {
            lblRole.setText("Người đấu giá (BIDDER)");
            lblRoleBadge.setText("BIDDER");
            lblRoleBadge.setStyle(lblRoleBadge.getStyle()
                .replace("#EFF6FF", "#F0FDF4").replace("#1D4ED8", "#15803D"));
            lblAvatar.setStyle(lblAvatar.getStyle()
                .replace("#3B82F6", "#10B981"));
            loadBidderStats(user);
        }
    }

    // ---- Thống kê theo từng role ----

    private void loadSellerStats(User user) {
        List<Auction> all = new AuctionDAO().findAll();

        // Đếm phiên seller này tạo (cần query chính xác – xem note dưới)
        long totalCreated = countAuctionsBySeller(user.getUserId());
        long running      = 0;
        long finished     = 0;
        for (Auction a : all) {
            if (!isOwnedBy(a, user.getUserId())) continue;
            if      ("RUNNING" .equals(a.getStatus())) running++;
            else if ("FINISHED".equals(a.getStatus())) finished++;
        }

        lblStat1Label.setText("Phiên đã tạo");
        lblStat1Value.setText(String.valueOf(totalCreated));

        lblStat2Label.setText("Đang chạy");
        lblStat2Value.setText(String.valueOf(running));

        lblStat3Label.setText("Đã kết thúc");
        lblStat3Value.setText(String.valueOf(finished));
    }

    private void loadBidderStats(User user) {
        BidTransactionDAO bidDao = new BidTransactionDAO();
        int totalBids = bidDao.countBidsByBidderId(user.getUserId());
        int totalWon  = bidDao.countWinsByBidderId(user.getUserId());

        lblStat1Label.setText("Lượt bid");
        lblStat1Value.setText(String.valueOf(totalBids));

        lblStat2Label.setText("Phiên thắng");
        lblStat2Value.setText(String.valueOf(totalWon));

        lblStat3Label.setText("Đang tham gia");
        lblStat3Value.setText("–");  // có thể bổ sung sau
    }

    private void loadAdminStats(User user) {
        List<Auction> all = new AuctionDAO().findAll();

        lblStat1Label.setText("Tổng phiên");
        lblStat1Value.setText(String.valueOf(all.size()));

        long running = all.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
        lblStat2Label.setText("Đang chạy");
        lblStat2Value.setText(String.valueOf(running));

        long finished = all.stream().filter(a -> "FINISHED".equals(a.getStatus())).count();
        lblStat3Label.setText("Đã kết thúc");
        lblStat3Value.setText(String.valueOf(finished));
    }

    // ---- Helper ----

    private long countAuctionsBySeller(String sellerId) {
        // Placeholder – trả về 0 nếu AuctionDAO chưa có method này
        try {
            return new AuctionDAO().countBySellerId(sellerId);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isOwnedBy(Auction a, String sellerId) {
        // Placeholder – tuỳ bạn có lưu sellerId vào Auction hay không
        return false;
    }

    @FXML
    void handleClose() {
        Stage stage = (Stage) lblUsername.getScene().getWindow();
        stage.close();
    }
}