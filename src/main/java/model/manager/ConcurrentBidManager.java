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

    /** Singleton thread-safe (double-checked locking + volatile). */
    public static ConcurrentBidManager getInstance() {
        if (instance == null) {
            synchronized (ConcurrentBidManager.class) {
                if (instance == null) instance = new ConcurrentBidManager();
            }
        }
        return instance;
    }

    /**
     * Lấy lock cho auctionId
     */
    private ReentrantLock getLock(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
    }

    /**
     * Bản test (không có DB).
     */
    public BidResult processBid(String auctionId, double amount, User bidder) {
        return processBid(auctionId, amount, bidder, null);
    }

    /**
     * Bản production
     */
    public BidResult processBid(String auctionId, double amount, User bidder,
                                BidTransactionDAO bidDao) {
        AuctionManager manager = AuctionManager.getInstance();
        Auction auction = manager.getAuctionById(auctionId);

        if (auction == null) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount, "Phiên đấu giá không tồn tại!");
        }

        // ===== Optimistic pre-check (NGOÀI lock) =====
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

        // ===== Critical section (lock per-auction) =====
        ReentrantLock lock = getLock(auctionId);
        if (lock.isLocked()) contentionCount.incrementAndGet();

        long t0 = System.nanoTime();
        lock.lock();
        try {
            // Double-check vì state có thể đã đổi trong lúc chờ lock
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

            // ===== Memory update =====
            try {
                auction.placeBid(bidder, amount);
            } catch (IllegalArgumentException | IllegalStateException e) {
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, e.getMessage());
            }

            // ===== DB persist (ATOMIC với memory) =====
            if (bidDao != null) {
                boolean saved = bidDao.save(auctionId, bidder.getUserId(), amount);
                if (!saved) {
                    System.err.println(">>> [WARN] Bid memory thành công nhưng DB lưu thất bại " +
                            "cho phiên " + auctionId);
                }
            }

            successCount.incrementAndGet();
            long dt = System.nanoTime() - t0;
            System.out.printf(">>> [LOCK] phiên %s | thread=%s | giữ lock %.2fms%n",
                    auctionId, Thread.currentThread().getName(), dt / 1_000_000.0);

            return BidResult.success(auctionId, amount, bidder.getUsername());

        } finally {
            lock.unlock();
        }
    }

    /** Giải phóng lock của phiên đã FINISHED. */
    public void releaseLock(String auctionId) {
        auctionLocks.remove(auctionId);
    }

    public int activeLockCount() { return auctionLocks.size(); }

    // ===== Metrics getters (cho demo/log) =====
    public long getSuccessCount()    { return successCount.get(); }
    public long getOutbidCount()     { return outbidCount.get(); }
    public long getFailureCount()    { return failureCount.get(); }
    public long getContentionCount() { return contentionCount.get(); }

    public String metricsSummary() {
        return String.format("Bids → SUCCESS=%d, OUTBID=%d, FAILURE=%d, contention=%d",
                successCount.get(), outbidCount.get(), failureCount.get(), contentionCount.get());
    }

    /** Reset metrics — chỉ dùng cho test. */
    public void resetMetrics() {
        successCount.set(0);
        outbidCount.set(0);
        failureCount.set(0);
        contentionCount.set(0);
    }
}