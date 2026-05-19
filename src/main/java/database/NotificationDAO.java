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
                    list.add(map(rs));
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

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotificationId(rs.getString("notification_id"));
        n.setUserId(rs.getString("user_id"));
        try {
            n.setType(Notification.Type.valueOf(rs.getString("type")));
        } catch (IllegalArgumentException ex) {
            // Type không nằm trong enum hiện tại (vd schema cũ) — bỏ qua
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
