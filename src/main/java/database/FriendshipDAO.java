package database;

import model.friendship.Friendship;
import model.friendship.Friendship.Status;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class FriendshipDAO {

    /** Gửi lời mời kết bạn. Trả false nếu đã tồn tại quan hệ. */
    public boolean sendRequest(String requesterId, String addresseeId) {
        String sql = "INSERT IGNORE INTO friendships (requester_id, addressee_id) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, requesterId);
            stmt.setString(2, addresseeId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi sendRequest: " + e.getMessage());
            return false;
        }
    }

    /** Chấp nhận lời mời (requester → addressee = me). */
    public boolean accept(String requesterId, String addresseeId) {
        String sql = "UPDATE friendships SET status = 'ACCEPTED' "
                   + "WHERE requester_id = ? AND addressee_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, requesterId);
            stmt.setString(2, addresseeId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi accept: " + e.getMessage());
            return false;
        }
    }

    /** Từ chối / hủy lời mời. */
    public boolean decline(String requesterId, String addresseeId) {
        String sql = "DELETE FROM friendships WHERE requester_id = ? AND addressee_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, requesterId);
            stmt.setString(2, addresseeId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi decline: " + e.getMessage());
            return false;
        }
    }

    /** Hủy kết bạn (xóa cả hai chiều). */
    public boolean unfriend(String userId1, String userId2) {
        String sql = "DELETE FROM friendships WHERE "
                   + "(requester_id = ? AND addressee_id = ?) OR "
                   + "(requester_id = ? AND addressee_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId1); stmt.setString(2, userId2);
            stmt.setString(3, userId2); stmt.setString(4, userId1);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi unfriend: " + e.getMessage());
            return false;
        }
    }

    /** Trả về "NONE","PENDING_SENT","PENDING_RECEIVED","ACCEPTED" từ góc nhìn của myId. */
    public String getStatus(String myId, String otherId) {
        String sql = "SELECT requester_id, status FROM friendships "
                   + "WHERE (requester_id = ? AND addressee_id = ?) "
                   + "   OR (requester_id = ? AND addressee_id = ?) LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, myId);   stmt.setString(2, otherId);
            stmt.setString(3, otherId); stmt.setString(4, myId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return "NONE";
                String requester = rs.getString("requester_id");
                String status    = rs.getString("status");
                if ("ACCEPTED".equals(status)) return "ACCEPTED";
                if ("PENDING".equals(status)) {
                    return myId.equals(requester) ? "PENDING_SENT" : "PENDING_RECEIVED";
                }
                return "NONE";
            }
        } catch (SQLException e) {
            System.err.println("Lỗi getStatus: " + e.getMessage());
            return "NONE";
        }
    }

    public boolean areFriends(String userId1, String userId2) {
        return "ACCEPTED".equals(getStatus(userId1, userId2));
    }

    /**
     * Bundle đầy đủ: danh sách bạn bè (ACCEPTED) + lời mời đang chờ tôi xử lý (PENDING received).
     * JOIN với users để lấy thông tin partner.
     */
    public Friendship.Bundle getFriendBundle(String myId) {
        List<Friendship> friends = new ArrayList<>();
        List<Friendship> pending = new ArrayList<>();

        String sql = "SELECT "
                   + "  f.requester_id, f.addressee_id, f.status, f.created_at,"
                   + "  u.user_id AS partner_id, u.username, u.avatar_path, u.role "
                   + "FROM friendships f "
                   + "JOIN users u ON u.user_id = CASE "
                   + "  WHEN f.requester_id = ? THEN f.addressee_id "
                   + "  ELSE f.requester_id END "
                   + "WHERE (f.requester_id = ? OR f.addressee_id = ?) "
                   + "  AND f.status IN ('ACCEPTED','PENDING') "
                   + "ORDER BY f.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, myId);
            stmt.setString(2, myId);
            stmt.setString(3, myId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Friendship f = mapRow(rs);
                    if ("ACCEPTED".equals(rs.getString("status"))) {
                        friends.add(f);
                    } else if ("PENDING".equals(rs.getString("status"))
                            && myId.equals(rs.getString("addressee_id"))) {
                        pending.add(f);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi getFriendBundle: " + e.getMessage());
        }
        return new Friendship.Bundle(friends, pending);
    }

    /**
     * Tìm kiếm user theo username, kèm trạng thái quan hệ với myId.
     */
    public Friendship.SearchBundle search(String myId, String query) {
        UserDAO userDao = new UserDAO();
        List<User> users = userDao.searchByUsername(query, myId);
        List<Friendship.SearchResult> results = new ArrayList<>();
        for (User u : users) {
            String status = getStatus(myId, u.getUserId());
            results.add(new Friendship.SearchResult(
                    u.getUserId(), u.getUsername(),
                    u.getAvatarPath(),
                    u instanceof model.user.Admin ? "ADMIN" : u instanceof model.user.Seller ? "SELLER" : "BIDDER",
                    status));
        }
        return new Friendship.SearchBundle(results);
    }

    private Friendship mapRow(ResultSet rs) throws SQLException {
        Friendship f = new Friendship();
        f.setRequesterId(rs.getString("requester_id"));
        f.setAddresseeId(rs.getString("addressee_id"));
        try {
            f.setStatus(Status.valueOf(rs.getString("status")));
        } catch (IllegalArgumentException ignored) {
        }
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) f.setCreatedAt(ts.toLocalDateTime());
        f.setPartnerId(rs.getString("partner_id"));
        f.setPartnerUsername(rs.getString("username"));
        f.setPartnerAvatarPath(rs.getString("avatar_path"));
        f.setPartnerRole(rs.getString("role"));
        return f;
    }
}
