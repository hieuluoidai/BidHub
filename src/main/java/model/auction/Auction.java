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
import java.util.concurrent.CopyOnWriteArrayList;

public class Auction extends Entity implements Serializable, Subject {
    private static final long serialVersionUID = 2L;

    // ================================================================
    // ANTI-SNIPING (Gia hạn phiên đấu giá) — yêu cầu 3.2.3
    // Nếu có bid hợp lệ trong X giây cuối → tự động gia hạn thêm Y giây.
    // ================================================================

    /** X — Ngưỡng "phút cuối" tính bằng giây. */
    private static volatile long antiSnipeThresholdSeconds = 10;

    /** Y — Số giây cộng thêm mỗi lần gia hạn. */
    private static volatile long antiSnipeExtensionSeconds = 60;

    /** Bật/tắt tính năng (mặc định BẬT). */
    private static volatile boolean antiSnipeEnabled = true;

    private Item item;
    private List<BidTransaction> bidHistory;
    private BidTransaction highestBid;
    private String status;
    private String sellerId; // ID của chủ sở hữu phiên
    private LocalDateTime startTime;
    // volatile: endTime bị sửa bởi thread xử lý bid nhưng được đọc bởi
    // thread AutoClosureService (monitor khác) — cần đảm bảo thấy giá trị mới.
    private volatile LocalDateTime endTime;

    /** Số lần phiên đã được gia hạn do anti-sniping. */
    private int extensionCount = 0;

    // Danh sách observer đăng ký theo dõi phiên này.
    private transient List<Observer> observers;

    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item      = item;
        this.startTime = startTime;
        this.endTime   = endTime;
        this.status    = "OPEN";
        this.bidHistory = new CopyOnWriteArrayList<>();
        this.observers  = new ArrayList<>();
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.observers = new ArrayList<>();
        // Đảm bảo sau khi deserialization, list vẫn là thread-safe
        if (!(this.bidHistory instanceof CopyOnWriteArrayList)) {
            this.bidHistory = new CopyOnWriteArrayList<>(this.bidHistory);
        }
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
        List<Observer> snapshot = new ArrayList<>(observers);
        for (Observer o : snapshot) {
            o.update(message);
        }
    }

    public synchronized void placeBid(User bidder, double amount)
            throws IllegalStateException, IllegalArgumentException {
        placeBid(bidder, amount, BidTransaction.BidType.MANUAL);
    }

    public synchronized void placeBid(User bidder, double amount, BidTransaction.BidType bidType)
            throws IllegalStateException, IllegalArgumentException {

        if (!"RUNNING".equals(this.status)) {
            throw new AuctionClosedException(getAuctionId(), this.status);
        }

        double currentPrice = getCurrentPrice();
        if (amount <= currentPrice) {
            throw new InvalidBidException(amount, currentPrice);
        }

        BidTransaction newBid = new BidTransaction(bidder, amount, bidType);
        this.highestBid = newBid;
        this.bidHistory.add(newBid);

        boolean extended = applyAntiSnipingIfNeeded(newBid.getTimestamp());

        String msg = String.format("[BID] %s đặt $%.2f cho phiên %s",
                bidder != null ? bidder.getUsername() : "Ẩn danh",
                amount,
                getAuctionId()
        );
        notifyObservers(msg);

        if (extended) {
            notifyObservers(String.format(
                    "[EXTENDED] Phiên %s được gia hạn (lần %d) — kết thúc mới: %s",
                    getAuctionId(), extensionCount, endTime));
        }

        System.out.println(">>> Bid thành công: "
                + (bidder != null ? bidder.getUsername() : "?") + " trả $" + amount
                + (extended ? "  [ANTI-SNIPE → endTime mới: " + endTime + "]" : ""));
    }

    private boolean applyAntiSnipingIfNeeded(LocalDateTime bidTime) {
        if (!antiSnipeEnabled)            return false;
        if (endTime == null)             return false;
        if (bidTime == null)             bidTime = LocalDateTime.now();

        if (!bidTime.isBefore(endTime))  return false;

        long secondsLeft = java.time.Duration.between(bidTime, endTime).getSeconds();

        if (secondsLeft <= antiSnipeThresholdSeconds) {
            this.endTime = this.endTime.plusSeconds(antiSnipeExtensionSeconds);
            this.extensionCount++;
            return true;
        }
        return false;
    }

    public void setStatus(String status) {
        this.status = status;
        notifyObservers("[STATUS] Phiên " + getAuctionId() + " → " + status);
    }

    public double getCurrentPrice() {
        return (highestBid != null) ? highestBid.getBidAmount() : item.getStartingPrice();
    }

    public void setHighestBid(BidTransaction highestBid) {
        this.highestBid = highestBid;
    }

    public String getAuctionId() {
        return super.getId();
    }

    public Item getItem() {
        return item;
    }

    public String getItemName() {
        return item != null ? item.getItemName() : "N/A";
    }

    public String getStatus() {
        return status;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String id) {
        this.sellerId = id;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getExtensionCount() {
        return extensionCount;
    }

    public static long getAntiSnipeThresholdSeconds() {
        return antiSnipeThresholdSeconds;
    }

    public static long getAntiSnipeExtensionSeconds() {
        return antiSnipeExtensionSeconds;
    }

    public static boolean isAntiSnipeEnabled() {
        return antiSnipeEnabled;
    }

    public static void configureAntiSnipe(long thresholdSeconds, long extensionSeconds) {
        if (thresholdSeconds <= 0 || extensionSeconds <= 0) {
            throw new IllegalArgumentException(
                    "X và Y phải lớn hơn 0 (nhận X=" + thresholdSeconds
                            + ", Y=" + extensionSeconds + ")");
        }
        antiSnipeThresholdSeconds = thresholdSeconds;
        antiSnipeExtensionSeconds = extensionSeconds;
    }

    public static void setAntiSnipeEnabled(boolean enabled) {
        antiSnipeEnabled = enabled;
    }

    public List<BidTransaction> getBidHistory() {
        return new ArrayList<>(bidHistory);
    }

    public BidTransaction getHighestBid() {
        return highestBid;
    }

    public void restoreBidHistory(List<BidTransaction> transactions) {
        this.bidHistory = new CopyOnWriteArrayList<>();
        this.highestBid = null;

        if (transactions == null || transactions.isEmpty()) return;

        this.bidHistory.addAll(transactions);
        for (BidTransaction transaction : transactions) {
            if (transaction == null) continue;
            if (this.highestBid == null
                    || transaction.getBidAmount() > this.highestBid.getBidAmount()) {
                this.highestBid = transaction;
            }
        }
    }
    public String getDuration() {
        return "Từ " + startTime.toLocalDate() + " đến " + endTime.toLocalDate();
    }
}
