package model.auction;

import model.user.User;
import java.io.Serializable; // Bắt buộc để truyền qua Socket [cite: 126]
import java.time.LocalDateTime;

/**
 * Lớp BidTransaction lưu trữ thông tin về một lượt đặt giá[cite: 117].
 */
public class BidTransaction implements Serializable {
    // Đảm bảo đồng bộ hóa phiên bản giữa Client và Server
    private static final long serialVersionUID = 1L;

    private User bidder;
    private double bidAmount;
    private LocalDateTime timestamp;
    
    /**
     * Khởi tạo một giao dịch đặt giá mới[cite: 47].
     * @param bidder Người tham gia đặt giá.
     * @param bidAmount Số tiền đặt giá.
     */
    public BidTransaction(User bidder, double bidAmount) {
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now(); // Ghi lại thời điểm đặt giá thực tế 
    }
    
    // Getters
    public User getBidder()             { return bidder;    }
    public double getBidAmount()        { return bidAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s bid $%.2f", timestamp, (bidder != null ? bidder.getUsername() : "Guest"), bidAmount);
    }
}