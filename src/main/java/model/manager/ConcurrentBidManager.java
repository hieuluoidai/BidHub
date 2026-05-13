package model.manager;

import database.BidTransactionDAO;
import model.auction.Auction;
import model.auction.BidResult;
import model.user.User;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bộ điều phối bid đồng thời — SINGLE POINT OF SYNCHRONIZATION cho mọi bid.
 * Đảm bảo tính Thread-safe và hiệu năng cao bằng cơ chế Lock Per-Auction.
 */
public class ConcurrentBidManager {

    private static volatile ConcurrentBidManager instance;
    private final ConcurrentHashMap<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    // ====== Metrics – an toàn đa luồng nhờ Atomic ======
    private final AtomicLong successCount    = new AtomicLong();
    private final AtomicLong outbidCount     = new AtomicLong();
    private final AtomicLong failureCount    = new AtomicLong();
    private final AtomicLong contentionCount = new AtomicLong();

    private ConcurrentBidManager() {}

    /** Singleton thread-safe (double-checked locking + volatile) cho hiệu năng tối ưu. */
    public static ConcurrentBidManager getInstance() {
        if (instance == null) {
            synchronized (ConcurrentBidManager.class) {
                if (instance == null) instance = new ConcurrentBidManager();
            }
        }
        return instance;
    }

    /** Lấy hoặc tạo mới lock riêng cho từng auctionId (Fair lock). */
    private ReentrantLock getLock(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
    }

    /** Bản test/offline (không có DB). */
    public BidResult processBid(String auctionId, double amount, User bidder) {
        return processBid(auctionId, amount, bidder, null);
    }

    /**
     * Bản production – Xử lý bid với đầy đủ cơ chế Double-check và lưu Database.
     */
    public BidResult processBid(String auctionId, double amount, User bidder,
                                BidTransactionDAO bidDao) {
        AuctionManager manager = AuctionManager.getInstance();
        Auction auction = manager.getAuctionById(auctionId);

        if (auction == null) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount, "Phiên đấu giá không tồn tại!");
        }

        // ===== 1. Optimistic pre-check (NGOÀI lock để lọc nhanh) =====
        if (!"RUNNING".equals(auction.getStatus())) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount,
                    "Phiên không đang diễn ra (Trạng thái: " + auction.getStatus() + ")");
        }
        if (amount <= auction.getCurrentPrice()) {
            outbidCount.incrementAndGet();
            return BidResult.outbid(auctionId, amount, auction.getCurrentPrice(),
                    String.format("Giá $%.2f không cao hơn giá hiện tại $%.2f.",
                            amount, auction.getCurrentPrice()));
        }

        // ===== 2. Critical section (lock per-auction) =====
        ReentrantLock lock = getLock(auctionId);
        if (lock.isLocked()) contentionCount.incrementAndGet();

        long t0 = System.nanoTime();
        lock.lock();
        try {
            // Double-check vì trạng thái có thể đã thay đổi trong lúc chờ lock
            auction = manager.getAuctionById(auctionId);
            if (auction == null) {
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, "Phiên đấu giá không tồn tại!");
            }
            if (!"RUNNING".equals(auction.getStatus())) {
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, "Phiên đã kết thúc trong lúc bạn chờ!");
            }
            
            double currentPrice = auction.getCurrentPrice();
            if (amount <= currentPrice) {
                outbidCount.incrementAndGet();
                return BidResult.outbid(auctionId, amount, currentPrice,
                        String.format("Bạn đã bị vượt giá! Giá hiện tại $%.2f, giá bạn $%.2f.",
                                currentPrice, amount));
            }

            // ===== 2b. Check balance: bidder phải có đủ tiền để cam kết bid =====
            // Tính tổng các bid đang dẫn đầu của bidder ở các phiên RUNNING khác
            // (không tính phiên hiện tại — bid mới ở phiên này sẽ thay thế bid cũ).
            // Yêu cầu: balance >= other_commitment + amount.
            //
            // Note: chỉ check khi có bidDao thật (production). Trong unit test
            // (ConcurrentBidStressTest), bidDao = null → bỏ qua check để test
            // tập trung vào logic concurrency, không phụ thuộc DB.
            if (bidDao != null) {
                database.UserDAO userDao = new database.UserDAO();
                double balance = userDao.getBalance(bidder.getUserId());
                if (balance < 0) {
                    failureCount.incrementAndGet();
                    return BidResult.failure(auctionId, amount, "Không đọc được số dư của bạn!");
                }

                double otherCommitment = bidDao.getTopBidCommitment(
                        bidder.getUserId(), auctionId);
                double required = otherCommitment + amount;

                if (balance < required) {
                    failureCount.incrementAndGet();
                    String detail;
                    if (otherCommitment > 0) {
                        detail = String.format(
                                "Bạn cần $%.2f (đang dẫn đầu $%.2f ở phiên khác + bid này $%.2f), " +
                                        "nhưng số dư chỉ có $%.2f. Hãy nạp thêm tiền hoặc bid thấp hơn.",
                                required, otherCommitment, amount, balance);
                    } else {
                        detail = String.format(
                                "Số dư không đủ! Cần $%.2f, bạn có $%.2f. Hãy nạp thêm tiền.",
                                amount, balance);
                    }
                    return BidResult.failure(auctionId, amount, detail);
                }
            }


            // ===== 3. Cập nhật dữ liệu trên bộ nhớ (Memory update) =====
            try {
                auction.placeBid(bidder, amount);
            } catch (IllegalArgumentException | IllegalStateException e) {
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, e.getMessage());
            }

            // ===== 4. Lưu vào DB (Đảm bảo tính nhất quán) =====
            if (bidDao != null) {
                boolean saved = bidDao.save(auctionId, bidder.getUserId(), amount);
                if (!saved) {
                    System.err.println(">>> [WARN] Bid memory thành công nhưng DB lưu thất bại " +
                            "cho phiên " + auctionId);
                }
            }

            successCount.incrementAndGet();
            long dt = System.nanoTime() - t0;
            System.out.printf(">>> [LOCK SUCCESS] phiên %s | thread=%s | giữ lock %.2fms%n",
                    auctionId, Thread.currentThread().getName(), dt / 1_000_000.0);

            return BidResult.success(auctionId, amount, bidder.getUsername());

        } finally {
            lock.unlock();
        }
    }

    /** Giải phóng lock khi phiên đấu giá kết thúc. */
    public void releaseLock(String auctionId) {
        auctionLocks.remove(auctionId);
    }

    public int activeLockCount() { 
        return auctionLocks.size(); 
    }

    // ===== Metrics getters (Dùng cho báo cáo/demo bảo vệ đồ án) =====
    public long getSuccessCount()    { return successCount.get(); }
    public long getOutbidCount()     { return outbidCount.get(); }
    public long getFailureCount()    { return failureCount.get(); }
    public long getContentionCount() { return contentionCount.get(); }

    public String metricsSummary() {
        return String.format("Bids → SUCCESS=%d, OUTBID=%d, FAILURE=%d, contention=%d",
                successCount.get(), outbidCount.get(), failureCount.get(), contentionCount.get());
    }

    /** Reset metrics — chỉ dùng cho Unit Test. */
    public void resetMetrics() {
        successCount.set(0);
        outbidCount.set(0);
        failureCount.set(0);
        contentionCount.set(0);
    }
}