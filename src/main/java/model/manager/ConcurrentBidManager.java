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

            // ===== 2b. Lock & Balance check =====
            database.UserDAO userDao = new database.UserDAO();
            boolean lockAcquired = false;
            double amountToUnlockOnFailure = 0;

            if (bidDao != null) {
                model.auction.BidTransaction prevBid = auction.getHighestBid();
                String prevBidderId = (prevBid != null) ? prevBid.getBidder().getUserId() : null;
                double prevAmount   = (prevBid != null) ? prevBid.getBidAmount() : 0;

                // Kiểm tra xem người đặt giá hiện tại có AutoBid không để tránh khóa trùng lặp
                model.auction.AutoBid currentAB = new database.AutoBidDAO().findByUserAndAuction(bidder.getUserId(), auctionId);

                if (bidder.getUserId().equals(prevBidderId)) {
                    double baseLocked = (currentAB != null) ? currentAB.getMaxBid() : prevAmount;
                    if (amount > baseLocked) {
                        double diff = amount - baseLocked;
                        if (!userDao.lockBalance(bidder.getUserId(), diff)) {
                            failureCount.incrementAndGet();
                            return BidResult.failure(auctionId, amount, "Số dư không đủ để nâng giá!");
                        }
                        lockAcquired = true;
                        amountToUnlockOnFailure = diff;
                    }
                } else {
                    double baseLocked = (currentAB != null) ? currentAB.getMaxBid() : 0;
                    if (amount > baseLocked) {
                        double diff = amount - baseLocked;
                        if (!userDao.lockBalance(bidder.getUserId(), diff)) {
                            failureCount.incrementAndGet();
                            return BidResult.failure(auctionId, amount, "Số dư không đủ để cam kết bid!");
                        }
                        lockAcquired = true;
                        amountToUnlockOnFailure = diff;
                    }

                    if (prevBidderId != null) {
                        model.auction.AutoBid prevAB = new database.AutoBidDAO().findByUserAndAuction(prevBidderId, auctionId);
                        if (prevAB == null) {
                            userDao.unlockBalance(prevBidderId, prevAmount);
                        }
                    }
                }
            }

            // ===== 3. Cập nhật dữ liệu trên bộ nhớ (Memory update) =====
            java.time.LocalDateTime endTimeBefore = auction.getEndTime();
            try {
                auction.placeBid(bidder, amount);
            } catch (Exception e) {
                if (lockAcquired) {
                    userDao.unlockBalance(bidder.getUserId(), amountToUnlockOnFailure);
                }
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, e.getMessage());
            }

            // ===== 3b. ANTI-SNIPING: nếu phiên vừa được gia hạn → ghi DB =====
            java.time.LocalDateTime endTimeAfter = auction.getEndTime();
            boolean wasExtended = endTimeBefore != null
                    && endTimeAfter != null
                    && endTimeAfter.isAfter(endTimeBefore);
            if (wasExtended && bidDao != null) {
                boolean tetUpdated = new database.AuctionDAO()
                        .updateEndTime(auctionId, endTimeAfter);
                if (!tetUpdated) {
                    System.err.println(">>> [WARN] Anti-snipe: gia hạn memory OK nhưng "
                            + "ghi end_time xuống DB thất bại cho phiên " + auctionId);
                }
                System.out.printf(">>> [ANTI-SNIPE] Phiên %s gia hạn: %s → %s (lần %d)%n",
                        auctionId, endTimeBefore, endTimeAfter, auction.getExtensionCount());
            }

            // ===== 4. Lưu vào DB (Đảm bảo tính nhất quán) =====
            if (bidDao != null) {
                boolean saved = bidDao.save(auctionId, bidder.getUserId(), amount);
                if (!saved) {
                    System.err.println(">>> [WARN] Bid memory thành công nhưng DB lưu thất bại " +
                            "cho phiên " + auctionId);
                }
            }

            // ===== 5. Kích hoạt Auto-Bidding (Logic mới) =====
            AutoBidManager.getInstance().executeAutoBids(auctionId, bidDao);

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