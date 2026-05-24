package utils;

import database.UserDAO;
import network.ClientHandler;
import java.util.Locale;

/**
 * Tiện ích server-side: gửi các phản hồi tiêu chuẩn tới Client.
 */
public class ServerResponseService {

    /**
     * Gửi cập nhật số dư (available + locked) tới một client cụ thể.
     */
    public static void pushBalanceUpdate(ClientHandler context, String userId) {
        if (userId == null || context == null) return;
        
        UserDAO userDao = new UserDAO();
        double avail = userDao.getBalance(userId);
        double locked = userDao.getLockedBalance(userId);
        
        String balanceMsg = String.format(Locale.US, "BALANCE_UPDATE:%.2f:%.2f", avail, locked);
        context.getServer().sendToUser(userId, balanceMsg);
    }

    /**
     * Gửi thông báo nạp tiền thành công.
     */
    public static void sendTopUpOk(ClientHandler context, String userId, double newBalance) {
        context.send("TOPUP_OK:" + newBalance);
        pushBalanceUpdate(context, userId);
    }
}
