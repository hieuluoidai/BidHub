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
    private static final long serialVersionUID = 2L;

    // ================================================================
    // ANTI-SNIPING (Gia hạn phiên đấu giá) — yêu cầu 3.2.3
    // Nếu có bid hợp lệ trong X giây cuối → tự động gia hạn thêm Y giây.
    // Ví dụ đề: end 20:00:00, bid 19:59:50 (còn 10s) → kéo dài 20:01:00
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

        // ===== ANTI-SNIPING: gia hạn nếu bid rơi vào X giây cuối =====
        boolean extended = applyAntiSnipingIfNeeded(newBid.getTimestamp());

        // Thông báo tới tất cả observer đang theo dõi phiên này
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

    /**
     * Áp dụng thuật toán Anti-sniping.
     * Quy tắc: nếu thời điểm bid cách endTime không quá X giây → đẩy
     * endTime thêm Y giây (tính từ endTime hiện tại, đúng ví dụ đề:
     * end 20:00:00, bid 19:59:50 còn 10s → 20:00:00 + 60s = 20:01:00).
     * Gọi bên trong placeBid() (đã synchronized) nên thread-safe.
     *
     * @param bidTime thời điểm bid (lấy từ BidTransaction để nhất quán)
     * @return true nếu phiên vừa được gia hạn
     */
    private boolean applyAntiSnipingIfNeeded(LocalDateTime bidTime) {
        if (!antiSnipeEnabled)            return false;
        if (endTime == null)             return false;
        if (bidTime == null)             bidTime = LocalDateTime.now();

        // Bid đến SAU thời điểm kết thúc → không gia hạn.
        if (!bidTime.isBefore(endTime))  return false;

        long secondsLeft = java.time.Duration.between(bidTime, endTime).getSeconds();

        if (secondsLeft <= antiSnipeThresholdSeconds) {
            this.endTime = this.endTime.plusSeconds(antiSnipeExtensionSeconds);
            this.extensionCount++;
            return true;
        }
        return false;
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

    /**
     * Cập nhật end_time (dùng khi đồng bộ từ DB hoặc Seller sửa phiên
     * lúc còn OPEN). Không tự notify; nơi gọi tự quyết định broadcast.
     */
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    /** Số lần phiên đã được gia hạn bởi anti-sniping. */
    public int getExtensionCount() { return extensionCount; }

    // ---- Cấu hình Anti-sniping (static, dùng chung toàn hệ thống) ----

    public static long getAntiSnipeThresholdSeconds() { return antiSnipeThresholdSeconds; }
    public static long getAntiSnipeExtensionSeconds() { return antiSnipeExtensionSeconds; }
    public static boolean isAntiSnipeEnabled()        { return antiSnipeEnabled; }

    /**
     * Thiết lập tham số anti-sniping.
     * @param thresholdSeconds X — ngưỡng giây cuối (phải > 0)
     * @param extensionSeconds Y — số giây gia hạn mỗi lần (phải > 0)
     */
    public static void configureAntiSnipe(long thresholdSeconds, long extensionSeconds) {
        if (thresholdSeconds <= 0 || extensionSeconds <= 0) {
            throw new IllegalArgumentException(
                    "X và Y phải lớn hơn 0 (nhận X=" + thresholdSeconds
                            + ", Y=" + extensionSeconds + ")");
        }
        antiSnipeThresholdSeconds = thresholdSeconds;
        antiSnipeExtensionSeconds = extensionSeconds;
    }

    public static void setAntiSnipeEnabled(boolean enabled) { antiSnipeEnabled = enabled; }
    public List<BidTransaction> getBidHistory() { return new ArrayList<>(bidHistory); }
    public BidTransaction getHighestBid()       { return highestBid; }
    public String getDuration() {
        return "Từ " + startTime.toLocalDate() + " đến " + endTime.toLocalDate();
    }
}