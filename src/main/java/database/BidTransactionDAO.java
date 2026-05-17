package database;

import model.auction.BidTransaction;
import model.user.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp chịu trách nhiệm ghi chép và truy xuất các Bid Transactions từ MySQL
 * sử dụng Connection Pool.
 */
public class BidTransactionDAO {

    public BidTransactionDAO() {
    }

    public boolean save(String auctionId, String bidderId, double amount) {
        return save(auctionId, bidderId, amount, BidTransaction.BidType.MANUAL, LocalDateTime.now());
    }

    public boolean save(String auctionId, String bidderId, double amount, BidTransaction.BidType bidType) {
        return save(auctionId, bidderId, amount, bidType, LocalDateTime.now());
    }

    public boolean save(String auctionId, String bidderId, double amount, BidTransaction.BidType bidType, LocalDateTime bidTime) {
        String sql = """
                INSERT INTO bid_transactions
                    (transaction_id, auction_id, bidder_id, bid_amount, bid_time, bid_type)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, java.util.UUID.randomUUID().toString());
            stmt.setString(2, auctionId);
            stmt.setString(3, bidderId);
            stmt.setDouble(4, amount);
            stmt.setObject(5, bidTime != null ? bidTime : LocalDateTime.now());
            stmt.setString(6, (bidType != null ? bidType : BidTransaction.BidType.MANUAL).name());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu lịch sử đặt giá: " + e.getMessage());
            return false;
        }
    }

    public List<BidTransaction> findTransactionsByAuctionId(String auctionId) {
        List<BidTransaction> history = new ArrayList<>();
        String sql = """
                SELECT bidder_id, bid_amount, bid_time, bid_type
                FROM bid_transactions
                WHERE auction_id = ?
                ORDER BY bid_time ASC, bid_amount ASC
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            UserDAO userDAO = new UserDAO();

            while (rs.next()) {
                String bidderId = rs.getString("bidder_id");
                User bidder = userDAO.findById(bidderId);
                double amount = rs.getDouble("bid_amount");

                // JDBC 4.2: Đọc trực tiếp LocalDateTime để tránh driver tự ý convert qua lại UTC
                LocalDateTime timestamp = rs.getObject("bid_time", LocalDateTime.class);
                if (timestamp == null) timestamp = LocalDateTime.now();

                BidTransaction.BidType bidType = parseBidType(rs.getString("bid_type"));

                history.add(new BidTransaction(
                        bidder,
                        amount,
                        timestamp,
                        bidType
                ));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tải lịch sử giá cho phiên " + auctionId + ": " + e.getMessage());
        }

        return history;
    }


    public List<double[]> findBidHistoryByAuctionId(String auctionId) {
        List<double[]> history = new ArrayList<>();
        String sql = "SELECT bid_amount, bid_time FROM bid_transactions WHERE auction_id = ? ORDER BY bid_time ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LocalDateTime ts = rs.getObject("bid_time", LocalDateTime.class);
                if (ts != null) {
                    history.add(new double[]{
                            rs.getDouble("bid_amount"),
                            ts.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tải lịch sử bid rút gọn: " + e.getMessage());
        }
        return history;
    }

    private BidTransaction.BidType parseBidType(String raw) {
        try {
            return raw != null
                    ? BidTransaction.BidType.valueOf(raw)
                    : BidTransaction.BidType.MANUAL;
        } catch (IllegalArgumentException ex) {
            return BidTransaction.BidType.MANUAL;
        }
    }

    public String[] findWinner(String auctionId) {
        String sql = """
                SELECT bidder_id, bid_amount
                FROM bid_transactions
                WHERE auction_id = ?
                ORDER BY bid_amount DESC, bid_time ASC
                LIMIT 1
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new String[]{
                        rs.getString("bidder_id"),
                        String.valueOf(rs.getDouble("bid_amount"))
                };
            }
        } catch (SQLException e) {
            System.err.println("Lỗi xác định người chiến thắng: " + e.getMessage());
        }
        return null;
    }

    public int countBidsByAuctionId(String auctionId) {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm số lượt đặt giá: " + e.getMessage());
        }
        return 0;
    }

    public int countBidsByBidderId(String bidderId) {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE bidder_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm bid: " + e.getMessage());
        }
        return 0;
    }

    public int countWinsByBidderId(String bidderId) {
        String sql = """
            SELECT COUNT(*) FROM auctions a
            INNER JOIN (
                SELECT auction_id, bidder_id, bid_amount,
                       ROW_NUMBER() OVER (PARTITION BY auction_id ORDER BY bid_amount DESC) AS rn
                FROM bid_transactions
            ) bt ON a.auction_id = bt.auction_id
            WHERE bt.rn = 1 AND bt.bidder_id = ? AND a.status = 'FINISHED'
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm phiên thắng: " + e.getMessage());
        }
        return 0;
    }

    public int countActiveParticipations(String bidderId) {
        String sql = """
                SELECT COUNT(DISTINCT bt.auction_id)
                FROM bid_transactions bt
                INNER JOIN auctions a ON bt.auction_id = a.auction_id
                WHERE bt.bidder_id = ?
                  AND a.status IN ('OPEN', 'RUNNING')
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm phiên đang tham gia: " + e.getMessage());
        }
        return 0;
    }

    public double getTopBidCommitment(String bidderId, String excludeAuction) {
        String sql = """
                SELECT COALESCE(SUM(top.bid_amount), 0) AS commitment
                FROM (
                    SELECT bt.auction_id, bt.bidder_id, bt.bid_amount,
                           ROW_NUMBER() OVER (
                               PARTITION BY bt.auction_id
                               ORDER BY bt.bid_amount DESC, bt.bid_time ASC
                           ) AS rn
                    FROM bid_transactions bt
                    INNER JOIN auctions a ON bt.auction_id = a.auction_id
                    WHERE a.status = 'RUNNING'
                ) top
                WHERE top.rn = 1
                  AND top.bidder_id = ?
                  AND (? IS NULL OR top.auction_id <> ?)
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            if (excludeAuction == null) {
                stmt.setNull(2, Types.VARCHAR);
                stmt.setNull(3, Types.VARCHAR);
            } else {
                stmt.setString(2, excludeAuction);
                stmt.setString(3, excludeAuction);
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("commitment");
        } catch (SQLException e) {
            System.err.println("Lỗi tính commitment: " + e.getMessage());
        }
        return 0;
    }
}
