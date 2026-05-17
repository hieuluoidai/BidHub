package database;

import model.auction.Auction;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp quản lý các Auction trong cơ sở dữ liệu sử dụng Connection Pool.
 */
public class AuctionDAO {

    private final ItemDAO itemDAO;

    public AuctionDAO() {
        this.itemDAO = new ItemDAO();
    }

    public boolean updateTime(String auctionId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = """
                UPDATE auctions
                   SET start_time = ?, end_time = ?
                 WHERE auction_id = ?
                   AND status     = 'OPEN'
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(startTime));
            stmt.setTimestamp(2, Timestamp.valueOf(endTime));
            stmt.setString(3, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật thời gian phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật riêng end_time cho phiên đang RUNNING.
     * Dùng cho Anti-sniping: khi bid phút cuối kéo dài phiên, ghi
     * end_time mới xuống DB để server restart / client reload không
     * mất thời gian gia hạn. Điều kiện status='RUNNING' tránh vô tình
     * hồi sinh phiên đã FINISHED/CANCELED/PAID.
     */
    public boolean updateEndTime(String auctionId, LocalDateTime endTime) {
        String sql = """
                UPDATE auctions
                   SET end_time = ?
                 WHERE auction_id = ?
                   AND status     = 'RUNNING'
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(endTime));
            stmt.setString(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi gia hạn end_time phiên: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(String auctionId) {
        String itemId = null;
        String sqlFindItem = "SELECT item_id FROM auctions WHERE auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlFindItem)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) itemId = rs.getString("item_id");
        } catch (SQLException e) {
            System.err.println("Lỗi tìm item_id của phiên: " + e.getMessage());
            return false;
        }

        if (itemId == null) return false;
        return itemDAO.delete(itemId);
    }

    public int deleteIfOwner(String auctionId, String sellerId) {
        String sql = """
                SELECT i.seller_id
                  FROM auctions a
                  INNER JOIN items i ON a.item_id = i.item_id
                 WHERE a.auction_id = ?
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                if (sellerId.equals(rs.getString("seller_id"))) {
                    return delete(auctionId) ? 1 : -2;
                }
                return 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra quyền xóa: " + e.getMessage());
        }
        return -2;
    }

    public boolean save(Auction auction) {
        String sql = """
                INSERT INTO auctions (auction_id, item_id, status, start_time, end_time)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auction.getAuctionId());
            stmt.setString(2, auction.getItem().getItemId());
            stmt.setString(3, auction.getStatus());
            stmt.setTimestamp(4, Timestamp.valueOf(auction.getStartTime()));
            stmt.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    public boolean updateStatus(String auctionId, String newStatus) {
        String sql = "UPDATE auctions SET status = ? WHERE auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setString(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái phiên: " + e.getMessage());
            return false;
        }
    }

    public int cancelAuction(String auctionId, String requesterId, boolean isAdmin) {
        String selectSql = """
                SELECT i.seller_id, a.status
                  FROM auctions a
                  INNER JOIN items i ON a.item_id = i.item_id
                 WHERE a.auction_id = ?
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return -2;

            String ownerId = rs.getString("seller_id");
            String status  = rs.getString("status");

            if (!isAdmin) {
                if (!requesterId.equals(ownerId)) return 0;
                if (!"OPEN".equals(status) && !"RUNNING".equals(status)) return -1;
            } else {
                if ("CANCELED".equals(status) || "PAID".equals(status)) return -1;
            }

            String updateSql = "UPDATE auctions SET status = 'CANCELED' WHERE auction_id = ?";
            try (PreparedStatement uStmt = conn.prepareStatement(updateSql)) {
                uStmt.setString(1, auctionId);
                return uStmt.executeUpdate() > 0 ? 1 : -2;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi hủy phiên: " + e.getMessage());
            return -2;
        }
    }

    public String[] findWinnerInfo(String auctionId) {
        String sql = """
                SELECT  bt.bidder_id   AS winner_id,
                        i.seller_id    AS seller_id,
                        bt.bid_amount  AS final_price
                  FROM auctions a
                  INNER JOIN items i ON a.item_id = i.item_id
                  INNER JOIN bid_transactions bt ON bt.auction_id = a.auction_id
                 WHERE a.auction_id = ?
                   AND a.status     = 'FINISHED'
                 ORDER BY bt.bid_amount DESC, bt.bid_time ASC
                 LIMIT 1
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new String[] {
                        rs.getString("winner_id"),
                        rs.getString("seller_id"),
                        String.valueOf(rs.getDouble("final_price"))
                };
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tra thông tin người thắng: " + e.getMessage());
        }
        return null;
    }

    public boolean markAsPaid(String auctionId) {
        String sql = "UPDATE auctions SET status = 'PAID' WHERE auction_id = ? AND status = 'FINISHED'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi đánh dấu PAID: " + e.getMessage());
            return false;
        }
    }

    public List<Auction> findAll() {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Auction auction = mapResultSetToAuction(rs);
                if (auction != null) auctions.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách tất cả phiên: " + e.getMessage());
        }
        return auctions;
    }

    public Auction findById(String auctionId) {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapResultSetToAuction(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi tìm phiên theo ID: " + e.getMessage());
        }
        return null;
    }

    public java.util.Set<String> getAuctionIdsBySeller(String sellerId) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        String sql = """
                SELECT a.auction_id
                FROM auctions a
                INNER JOIN items i ON a.item_id = i.item_id
                WHERE i.seller_id = ?
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sellerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("auction_id"));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách phiên của seller: " + e.getMessage());
        }
        return ids;
    }

    private Auction mapResultSetToAuction(ResultSet rs) throws SQLException {
        String auctionId = rs.getString("auction_id");
        String itemId    = rs.getString("item_id");
        String status    = rs.getString("status");
        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
        LocalDateTime endTime   = rs.getTimestamp("end_time").toLocalDateTime();

        var item = itemDAO.findById(itemId);
        if (item == null) return null;

        Auction auction = new Auction(auctionId, item, startTime, endTime);
        auction.setStatus(status);

        database.BidTransactionDAO bidDAO = new database.BidTransactionDAO();
        auction.restoreBidHistory(bidDAO.findTransactionsByAuctionId(auctionId));
        return auction;
    }
}
