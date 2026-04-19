package model.auction;

import model.core.Entity;
import model.item.Item;
import model.user.User;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý cycle và logic của một phiên đấu giá.
 */
public class Auction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;

    private Item item;
    private List<BidTransaction> bidHistory;
    private BidTransaction highestBid;
    private String status; // OPEN, RUNNING, FINISHED, PAID/CANCELLED
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item = item;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "OPEN";
        this.bidHistory = new ArrayList<>();
    }

    /**
     * Logic đặt giá mới. Đảm bảo tính đa luồng
     */
    public synchronized void placeBid(User bidder, double amount) throws IllegalStateException, IllegalArgumentException {
        
        // Chỉ cho phép bid khi phiên RUNNING
        if (!"RUNNING".equals(this.status)) {
            throw new IllegalStateException("Phiên đấu giá hiện không diễn ra (Trạng thái: " + this.status + ")");
        }

        // Giá mới phải cao hơn mức giá hiện tại
        double currentPrice = getCurrentPrice(); 
        if (amount <= currentPrice) {
            throw new IllegalArgumentException("Giá đặt ($" + amount + ") phải cao hơn giá hiện tại ($" + currentPrice + ")!");
        }

        // Ghi nhận bid và update highestBid
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
        // Trả về highestBid tại hoặc startingPrice (nếu chưa có ai bid)
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
        return "Từ " + startTime.toLocalDate() + " đến " + endTime.toLocalDate();
    }
}