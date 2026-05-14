package model.auction;

import model.core.Entity;
import model.core.Observer;
import model.core.Subject;
import model.item.Item;
import model.user.User;
import exception.AuctionClosedException;
import exception.InvalidBidException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity implements Serializable, Subject {
    private static final long serialVersionUID = 1L;

    private Item item;
    private List<BidTransaction> bidHistory;
    private BidTransaction highestBid;
    private String status;
    private String sellerId; // ID của chủ sở hữu phiên
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Danh sách observer đăng ký theo dõi phiên này.
    private transient List<Observer> observers;

    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item      = item;
        this.startTime = startTime;
        this.endTime   = endTime;
        this.status    = "OPEN";
        this.bidHistory = new ArrayList<>();
        this.observers  = new ArrayList<>();
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.observers = new ArrayList<>();
    }

    // Subject interface
    @Override
    public void attach(Observer observer) {
        if (observers == null) observers = new ArrayList<>();
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void detach(Observer observer) {
        if (observers != null) observers.remove(observer);
    }

    @Override
    public void notifyObservers(String message) {
        if (observers == null) return;
        // Tạo bản sao để tránh ConcurrentModificationException
        // nếu observer tự detach trong lúc nhận thông báo
        List<Observer> snapshot = new ArrayList<>(observers);
        for (Observer o : snapshot) {
            o.update(message);
        }
    }

    // ----------------------------------------------------------------
    // placeBid — gọi notifyObservers sau khi bid thành công
    // ----------------------------------------------------------------

    public synchronized void placeBid(User bidder, double amount)
            throws IllegalStateException, IllegalArgumentException {

        if (!"RUNNING".equals(this.status)) {
            throw new AuctionClosedException(getAuctionId(), this.status);
        }

        double currentPrice = getCurrentPrice();
        if (amount <= currentPrice) {
            throw new InvalidBidException(amount, currentPrice);
        }

        BidTransaction newBid = new BidTransaction(bidder, amount);
        this.highestBid = newBid;
        this.bidHistory.add(newBid);

        // Thông báo tới tất cả observer đang theo dõi phiên này
        String msg = String.format("[BID] %s đặt $%.2f cho phiên %s",
            bidder != null ? bidder.getUsername() : "Ẩn danh",
            amount,
            getAuctionId()
        );
        notifyObservers(msg);

        System.out.println(">>> Bid thành công: "
            + (bidder != null ? bidder.getUsername() : "?") + " trả $" + amount);
    }

    // Khi chuyển trạng thái cũng notify
    public void setStatus(String status) {
        this.status = status;
        notifyObservers("[STATUS] Phiên " + getAuctionId() + " → " + status);
    }

    // Getters
    
    public double getCurrentPrice() {
        return (highestBid != null) ? highestBid.getBidAmount() : item.getStartingPrice();
    }

    public void setHighestBid(BidTransaction highestBid) { this.highestBid = highestBid; }
    public String getAuctionId()        { return super.getId(); }
    public Item getItem()               { return item; }
    public String getItemName()         { return item != null ? item.getItemName() : "N/A"; }
    public String getStatus()           { return status; }
    public String getSellerId()         { return sellerId; }
    public void setSellerId(String id)  { this.sellerId = id; }
    public LocalDateTime getEndTime()   { return endTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public List<BidTransaction> getBidHistory() { return new ArrayList<>(bidHistory); }
    public BidTransaction getHighestBid()       { return highestBid; }
    public String getDuration() {
        return "Từ " + startTime.toLocalDate() + " đến " + endTime.toLocalDate();
    }
}