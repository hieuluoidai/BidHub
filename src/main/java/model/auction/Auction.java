package model.auction;

import model.core.Entity;
import model.item.Item;
import model.user.User;
import java.io.Serializable; // Bắt buộc để chạy Client-Server 
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Class Auction quản lý trung tâm một phiên đấu giá.
 * Triển khai Serializable để truyền dữ liệu qua Socket.
 */
public class Auction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L; // Đảm bảo đồng bộ giữa Client và Server

    private Item item;
    private List<BidTransaction> bidHistory;
    private BidTransaction highestBid;
    private String status; 
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // KHÔNG dùng DoubleProperty ở đây vì nó không Serializable.
    // Việc update giao diện realtime đã có AppState và ObservableList lo (Observer Pattern)[cite: 7, 143].

    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item = item;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "OPEN"; // Trạng thái ban đầu
        this.bidHistory = new ArrayList<>();
    }

    /**
     * Tham gia đấu giá: Kiểm tra tính hợp lệ và cập nhật người dẫn đầu.
     * Sử dụng synchronized để xử lý đấu giá đồng thời (Concurrent Bidding).
     */
    public synchronized void placeBid(User bidder, double amount) throws IllegalStateException, IllegalArgumentException {
        
        // 1. Kiểm tra trạng thái phiên: Chỉ cho phép đặt giá khi đang RUNNING.
        if (!"RUNNING".equals(this.status)) {
            throw new IllegalStateException("Phiên đấu giá không ở trạng thái RUNNING (Hiện tại: " + this.status + ") [cite: 5, 59]");
        }

        // 2. Kiểm tra tính hợp lệ: Giá đặt phải cao hơn giá hiện tại.
        double currentPrice = getCurrentPrice(); 
        if (amount <= currentPrice) {
            throw new IllegalArgumentException("Giá đặt ($" + amount + ") phải cao hơn giá hiện tại ($" + currentPrice + ")! [cite: 5, 58]");
        }

        // 3. Cập nhật giao dịch đặt giá mới.
        BidTransaction newBid = new BidTransaction(bidder, amount);
        this.highestBid = newBid;
        this.bidHistory.add(newBid);
        
        if (bidder != null) {
            System.out.println(">>> Bid thành công: " + bidder.getUsername() + " trả $" + amount);
        }
    }

    // --- Getters & Setters ---
    
    public void setStatus(String status) { this.status = status; }

    public double getCurrentPrice() {
        // Giá hiện tại cao nhất hoặc giá khởi điểm nếu chưa có ai đặt.
        return (highestBid != null) ? highestBid.getBidAmount() : item.getStartingPrice();
    }

    public String getAuctionId()     { return super.getId(); }
    public Item getItem()            { return item; }
    public String getItemName()      { return item != null ? item.getItemName() : "N/A"; }
    public String getStatus()        { return status; }
    public LocalDateTime getEndTime()   { return endTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public List<BidTransaction> getBidHistory() { return new ArrayList<>(bidHistory); }
    public BidTransaction getHighestBid() { return highestBid; }

    public String getDuration() {
        return "From " + startTime.toLocalDate() + " to " + endTime.toLocalDate();
    }
}