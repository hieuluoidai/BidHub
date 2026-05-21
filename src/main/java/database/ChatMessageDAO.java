package database;

import model.chat.ChatMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatMessageDAO {

    private static volatile boolean tablesReady = false;

    public ChatMessageDAO() {
        ensureTables();
    }

    private static synchronized void ensureTables() {
        if (tablesReady) return;
        String ddl = "CREATE TABLE IF NOT EXISTS chat_messages ("
                + "message_id VARCHAR(64) PRIMARY KEY,"
                + "sender_id VARCHAR(64) NOT NULL,"
                + "receiver_id VARCHAR(64) NOT NULL,"
                + "content TEXT NOT NULL,"
                + "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "read_at TIMESTAMP NULL,"
                + "liked TINYINT(1) DEFAULT 0,"
                + "recalled TINYINT(1) DEFAULT 0,"
                + "hidden_by_sender TINYINT(1) DEFAULT 0,"
                + "hidden_by_receiver TINYINT(1) DEFAULT 0,"
                + "INDEX idx_pair_ab (sender_id, receiver_id),"
                + "INDEX idx_pair_ba (receiver_id, sender_id),"
                + "INDEX idx_receiver_unread (receiver_id, read_at)"
                + ")";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(ddl);
            // Migration: bổ sung cột recall nếu bảng cũ chưa có
            for (String col : new String[]{
                    "ALTER TABLE chat_messages ADD COLUMN recalled TINYINT(1) DEFAULT 0",
                    "ALTER TABLE chat_messages ADD COLUMN hidden_by_sender TINYINT(1) DEFAULT 0",
                    "ALTER TABLE chat_messages ADD COLUMN hidden_by_receiver TINYINT(1) DEFAULT 0"
            }) {
                try {
                    st.executeUpdate(col);
                } catch (SQLException ignore) {
                    // Cột đã tồn tại — bỏ qua
                }
            }
            tablesReady = true;
            System.out.println(">>> [CHAT] Table chat_messages sẵn sàng.");
        } catch (SQLException e) {
            System.err.println("Lỗi tạo bảng chat_messages: " + e.getMessage());
        }
    }

    public ChatMessage insert(String senderId, String receiverId, String content) {
        String id = "m-" + UUID.randomUUID().toString().substring(0, 12);
        String sql = "INSERT INTO chat_messages (message_id, sender_id, receiver_id, content) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, senderId);
            stmt.setString(3, receiverId);
            stmt.setString(4, content);
            if (stmt.executeUpdate() > 0) {
                return findById(id);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi insert chat: " + e.getMessage());
        }
        return null;
    }

    public ChatMessage findById(String messageId) {
        String sql = "SELECT * FROM chat_messages WHERE message_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi find chat: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tin nhắn giữa 2 user (cả 2 chiều), sắp xếp tăng dần theo thời gian.
     * uidA là người đang xem — lọc bỏ tin bị ẩn phía họ (hidden_by_sender/receiver).
     */
    public List<ChatMessage> findConversation(String uidA, String uidB, int limit) {
        List<ChatMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM chat_messages "
                + "WHERE ((sender_id = ? AND receiver_id = ?) "
                + "    OR (sender_id = ? AND receiver_id = ?)) "
                + "  AND NOT (sender_id = ? AND hidden_by_sender = 1) "
                + "  AND NOT (receiver_id = ? AND hidden_by_receiver = 1) "
                + "ORDER BY sent_at ASC, message_id ASC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uidA);
            stmt.setString(2, uidB);
            stmt.setString(3, uidB);
            stmt.setString(4, uidA);
            stmt.setString(5, uidA);
            stmt.setString(6, uidA);
            stmt.setInt(7, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi load conversation: " + e.getMessage());
        }
        return list;
    }

    /**
     * Thu hồi tin nhắn với tất cả mọi người (chỉ sender mới được phép).
     * Trả về object đã cập nhật để push cho cả 2 phía, hoặc null nếu thất bại.
     */
    public ChatMessage recallForAll(String messageId, String requesterId) {
        // Kiểm tra chỉ sender mới được thu hồi
        ChatMessage msg = findById(messageId);
        if (msg == null || !requesterId.equals(msg.getSenderId())) {
            return null;
        }
        if (msg.isRecalled()) {
            return msg; // Đã thu hồi rồi
        }
        String sql = "UPDATE chat_messages SET recalled = 1 WHERE message_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            if (stmt.executeUpdate() > 0) {
                return findById(messageId);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi recallForAll: " + e.getMessage());
        }
        return null;
    }

    /**
     * Ẩn tin nhắn chỉ ở phía người yêu cầu (thu hồi với tôi).
     * viewerId có thể là sender hoặc receiver.
     * Trả về true nếu thành công.
     */
    public boolean hideSelf(String messageId, String viewerId) {
        ChatMessage msg = findById(messageId);
        if (msg == null) {
            return false;
        }
        String col;
        if (viewerId.equals(msg.getSenderId())) {
            col = "hidden_by_sender";
        } else if (viewerId.equals(msg.getReceiverId())) {
            col = "hidden_by_receiver";
        } else {
            return false; // Không liên quan đến tin nhắn này
        }
        String sql = "UPDATE chat_messages SET " + col + " = 1 WHERE message_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi hideSelf: " + e.getMessage());
            return false;
        }
    }

    /** Danh sách conversations của 1 user: 1 dòng cho mỗi partner, kèm tin gần nhất + unread. */
    public List<ChatMessage.Summary> findSummaries(String userId) {
        List<ChatMessage.Summary> list = new ArrayList<>();
        // Dùng ROW_NUMBER() để lấy tin nhắn mới nhất mỗi conversation — tránh correlated subquery
        // trong điều kiện JOIN vốn gây lỗi âm thầm trên một số cấu hình MySQL.
        String sql = "SELECT ranked.partner_id, u.username, u.avatar_path,"
                + " ranked.content, ranked.sent_at, ranked.sender_id,"
                + " COALESCE(unread_tbl.unread_count, 0) AS unread"
                + " FROM ("
                + "   SELECT"
                + "     CASE WHEN sender_id = ? THEN receiver_id ELSE sender_id END AS partner_id,"
                + "     content, sent_at, sender_id,"
                + "     ROW_NUMBER() OVER ("
                + "       PARTITION BY CASE WHEN sender_id = ? THEN receiver_id ELSE sender_id END"
                + "       ORDER BY sent_at DESC, message_id DESC"
                + "     ) AS rn"
                + "   FROM chat_messages"
                + "   WHERE sender_id = ? OR receiver_id = ?"
                + " ) ranked"
                + " JOIN users u ON u.user_id = ranked.partner_id"
                + " LEFT JOIN ("
                + "   SELECT sender_id AS partner_id, COUNT(*) AS unread_count"
                + "   FROM chat_messages"
                + "   WHERE receiver_id = ? AND read_at IS NULL"
                + "   GROUP BY sender_id"
                + " ) unread_tbl ON unread_tbl.partner_id = ranked.partner_id"
                + " WHERE ranked.rn = 1"
                + " ORDER BY ranked.sent_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            stmt.setString(3, userId);
            stmt.setString(4, userId);
            stmt.setString(5, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String pid = rs.getString("partner_id");
                    String pname = rs.getString("username");
                    String avatar = rs.getString("avatar_path");
                    String content = rs.getString("content");
                    Timestamp ts = rs.getTimestamp("sent_at");
                    String lastSender = rs.getString("sender_id");
                    int unread = rs.getInt("unread");
                    boolean fromMe = userId.equals(lastSender);
                    list.add(new ChatMessage.Summary(pid, pname, avatar, content,
                            ts != null ? ts.toLocalDateTime() : null, unread, fromMe));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi load chat summaries: " + e.getMessage());
        }
        return list;
    }

    public int getTotalUnread(String userId) {
        String sql = "SELECT COUNT(*) FROM chat_messages WHERE receiver_id = ? AND read_at IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đếm chat unread: " + e.getMessage());
        }
        return 0;
    }

    /** Đánh dấu đã đọc tất cả tin trong conversation mà receiver = userId, sender = partnerId. */
    public List<String> markConversationRead(String userId, String partnerId) {
        List<String> ids = new ArrayList<>();
        String findSql = "SELECT message_id FROM chat_messages "
                + "WHERE receiver_id = ? AND sender_id = ? AND read_at IS NULL";
        String upd = "UPDATE chat_messages SET read_at = CURRENT_TIMESTAMP "
                + "WHERE receiver_id = ? AND sender_id = ? AND read_at IS NULL";
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(findSql)) {
                ps.setString(1, userId);
                ps.setString(2, partnerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getString(1));
                    }
                }
            }
            if (!ids.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setString(1, userId);
                    ps.setString(2, partnerId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi mark conversation read: " + e.getMessage());
        }
        return ids;
    }

    public boolean setLiked(String messageId, boolean liked) {
        String sql = "UPDATE chat_messages SET liked = ? WHERE message_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, liked ? 1 : 0);
            stmt.setString(2, messageId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi set liked: " + e.getMessage());
            return false;
        }
    }

    private ChatMessage map(ResultSet rs) throws SQLException {
        ChatMessage m = new ChatMessage();
        m.setMessageId(rs.getString("message_id"));
        m.setSenderId(rs.getString("sender_id"));
        m.setReceiverId(rs.getString("receiver_id"));
        m.setContent(rs.getString("content"));
        Timestamp sent = rs.getTimestamp("sent_at");
        if (sent != null) m.setSentAt(sent.toLocalDateTime());
        Timestamp read = rs.getTimestamp("read_at");
        if (read != null) m.setReadAt(read.toLocalDateTime());
        m.setLiked(rs.getInt("liked") == 1);
        m.setRecalled(rs.getInt("recalled") == 1);
        return m;
    }
}
