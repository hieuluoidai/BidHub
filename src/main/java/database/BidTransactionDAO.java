package database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp chịu trách nhiệm ghi chép và truy xuất các Bid Transactions từ MySQL.
 */
public class BidTransactionDAO {

    private final Connection conn;

    /**
     * Khởi tạo kết nối đến cơ sở dữ liệu.
     */
    public BidTransactionDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Ghi lại một BidTransaction mới vào hệ thống.
     */
    public boolean save(String auctionId, String bidderId, double amount) {
        // Sử dụng hàm NOW() của MySQL để lấy chính xác thời gian máy chủ tại thời điểm chèn dữ liệu
        String sql = """
                INSERT INTO bid_transactions (transaction_id, auction_id, bidder_id, bid_amount, bid_time)
                VALUES (?, ?, ?, ?, NOW())
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Tạo một ID duy nhất và ngẫu nhiên (UUID) để đảm bảo không bao giờ bị trùng lặp giao dịch
            stmt.setString(1, java.util.UUID.randomUUID().toString());
            stmt.setString(2, auctionId);
            stmt.setString(3, bidderId);
            stmt.setDouble(4, amount);
            
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu lịch sử đặt giá: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy toàn bộ lịch sử trả giá của một phiên đấu giá để vẽ biểu đồ diễn biến.
     * Dữ liệu được sắp xếp theo thời gian từ cũ nhất đến mới nhất.
     */
    public List<double[]> findBidHistoryByAuctionId(String auctionId) {
        List<double[]> history = new ArrayList<>();
        // Lấy giá tiền và thời gian, sắp xếp theo trình tự thời gian (ASC)
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
                // Đổi thời gian Timestamp của SQL ra số mili-giây (long) để dễ dàng đưa vào thư viện vẽ biểu đồ
                long   timestamp = rs.getTimestamp("bid_time").getTime();
                
                // Mỗi phần tử trong danh sách là một mảng gồm 2 thông số: [Mức giá, Thời điểm]
                history.add(new double[]{amount, timestamp});
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tải lịch sử giá: " + e.getMessage());
        }
        return history;
    }

    /**
     * Tìm ra người chiến thắng của một phiên đấu giá đã kết thúc.
     */
    public String[] findWinner(String auctionId) {
        // Xếp người trả giá cao nhất lên đầu.
        // Nếu trùng giá, ai bid trước thì xếp trên.
        // Chỉ lấy đúng 1 người đứng trên cùng danh sách.
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
                // Trả về mảng chứa [ID người thắng, Mức giá thắng cuộc]
                return new String[]{
                        rs.getString("bidder_id"),
                        String.valueOf(rs.getDouble("bid_amount"))
                };
            }
        } catch (SQLException e) {
            System.err.println("Lỗi xác định người chiến thắng: " + e.getMessage());
        }
        return null; // Return null nếu phiên này chưa ai bid.
    }

    /**
     * Đếm tổng số lượt đã bid trong một phiên cụ thể.
     */
    public int countBidsByAuctionId(String auctionId) {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) return rs.getInt(1); // Lấy kết quả của hàm COUNT(*)
        } catch (SQLException e) {
            System.err.println("Lỗi đếm số lượt đặt giá: " + e.getMessage());
        }
        return 0;
    }
    
    public int countBidsByBidderId(String bidderId) {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE bidder_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm bid: " + e.getMessage());
        }
        return 0;
    }

    public int countWinsByBidderId(String bidderId) {
        // Đếm số phiên FINISHED mà bidder này là người bid cao nhất
        String sql = """
            SELECT COUNT(*) FROM auctions a
            INNER JOIN (
                SELECT auction_id, bidder_id, bid_amount,
                       ROW_NUMBER() OVER (PARTITION BY auction_id ORDER BY bid_amount DESC) AS rn
                FROM bid_transactions
            ) bt ON a.auction_id = bt.auction_id
            WHERE bt.rn = 1 AND bt.bidder_id = ? AND a.status = 'FINISHED'
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm phiên thắng: " + e.getMessage());
        }
        return 0;
    }
    
    public int countActiveParticipations(String bidderId) {
        // Đếm số phiên RUNNING/OPEN mà bidder này đã từng bid
        String sql = """
                SELECT COUNT(DISTINCT bt.auction_id)
                FROM bid_transactions bt
                INNER JOIN auctions a ON bt.auction_id = a.auction_id
                WHERE bt.bidder_id = ?
                  AND a.status IN ('OPEN', 'RUNNING')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Lỗi đếm phiên đang tham gia: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Tính TỔNG TIỀN bidder này đang "cam kết" — tức tổng giá đang dẫn đầu
     * ở các phiên còn RUNNING.
     *
     * Logic SQL:
     *   Với mỗi phiên RUNNING, tìm bid_amount MAX của phiên đó
     *   Nếu bid cao nhất là của bidder này → cộng vào total
     *   (tie-breaker: ai bid trước thì xếp trên — giống logic findWinner)
     *
     * @param bidderId       người cần tính commitment
     * @param excludeAuction phiên cần loại trừ khỏi tính toán (vì đang định bid mới
     *                        ở phiên này, sẽ tính riêng). Truyền null nếu không loại trừ.
     * @return tổng cam kết (≥ 0)
     */
    public double getTopBidCommitment(String bidderId, String excludeAuction) {
        // Subquery lấy bid cao nhất của mỗi phiên RUNNING (xếp theo giá DESC, thời gian ASC)
        // Bên ngoài chỉ lấy những row mà top bidder == bidderId này, rồi SUM
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
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bidderId);
            // Truyền excludeAuction 2 lần (cho IS NULL check và <> check)
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