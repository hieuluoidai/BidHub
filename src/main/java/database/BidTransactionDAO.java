package database;

import model.auction.BidTransaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BidTransactionDAO {

    private final Connection conn;

    public BidTransactionDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    public boolean save(String auctionId, String bidderId, double amount) {
        String sql = """
                INSERT INTO bid_transactions (transaction_id, auction_id, bidder_id, bid_amount, bid_time)
                VALUES (?, ?, ?, ?, NOW())
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Tạo ID ngẫu nhiên cho transaction
            stmt.setString(1, java.util.UUID.randomUUID().toString());
            stmt.setString(2, auctionId);
            stmt.setString(3, bidderId);
            stmt.setDouble(4, amount);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi save bid transaction: " + e.getMessage());
            return false;
        }
    }

    public List<double[]> findBidHistoryByAuctionId(String auctionId) {
        List<double[]> history = new ArrayList<>();
        String sql = """
                SELECT bid_amount, bid_time
                FROM bid_transactions
                WHERE auction_id = ?
                ORDER BY bid_time ASC
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                double amount    = rs.getDouble("bid_amount");
                long   timestamp = rs.getTimestamp("bid_time").getTime();
                // Trả về mảng [amount, timestamp] để vẽ chart
                history.add(new double[]{amount, timestamp});
            }
        } catch (SQLException e) {
            System.err.println("Lỗi findBidHistory: " + e.getMessage());
        }
        return history;
    }

    public String[] findWinner(String auctionId) {
        String sql = """
                SELECT bidder_id, bid_amount
                FROM bid_transactions
                WHERE auction_id = ?
                ORDER BY bid_amount DESC, bid_time ASC
                LIMIT 1
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new String[]{
                        rs.getString("bidder_id"),
                        String.valueOf(rs.getDouble("bid_amount"))
                };
            }
        } catch (SQLException e) {
            System.err.println("Lỗi findWinner: " + e.getMessage());
        }
        return null;
    }

    public int countBidsByAuctionId(String auctionId) {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi countBids: " + e.getMessage());
        }
        return 0;
    }
}
