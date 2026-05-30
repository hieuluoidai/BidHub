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
    private static volatile Boolean sourceIdSupported;

    /** Tra ve notification_id moi (da insert), hoac null neu loi. */
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
            if (stmt.executeUpdate() > 0) {
                return id;
            }
        } catch (SQLException e) {
            System.err.println("Loi insert notification: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gop thong bao chat: neu da co thong bao CHAT_NEW_MESSAGE chua doc tu cung sender,
     * cap nhat noi dung thay vi tao moi de tranh spam.
     */
    public String upsertChat(String receiverId, String senderId, String title, String message) {
        if (!supportsSourceId()) {
            return insert(receiverId, Notification.Type.CHAT_NEW_MESSAGE, title, message);
        }

        String updSql = "UPDATE notifications "
                + "SET title = ?, message = ?, created_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND type = 'CHAT_NEW_MESSAGE' "
                + "AND source_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement upd = conn.prepareStatement(updSql)) {
            upd.setString(1, title);
            upd.setString(2, truncate(message, 500));
            upd.setString(3, receiverId);
            upd.setString(4, senderId);
            if (upd.executeUpdate() > 0) {
                return "updated";
            }
        } catch (SQLException e) {
            if (handleMissingSourceId(e)) {
                return insert(receiverId, Notification.Type.CHAT_NEW_MESSAGE, title, message);
            }
            System.err.println("Loi upsert chat notification: " + e.getMessage());
            return null;
        }

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
            if (ins.executeUpdate() > 0) {
                return id;
            }
        } catch (SQLException e) {
            if (handleMissingSourceId(e)) {
                return insert(receiverId, Notification.Type.CHAT_NEW_MESSAGE, title, message);
            }
            System.err.println("Loi insert chat notification: " + e.getMessage());
        }
        return null;
    }

    /** Lay thong bao moi nhat cua mot user (gioi han N). */
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
                    Notification notification = map(rs);
                    if (notification != null) {
                        list.add(notification);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi load notifications: " + e.getMessage());
        }
        return list;
    }

    public int getUnreadCount(String userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi dem unread: " + e.getMessage());
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
            System.err.println("Loi mark read: " + e.getMessage());
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
            System.err.println("Loi mark-all read: " + e.getMessage());
            return false;
        }
    }

    public void markChatAsRead(String userId, String senderId) {
        if (!supportsSourceId()) {
            markAllChatAsRead(userId);
            return;
        }

        String sql = "UPDATE notifications SET is_read = 1 "
                + "WHERE user_id = ? AND type = 'CHAT_NEW_MESSAGE' AND source_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, senderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (handleMissingSourceId(e)) {
                markAllChatAsRead(userId);
                return;
            }
            System.err.println("Loi mark chat read: " + e.getMessage());
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        String typeStr = rs.getString("type");
        if (typeStr == null) {
            return null;
        }

        Notification notification = new Notification();
        notification.setNotificationId(rs.getString("notification_id"));
        notification.setUserId(rs.getString("user_id"));
        try {
            notification.setType(Notification.Type.valueOf(typeStr.trim().toUpperCase()));
        } catch (IllegalArgumentException | NullPointerException ex) {
            System.err.println("Unknown notification type: " + typeStr);
            return null;
        }
        notification.setTitle(rs.getString("title"));
        notification.setMessage(rs.getString("message"));
        notification.setRead(rs.getBoolean("is_read"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            notification.setCreatedAt(ts.toLocalDateTime());
        }
        return notification;
    }

    private static boolean supportsSourceId() {
        Boolean cached = sourceIdSupported;
        if (cached != null) {
            return cached;
        }

        synchronized (NotificationDAO.class) {
            if (sourceIdSupported != null) {
                return sourceIdSupported;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                sourceIdSupported = hasColumn(conn, "notifications", "source_id");
            } catch (SQLException e) {
                System.err.println("Loi kiem tra notifications.source_id: " + e.getMessage());
                sourceIdSupported = false;
            }
            return sourceIdSupported;
        }
    }

    private boolean handleMissingSourceId(SQLException e) {
        if (!isMissingColumn(e)) {
            return false;
        }
        sourceIdSupported = false;
        System.err.println(">>> [WARN] notifications.source_id chua ton tai, fallback sang logic cu.");
        return true;
    }

    private void markAllChatAsRead(String userId) {
        String sql = "UPDATE notifications SET is_read = 1 "
                + "WHERE user_id = ? AND type = 'CHAT_NEW_MESSAGE' AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Loi mark all chat read fallback: " + e.getMessage());
        }
    }

    private boolean isMissingColumn(SQLException e) {
        return "42S22".equals(e.getSQLState())
                || (e.getMessage() != null && e.getMessage().contains("Unknown column"));
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        String catalog = conn.getCatalog();
        if (hasColumn(conn, catalog, tableName, columnName)) {
            return true;
        }
        if (hasColumn(conn, catalog, tableName.toUpperCase(), columnName.toUpperCase())) {
            return true;
        }
        return hasColumn(conn, catalog, tableName.toLowerCase(), columnName.toLowerCase());
    }

    private static boolean hasColumn(Connection conn, String catalog, String tableName, String columnName)
            throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(catalog, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
