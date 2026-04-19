package database;

import model.auction.Auction;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp quản lý các Auction trong cơ sở dữ liệu.
 * Chịu trách nhiệm lưu trữ thời gian, trạng thái và liên kết với Item.
 */
public class AuctionDAO {

    private final Connection conn;
    // Khai báo ItemDAO để tìm kiếm thông tin món hàng
    private final ItemDAO itemDAO; 

    /**
     * Khởi tạo kết nối DB và khởi tạo ItemDAO.
     */
    public AuctionDAO() {
        this.conn    = DatabaseConnection.getInstance().getConnection();
        this.itemDAO = new ItemDAO();
    }

    /**
     * Lưu một phiên đấu giá mới vào hệ thống.
     */
    public boolean save(Auction auction) {
        // Chỉ lưu ID của món hàng (item_id) chứ không lưu toàn bộ thông tin món hàng vào bảng
        String sql = """
                INSERT INTO auctions (auction_id, item_id, status, start_time, end_time)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auction.getAuctionId());
            stmt.setString(2, auction.getItem().getItemId()); // Trích xuất ID từ đối tượng Item
            stmt.setString(3, auction.getStatus());
            
            // Ép kiểu thời gian: Đổi từ LocalDateTime của Java sang chuẩn Timestamp của MySQL
            stmt.setTimestamp(4, Timestamp.valueOf(auction.getStartTime()));
            stmt.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
            
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái của phiên đấu giá
     */
    public boolean updateStatus(String auctionId, String newStatus) {
        String sql = "UPDATE auctions SET status = ? WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setString(2, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy toàn bộ danh sách phiên đấu giá (Sẽ dùng cho tính năng Quản lý của Admin).
     */
    public List<Auction> findAll() {
        List<Auction> auctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                // Biến đổi từng dòng dữ liệu thành đối tượng Auction
                Auction auction = mapResultSetToAuction(rs);
                if (auction != null) auctions.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách tất cả phiên: " + e.getMessage());
        }
        return auctions;
    }

    /**
     * Tìm một phiên đấu giá cụ thể dựa trên ID.
     */
    public Auction findById(String auctionId) {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapResultSetToAuction(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi tìm phiên theo ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy danh sách các phiên đang RUNNING (Hiển thị lên bảng Dashboard cho bidder).
     */
    public List<Auction> findRunning() {
        List<Auction> auctions = new ArrayList<>();
        // Chỉ lấy những phiên có trạng thái RUNNING
        String sql = "SELECT * FROM auctions WHERE status = 'RUNNING'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Auction auction = mapResultSetToAuction(rs);
                if (auction != null) auctions.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm các phiên đang chạy: " + e.getMessage());
        }
        return auctions;
    }

    /**
     * Tiện ích: Khởi tạo một đối tượng Auction hoàn chỉnh từ dữ liệu thô của MySQL.
     */
    private Auction mapResultSetToAuction(ResultSet rs) throws SQLException {
        // 1. Lấy các thông số cơ bản của bảng auctions
        String auctionId = rs.getString("auction_id");
        String itemId    = rs.getString("item_id");
        String status    = rs.getString("status");
        
        // Dịch ngược thời gian: Từ Timestamp của MySQL về lại LocalDateTime của Java
        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
        LocalDateTime endTime   = rs.getTimestamp("end_time").toLocalDateTime();

        // 2. Mấu chốt: Dùng itemDAO để tìm món hàng có ID tương ứng trong bảng items
        var item = itemDAO.findById(itemId);
        
        // Nếu database bị lỗi (có phiên đấu giá nhưng mất hàng), báo lỗi và bỏ qua
        if (item == null) {
            System.err.println("Cảnh báo dữ liệu: Không tìm thấy món hàng " + itemId + " cho phiên " + auctionId);
            return null; 
        }

        // 3. Ghép nối Sản phẩm và Thời gian lại thành một Phiên đấu giá hoàn chỉnh
        Auction auction = new Auction(auctionId, item, startTime, endTime);
        auction.setStatus(status);
        return auction;
    }
}