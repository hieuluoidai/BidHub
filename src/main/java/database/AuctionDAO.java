package database;

import model.auction.Auction;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {

    private final Connection conn;
    private final ItemDAO itemDAO; // dùng để load Item từ DB khi đọc Auction

    public AuctionDAO() {
        this.conn    = DatabaseConnection.getInstance().getConnection();
        this.itemDAO = new ItemDAO();
    }

    public boolean save(Auction auction) {
        String sql = """
                INSERT INTO auctions (auction_id, item_id, status, start_time, end_time)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auction.getAuctionId());
            stmt.setString(2, auction.getItem().getItemId());
            stmt.setString(3, auction.getStatus());
            stmt.setTimestamp(4, Timestamp.valueOf(auction.getStartTime()));
            stmt.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi save auction: " + e.getMessage());
            return false;
        }
    }

    public boolean updateStatus(String auctionId, String newStatus) {
        String sql = "UPDATE auctions SET status = ? WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setString(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi updateStatus: " + e.getMessage());
            return false;
        }
    }

    public List<Auction> findAll() {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Auction auction = mapResultSetToAuction(rs);
                if (auction != null) auctions.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi findAll auctions: " + e.getMessage());
        }
        return auctions;
    }

    public Auction findById(String auctionId) {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapResultSetToAuction(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi findById auction: " + e.getMessage());
        }
        return null;
    }

    public List<Auction> findRunning() {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'RUNNING'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Auction auction = mapResultSetToAuction(rs);
                if (auction != null) auctions.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi findRunning: " + e.getMessage());
        }
        return auctions;
    }

    private Auction mapResultSetToAuction(ResultSet rs) throws SQLException {
        String auctionId = rs.getString("auction_id");
        String itemId    = rs.getString("item_id");
        String status    = rs.getString("status");
        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
        LocalDateTime endTime   = rs.getTimestamp("end_time").toLocalDateTime();

        var item = itemDAO.findById(itemId);
        if (item == null) {
            System.err.println("Không tìm thấy item " + itemId + " cho auction " + auctionId);
            return null;
        }

        Auction auction = new Auction(auctionId, item, startTime, endTime);
        auction.setStatus(status);
        return auction;
    }
}
