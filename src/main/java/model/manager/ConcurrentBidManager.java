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
            // Nếu có bidDao (production), thực hiện khóa tiền để cam kết thanh toán.
            if (bidDao != null) {
                database.UserDAO userDao = new database.UserDAO();
                
                // 1. Xác định người đang dẫn đầu HIỆN TẠI (trước khi ghi đè)
                model.auction.BidTransaction prevBid = auction.getHighestBid();
                String prevBidderId = (prevBid != null) ? prevBid.getBidder().getUserId() : null;
                double prevAmount   = (prevBid != null) ? prevBid.getBidAmount() : 0;

                // 2. Kiểm tra nếu cùng 1 người nâng giá
                if (bidder.getUserId().equals(prevBidderId)) {
                    double diff = amount - prevAmount;
                    if (diff > 0) {
                        if (!userDao.lockBalance(bidder.getUserId(), diff)) {
                            failureCount.incrementAndGet();
                            return BidResult.failure(auctionId, amount, "Số dư không đủ để nâng giá!");
                        }
                    }
                } else {
                    // Người mới nhảy vào bid
                    if (!userDao.lockBalance(bidder.getUserId(), amount)) {
                        failureCount.incrementAndGet();
                        return BidResult.failure(auctionId, amount, "Số dư không đủ để cam kết bid!");
                    }
                    // Giải phóng tiền cho người vừa bị vượt mặt (nếu không phải là Auto-Bid - sẽ do AutoBidManager quản lý)
                    // Tuy nhiên để an toàn và đơn giản, nếu người đó không có AutoBid đang chạy thì ta unlock.
                    // ĐỂ ĐẢM BẢO NHẤT QUÁN: Ta luôn unlock tiền Bid cũ tại đây, 
                    // còn tiền cọc AutoBid sẽ do AutoBidManager tự quản lý riêng.
                    if (prevBidderId != null) {
                        // Kiểm tra xem bid cũ có phải từ AutoBid không?
                        // Nếu bid cũ là manual bid, ta unlock.
                        // Nếu bid cũ là auto bid, AutoBidManager sẽ tự unlock khi nó 'exhausted'.
                        // NHƯNG: Để đơn giản, ta quy ước: 
                        // - Manual bid khóa đúng 'amount'.
                        // - Auto-bid khóa đúng 'maxBid'.
                        // Khi 1 manual bid bị outbid, unlock đúng 'amount'.
                        // Khi 1 auto-bid bị outbid, NHƯNG maxBid vẫn > currentPrice -> KHÔNG unlock.
                        // Khi 1 auto-bid bị outbid VÀ maxBid <= currentPrice -> Unlock 'maxBid'.
                        
                        // Lấy AutoBid của người cũ
                        model.auction.AutoBid prevAB = new database.AutoBidDAO().findByUserAndAuction(prevBidderId, auctionId);
                        if (prevAB == null) {
                            userDao.unlockBalance(prevBidderId, prevAmount);
                        }
                        // Nếu có prevAB, AutoBidManager.executeAutoBids sẽ lo việc unlock nếu nó thua hoàn toàn.
                    }
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