package network.handler;

import database.UserDAO;
import database.WalletTransactionDAO;
import database.DepositRequestDAO;
import model.notification.Notification;
import model.user.User;
import model.auction.DepositRequest;
import network.ClientHandler;
import utils.NotificationService;

/**
 * Handles wallet and deposit-related commands.
 */
public class WalletHandler implements RequestHandler {

    @Override
    public void handle(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (msg.startsWith("TOPUP:")) {
            if (parts.length >= 2) context.setUserId(parts[1]);
            handleTopUp(context, msg);
        } else if (msg.startsWith("DEPOSIT_REQUEST:")) {
            if (parts.length >= 2) context.setUserId(parts[1]);
            handleDepositRequest(context, msg);
        } else if (msg.startsWith("DEPOSIT_REVIEW:")) {
            handleDepositReview(context, msg);
        }
    }

    private void handleTopUp(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            context.send("TOPUP_FAILED: Lệnh sai định dạng");
            return;
        }
        String userId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            context.send("TOPUP_FAILED: Số tiền không hợp lệ");
            return;
        }

        if (amount <= 0) {
            context.send("TOPUP_FAILED: Số tiền phải lớn hơn 0");
            return;
        }

        UserDAO userDao = new UserDAO();
        User user = userDao.findById(userId);
        if (user == null) {
            context.send("TOPUP_FAILED: Người dùng không tồn tại");
            return;
        }

        double current = userDao.getBalance(userId);
        double newBalance = current + amount;
        boolean ok = userDao.setBalance(userId, newBalance);

        if (ok) {
            new WalletTransactionDAO().save(userId, amount, 
                model.auction.WalletTransaction.TransactionType.TOPUP, 
                "Nạp tiền vào ví qua hệ thống");

            NotificationService.notifyUser(context.getServer(), userId,
                Notification.Type.WALLET_TOPUP,
                "Nạp tiền thành công",
                String.format("Bạn đã nạp thành công %,.0f ₫ vào ví.", amount));

            context.send("TOPUP_OK:" + newBalance);
            
            double locked = userDao.getLockedBalance(userId);
            context.send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", newBalance, locked));
        } else {
            context.send("TOPUP_FAILED: Lỗi cập nhật số dư");
        }
    }

    private void handleDepositRequest(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) {
            context.send("DEPOSIT_REQUEST_FAILED:Lệnh sai định dạng");
            return;
        }
        String userId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            context.send("DEPOSIT_REQUEST_FAILED:Số tiền không hợp lệ");
            return;
        }
        String requestId = parts[3];

        if (amount <= 0) {
            context.send("DEPOSIT_REQUEST_FAILED:Số tiền phải lớn hơn 0");
            return;
        }

        UserDAO userDao = new UserDAO();
        User user = userDao.findById(userId);
        if (user == null) {
            context.send("DEPOSIT_REQUEST_FAILED:Người dùng không tồn tại");
            return;
        }

        boolean saved = new DepositRequestDAO().save(requestId, userId, amount);
        if (!saved) {
            context.send("DEPOSIT_REQUEST_FAILED:Lỗi lưu yêu cầu");
            return;
        }

        context.send("DEPOSIT_REQUEST_OK");
        NotificationService.notifyAdmins(context.getServer(),
                Notification.Type.ADMIN_DEPOSIT_REQUEST,
                "Yêu cầu nạp tiền mới",
                String.format("%s muốn nạp %,.0f ₫ (mã: %s)", user.getUsername(), amount, requestId));
        context.getServer().broadcastToRole("ADMIN", "NEW_DEPOSIT_REQUEST:" + userId);
    }

    private void handleDepositReview(ClientHandler context, String msg) {
        String[] parts = msg.split(":", 4);
        if (parts.length < 3) {
            context.send("DEPOSIT_REVIEW_FAILED:Lệnh sai định dạng");
            return;
        }
        String requestId = parts[1];
        String statusStr  = parts[2];
        String adminNote  = parts.length >= 4 ? parts[3] : "";

        DepositRequest.Status status;
        try {
            status = DepositRequest.Status.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            context.send("DEPOSIT_REVIEW_FAILED:Trạng thái không hợp lệ");
            return;
        }

        DepositRequestDAO dao = new DepositRequestDAO();
        DepositRequest dr = dao.findById(requestId);
        if (dr == null) {
            context.send("DEPOSIT_REVIEW_FAILED:Không tìm thấy yêu cầu");
            return;
        }

        boolean ok = dao.review(requestId, status, adminNote);
        if (!ok) {
            context.send("DEPOSIT_REVIEW_FAILED:Lỗi cập nhật DB");
            return;
        }

        if (status == DepositRequest.Status.APPROVED) {
            UserDAO userDao = new UserDAO();
            double current    = userDao.getBalance(dr.getUserId());
            double newBalance = current + dr.getAmount();
            userDao.setBalance(dr.getUserId(), newBalance);

            new WalletTransactionDAO().save(dr.getUserId(), dr.getAmount(),
                    model.auction.WalletTransaction.TransactionType.TOPUP,
                    "Nạp tiền qua chuyển khoản - Mã: " + requestId);

            NotificationService.notifyUser(context.getServer(), dr.getUserId(),
                    Notification.Type.WALLET_TOPUP,
                    "Nạp tiền thành công",
                    String.format("Yêu cầu nạp %,.0f ₫ (mã: %s) đã được duyệt.", dr.getAmount(), requestId));

            double locked = userDao.getLockedBalance(dr.getUserId());
            context.getServer().sendToUser(dr.getUserId(), String.format(java.util.Locale.US,
                    "BALANCE_UPDATE:%.2f:%.2f", newBalance, locked));
        } else {
            NotificationService.notifyUser(context.getServer(), dr.getUserId(),
                    Notification.Type.DEPOSIT_REJECTED,
                    "Yêu cầu nạp tiền bị từ chối",
                    "Yêu cầu nạp tiền (mã: " + requestId + ") bị từ chối."
                            + (adminNote.isEmpty() ? "" : " Lý do: " + adminNote));
        }

        context.send("DEPOSIT_REVIEW_OK");
        context.getServer().broadcastToRole("ADMIN", "USERS_UPDATED");
    }
}
