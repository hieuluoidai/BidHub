package model.auction;

import model.user.User;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Class cho 1 lần bid trong hệ thống.
 */
public class BidTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private User bidder;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction(User bidder, double bidAmount) {
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now(); // Lấy thời gian lúc bid
    }

    // --- Getters ---
    public User getBidder()             { return bidder; }
    public double getBidAmount()        { return bidAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // Override lại để hiển thị đầy đủ thông tin dễ nhìn hơn
    @Override
    public String toString() {
        String name = (bidder != null) ? bidder.getUsername() : "Ẩn danh";
        return String.format("[%s] %s đã trả giá $%.2f", timestamp, name, bidAmount);
    }
}