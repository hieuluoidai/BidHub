package database;

import model.notification.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationDAO {

    /** Trả về notification_id mới (đã insert), hoặc null nếu lỗi. */
    public String insert(String userId, Notification.Type type, String title, String message) {
        String id = "n-" + UUID.randomUUID().toString().substring(0, 12);
        String sql = "INSERT INTO notifications (notification_id, user_id, type, title, message) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, userId);
            stmt.setString(3, type.name());
            stmt.setString(4, title);
            stmt.setString(5, truncate(message, 500));
            if (stmt.executeUpdate() > 0) return id;
        } catch (SQLException e) {
            System.err.println("Lỗi insert notification: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gộp thông báo chat: nếu đã có thông báo CHAT_NEW_MESSAGE chưa đọc từ cùng sender,
     * cập nhật nội dung thay vì tạo mới — tránh spam thông báo.
     * Trả về id (hoặc "updated") nếu thành công, null nếu lỗi.
     */
    public String upsertChat(String receiverId, String senderId, String title, String message) {
        String updSql = "UPDATE notifications "
                      + "SET title = ?, message = ?, created_at = CURRENT_TIMESTAMP "
                      + "WHERE user_id = ? AND type = 'CHAT_NEW_MESSAGE' "
                      + "  AND source_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement upd = conn.prepareStatement(updSql)) {
            upd.setString(1, title);
            upd.setString(2, truncate(message, 500));
            upd.setString(3, receiverId);
            upd.setString(4, senderId);
            if (upd.executeUpdate() > 0) return "updated";
        } catch (SQLException e) {
            System.err.println("Lỗi upsert chat notification: " + e.getMessage());
            return null;
        }
        // Không có thông báo cũ → insert mới với source_id
        String id = "n-" + UUID.randomUUID().toString().substring(0, 12);
        String insSql = "INSERT INTO notifications "
                      + "(notification_id, user_id, type, title, message, source_id) "
                      + "VALUES (?, ?, 'CHAT_NEW_MESSAGE', ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ins = conn.prepareStatement(insSql)) {
            ins.setString(1, id);
            ins.setString(2, receiverId);
            ins.setString(3, title);
            ins.setString(4, truncate(message, 500));
            ins.setString(5, senderId);
            if (ins.executeUpdate() > 0) return id;
        } catch (SQLException e) {
            System.err.println("Lỗi insert chat notification: " + e.getMessage());
        }
        return null;
    }

    /** Lấy thông báo mới nhất của 1 user (giới hạn N). */
    public List<Notification> findRecent(String userId, int limit) {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications WHERE user_id = ? "
                   + "ORDER BY created_at DESC, notification_id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Notification n = map(rs);
                    if (n != null) list.add(n);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi load notifications: " + e.getMessage());
        }
        return list;
    }

    public int getUnreadCount(String userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đếm unread: " + e.getMessage());
        }
        return 0;
    }

    public boolean markAsRead(String notificationId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE notification_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, notificationId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi mark read: " + e.getMessage());
            return false;
        }
    }

    public boolean markAllAsRead(String userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi mark-all read: " + e.getMessage());
            return false;
        }
    }

    public void markChatAsRead(String userId, String senderId) {
        String sql = "UPDATE notifications SET is_read = 1 "
                   + "WHERE user_id = ? AND type = 'CHAT_NEW_MESSAGE' AND source_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, senderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi mark chat read: " + e.getMessage());
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        String typeStr = rs.getString("type");
        if (typeStr == null) return null;
        
        Notification n = new Notification();
        n.setNotificationId(rs.getString("notification_id"));
        n.setUserId(rs.getString("user_id"));
        try {
            // Trim và UpperCase để tránh lỗi mismatch do định dạng chuỗi trong DB
            n.setType(Notification.Type.valueOf(typeStr.trim().toUpperCase()));
        } catch (IllegalArgumentException | NullPointerException ex) {
            System.err.println("Unknown notification type: " + typeStr);
            return null;
        }
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setRead(rs.getBoolean("is_read"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) n.setCreatedAt(ts.toLocalDateTime());
        return n;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
