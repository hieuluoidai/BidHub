package database;

import model.auction.AutoBid;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for managing AutoBid entities in MySQL using Connection Pool.
 */
public class AutoBidDAO {

    public AutoBidDAO() {
    }

    public boolean save(AutoBid autoBid) {
        String sql = """
                INSERT INTO auto_bids (auto_bid_id, auction_id, user_id, max_bid, increment, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    max_bid = VALUES(max_bid),
                    increment = VALUES(increment),
                    created_at = VALUES(created_at)
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, autoBid.getAutoBidId());
            stmt.setString(2, autoBid.getAuctionId());
            stmt.setString(3, autoBid.getUserId());
            stmt.setDouble(4, autoBid.getMaxBid());
            stmt.setDouble(5, autoBid.getIncrement());
            stmt.setTimestamp(6, Timestamp.valueOf(autoBid.getCreatedAt()));
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu AutoBid: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(String autoBidId) {
        String sql = "DELETE FROM auto_bids WHERE auto_bid_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, autoBidId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi xóa AutoBid: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteByUserAndAuction(String userId, String auctionId) {
        String sql = "DELETE FROM auto_bids WHERE user_id = ? AND auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi xóa AutoBid: " + e.getMessage());
            return false;
        }
    }

    public List<AutoBid> findByAuctionId(String auctionId) {
        List<AutoBid> list = new ArrayList<>();
        String sql = "SELECT * FROM auto_bids WHERE auction_id = ? ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSetToAutoBid(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách AutoBid theo phiên: " + e.getMessage());
        }
        return list;
    }

    public AutoBid findByUserAndAuction(String userId, String auctionId) {
        String sql = "SELECT * FROM auto_bids WHERE user_id = ? AND auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapResultSetToAutoBid(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi tìm AutoBid: " + e.getMessage());
        }
        return null;
    }

    public List<AutoBid> findAll() {
        List<AutoBid> list = new ArrayList<>();
        String sql = "SELECT * FROM auto_bids";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToAutoBid(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy tất cả AutoBid: " + e.getMessage());
        }
        return list;
    }

    private AutoBid mapResultSetToAutoBid(ResultSet rs) throws SQLException {
        AutoBid ab = new AutoBid(
                rs.getString("auto_bid_id"),
                rs.getString("auction_id"),
                rs.getString("user_id"),
                rs.getDouble("max_bid"),
                rs.getDouble("increment")
        );
        ab.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return ab;
    }
}
