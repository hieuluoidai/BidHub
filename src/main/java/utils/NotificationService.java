package utils;

import database.NotificationDAO;
import database.UserDAO;
import model.notification.Notification;
import model.user.Admin;
import model.user.User;
import network.AuctionServer;

import java.util.List;

/**
 * Tiện ích server-side: tạo notification trong DB + push tín hiệu refresh
 * tới đúng client (nếu đang online).
 *
 * Dùng pattern "push a refresh signal" thay vì push object đầy đủ — client
 * sẽ tự gọi FETCH_NOTIFICATIONS để lấy danh sách mới nhất. Cách này đơn giản,
 * tránh đồng bộ rắc rối và tận dụng pagination/limit của DAO.
 */
public class NotificationService {

    private static final NotificationDAO DAO = new NotificationDAO();

    /** Tạo notification cho 1 user cụ thể + push tín hiệu refresh nếu online. */
    public static void notifyUser(AuctionServer server, String userId,
                                  Notification.Type type, String title, String message) {
        if (userId == null) return;
        String id = DAO.insert(userId, type, title, message);
        if (id != null && server != null) {
            server.sendToUser(userId, new Notification.RefreshSignal());
        }
    }

    /**
     * Gộp thông báo tin nhắn chat: nếu receiver đã có thông báo chưa đọc từ sender này,
     * cập nhật nội dung thay vì tạo mới — tránh spam thông báo.
     */
    public static void notifyChat(AuctionServer server, String receiverId,
                                  String senderId, String senderName, String preview) {
        if (receiverId == null) return;
        String title = "Tin nhắn mới từ " + senderName;
        String id = DAO.upsertChat(receiverId, senderId, title, preview);
        if (id != null && server != null) {
            server.sendToUser(receiverId, new Notification.RefreshSignal());
        }
    }

    /** Tạo notification cho tất cả Admin trong DB + push refresh signal cho admin online. */
    public static void notifyAdmins(AuctionServer server,
                                    Notification.Type type, String title, String message) {
        UserDAO userDao = new UserDAO();
        List<User> all = userDao.findAll();
        for (User u : all) {
            if (u instanceof Admin) {
                notifyUser(server, u.getUserId(), type, title, message);
            }
        }
    }
}
