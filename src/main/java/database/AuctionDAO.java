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
    // Khai báo ItemDAO
    private final ItemDAO itemDAO;

    /**
     * Khởi tạo kết nối DB và khởi tạo ItemDAO.
     */
    public AuctionDAO() {
        this.conn    = DatabaseConnection.getInstance().getConnection();
        this.itemDAO = new ItemDAO();
    }

    /**
     * Cập nhật thời gian bắt đầu và kết thúc của một phiên đấu giá.
     * Chỉ áp dụng được khi phiên đang ở trạng thái OPEN (chưa bắt đầu).
     */
    public boolean updateTime(String auctionId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = """
                UPDATE auctions
                   SET start_time = ?, end_time = ?
                 WHERE auction_id = ?
                   AND status     = 'OPEN'
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
     * Xóa một phiên đấu giá.
     * Chiến lược: xóa Item tương ứng → DB tự ON DELETE CASCADE xóa luôn auction + bid_transactions.
     * Nhờ vậy mọi dữ liệu liên quan được dọn sạch chỉ trong 1 transaction.
     */
    public boolean delete(String auctionId) {
        // Bước 1: Tra item_id tương ứng (để dùng cho DELETE cascade)
        String itemId = null;
        String sqlFindItem = "SELECT item_id FROM auctions WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sqlFindItem)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) itemId = rs.getString("item_id");
        } catch (SQLException e) {
            System.err.println("Lỗi tìm item_id của phiên: " + e.getMessage());
            return false;
        }

        if (itemId == null) {
            System.err.println("Không tìm thấy phiên đấu giá: " + auctionId);
            return false;
        }

        // Bước 2: Xóa item — FK ON DELETE CASCADE sẽ tự xóa auction + bid_transactions
        return itemDAO.delete(itemId);
    }

    /**
     * Phiên bản delete an toàn dành cho Seller: chỉ cho phép xóa nếu seller này là chủ
     * VÀ phiên đang ở trạng thái OPEN (chưa có ai đấu giá).
     * Trả về:
     *  -  1: xóa thành công
     *  -  0: không có quyền (không phải chủ phiên)
     *  - -1: phiên không ở trạng thái OPEN
     *  - -2: lỗi DB hoặc không tồn tại
     */
    public int deleteIfOwnerAndOpen(String auctionId, String sellerId) {
        String sql = """
                SELECT i.seller_id, a.status
                  FROM auctions a
                  INNER JOIN items i ON a.item_id = i.item_id
                 WHERE a.auction_id = ?
                """;
        String ownerId = null, status = null;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                ownerId = rs.getString("seller_id");
                status  = rs.getString("status");
            } else {
                return -2; // Không tồn tại
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra quyền xóa: " + e.getMessage());
            return -2;
        }

        if (!sellerId.equals(ownerId)) return 0;
        if (!"OPEN".equals(status))    return -1;

        return delete(auctionId) ? 1 : -2;
    }

    /**
     * Xóa phiên nếu seller là chủ — không giới hạn status.
     * Trả về 1 nếu xóa thành công, 0 nếu không phải chủ, -2 nếu lỗi.
     */
    public int deleteIfOwner(String auctionId, String sellerId) {
        String sql = """
                SELECT i.seller_id
                  FROM auctions a
                  INNER JOIN items i ON a.item_id = i.item_id
                 WHERE a.auction_id = ?
                """;
        String ownerId = null;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) ownerId = rs.getString("seller_id");
            else return -2;
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra quyền xóa: " + e.getMessage());
            return -2;
        }
        if (!sellerId.equals(ownerId)) return 0;
        return delete(auctionId) ? 1 : -2;
    }

    /**
     * Lưu một phiên đấu giá mới vào hệ thống.
     */
    public boolean save(Auction auction) {
        // Chỉ lưu item_id chứ không lưu toàn bộ thông tin món hàng vào bảng
        String sql = """
                INSERT INTO auctions (auction_id, item_id, status, start_time, end_time)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auction.getAuctionId());
            stmt.setString(2, auction.getItem().getItemId()); // Get ID từ đối tượng Item
            stmt.setString(3, auction.getStatus());

            // Ép kiểu: Đổi từ LocalDateTime của Java sang Timestamp của MySQL
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
     * Cập nhật trạng thái của một phiên đấu giá trong DB.
     * Được gọi bởi AuctionManager khi phiên chuyển OPEN→RUNNING hoặc RUNNING→FINISHED.
     */
    public boolean updateStatus(String auctionId, String newStatus) {
        String sql = "UPDATE auctions SET status = ? WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setString(2, auctionId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Hủy phiên (CANCEL): cập nhật status sang 'CANCELED'.
     * Quy tắc:
     *   - Admin: cho phép hủy ở mọi trạng thái (trừ chính CANCELED/PAID)
     *   - Seller: chỉ cho hủy phiên thuộc về mình và đang OPEN hoặc RUNNING
     *
     * @param requesterId ID người yêu cầu hủy
     * @param isAdmin     true nếu requester là Admin
     * @return  1 nếu hủy thành công
     *          0 nếu không có quyền (không phải chủ phiên)
     *         -1 nếu trạng thái không cho phép hủy
     *         -2 nếu phiên không tồn tại / lỗi DB
     */
    public int cancelAuction(String auctionId, String requesterId, boolean isAdmin) {
        // 1. Lấy thông tin phiên + chủ sở hữu
        String selectSql = """
                SELECT i.seller_id, a.status
                  FROM auctions a
                  INNER JOIN items i ON a.item_id = i.item_id
                 WHERE a.auction_id = ?
                """;
        String ownerId = null, status = null;
        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                ownerId = rs.getString("seller_id");
                status  = rs.getString("status");
            } else {
                return -2;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tra phiên để hủy: " + e.getMessage());
            return -2;
        }

        // 2. Kiểm tra quyền
        if (!isAdmin) {
            if (!requesterId.equals(ownerId)) return 0;
            // Seller chỉ được hủy OPEN hoặc RUNNING
            if (!"OPEN".equals(status) && !"RUNNING".equals(status)) return -1;
        } else {
            // Admin không được hủy phiên đã CANCELED hoặc đã PAID
            if ("CANCELED".equals(status) || "PAID".equals(status)) return -1;
        }

        // 3. Update sang CANCELED
        String updateSql = "UPDATE auctions SET status = 'CANCELED' WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, auctionId);
            return stmt.executeUpdate() > 0 ? 1 : -2;
        } catch (SQLException e) {
            System.err.println("Lỗi hủy phiên: " + e.getMessage());
            return -2;
        }
    }

    /**
     * Lấy thông tin người thắng phiên đã FINISHED:
     *   - winnerId: ID của bidder có giá cao nhất
     *   - sellerId: ID của chủ sở hữu sản phẩm
     *   - finalPrice: giá thắng cuộc
     *
     * @return mảng [winnerId, sellerId, finalPrice as String] hoặc null nếu
     *         phiên chưa FINISHED, hoặc chưa có ai bid
     */
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
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    /**
     * Đánh dấu phiên đã PAID. Chỉ áp dụng được khi phiên đang ở FINISHED.
     */
    public boolean markAsPaid(String auctionId) {
        String sql = "UPDATE auctions SET status = 'PAID' " +
                     " WHERE auction_id = ? AND status = 'FINISHED'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi đánh dấu PAID: " + e.getMessage());
            return false;
        }
    }


    /**
     * Lấy toàn bộ danh sách phiên đấu giá
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

        // 2. Dùng itemDAO để tìm món hàng có ID tương ứng trong bảng items
        var item = itemDAO.findById(itemId);

        if (item == null) {
            System.err.println("Cảnh báo dữ liệu: Không tìm thấy món hàng " + itemId + " cho phiên " + auctionId);
            return null;
        }

        // 3. Ghép nối Sản phẩm và Thời gian lại thành một Phiên đấu giá
        Auction auction = new Auction(auctionId, item, startTime, endTime);
        auction.setStatus(status);

        database.BidTransactionDAO bidDAO = new database.BidTransactionDAO();
        // Gọi hàm findWinner để lấy [bidder_id, bid_amount]
        String[] winnerData = bidDAO.findWinner(auctionId);

        if (winnerData != null) {
            String bidderId = winnerData[0];
            double amount = Double.parseDouble(winnerData[1]);

            // Tìm đối tượng User từ Database dựa vào bidderId
            database.UserDAO userDAO = new database.UserDAO();
            model.user.User highestBidder = userDAO.findById(bidderId);

            if (highestBidder != null) {
                // Tái tạo lại đối tượng BidTransaction từ dữ liệu nạp lên
                model.auction.BidTransaction restoredBid = new model.auction.BidTransaction(highestBidder, amount);

                // Bơm lại dữ liệu này vào phiên đấu giá
                auction.setHighestBid(restoredBid);
            }
        }
        // =========================================================================

        return auction;
    }

    public int countBySellerId(String sellerId) {
        // Đếm số phiên của một seller
        String sql = """
            SELECT COUNT(*) FROM auctions a
            INNER JOIN items i ON a.item_id = i.item_id
            WHERE i.seller_id = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sellerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm phiên theo seller: " + e.getMessage());
        }
        return 0;
    }

    public java.util.Set<String> getAuctionIdsBySeller(String sellerId) {
        // Trả về tập ID các phiên thuộc seller này
        java.util.Set<String> ids = new java.util.HashSet<>();
        String sql = """
                SELECT a.auction_id
                FROM auctions a
                INNER JOIN items i ON a.item_id = i.item_id
                WHERE i.seller_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
}