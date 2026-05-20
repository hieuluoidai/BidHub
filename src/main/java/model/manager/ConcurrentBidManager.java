package model.manager;

import database.BidTransactionDAO;
import exception.ErrorCode;
import model.auction.Auction;
import model.auction.BidTransaction;
import model.auction.BidResult;
import model.user.User;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bộ điều phối bid đồng thời, là điểm đồng bộ trung tâm cho mọi thao tác đặt giá.
 *
 * <p>Mỗi phiên đấu giá có một lock riêng. Cách này giúp các bid trong cùng một phiên
 * được xử lý tuần tự để tránh race condition, trong khi bid ở các phiên khác nhau
 * vẫn có thể chạy song song.
 */
public class ConcurrentBidManager {

    private static volatile ConcurrentBidManager instance;
    private final ConcurrentHashMap<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    // ====== Chỉ số vận hành, an toàn đa luồng nhờ AtomicLong ======
    private final AtomicLong successCount    = new AtomicLong();
    private final AtomicLong outbidCount     = new AtomicLong();
    private final AtomicLong failureCount    = new AtomicLong();
    private final AtomicLong contentionCount = new AtomicLong();

    private ConcurrentBidManager() {}

    /**
     * Singleton an toàn đa luồng, dùng double-checked locking kết hợp volatile
     * để tránh tạo thừa instance.
     */
    public static ConcurrentBidManager getInstance() {
        if (instance == null) {
            synchronized (ConcurrentBidManager.class) {
                if (instance == null) instance = new ConcurrentBidManager();
            }
        }
        return instance;
    }

    /**
     * Lấy hoặc tạo mới lock công bằng cho từng auctionId.
     * Các thread chờ lock sẽ được phục vụ theo thứ tự để giảm tranh chấp không công bằng.
     */
    private ReentrantLock getLock(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
    }

    /** Bản test/offline (không có DB). */
    public BidResult processBid(String auctionId, double amount, User bidder) {
        return processBid(auctionId, amount, bidder, null);
    }

    /**
     * Xử lý bid trong luồng production với đầy đủ kiểm tra nhanh, khóa theo phiên,
     * kiểm tra lại trong lock và lưu database nếu bidDao được truyền vào.
     */
    public BidResult processBid(String auctionId, double amount, User bidder,
                                BidTransactionDAO bidDao) {
        if (auctionId == null || auctionId.isBlank()) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount, ErrorCode.VALIDATION_ERROR,
                    "Mã phiên đấu giá không được để trống.");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount, ErrorCode.VALIDATION_ERROR,
                    "Số tiền đặt giá phải là số dương hợp lệ.");
        }
        if (bidder == null) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount, ErrorCode.VALIDATION_ERROR,
                    "Người đặt giá không được để trống.");
        }

        AuctionManager manager = AuctionManager.getInstance();
        Auction auction = manager.getAuctionById(auctionId);

        if (auction == null) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount, ErrorCode.AUCTION_NOT_FOUND,
                    "Phiên đấu giá không tồn tại.");
        }

        // Sau khi server restart, state trong RAM có thể vừa được dựng lại nhưng chưa
        // mang lịch sử đầy đủ. Trước khi kiểm tra giá mới, tự đồng bộ lại từ DB để
        // không bao giờ cho phép một bid thấp hơn giá cao nhất cũ "mở vòng mới".
        restorePersistedHistoryIfNeeded(auctionId, auction, bidDao);

        // ===== 1. Kiểm tra nhanh ngoài lock để loại sớm các bid chắc chắn không hợp lệ =====
        if (!"RUNNING".equals(auction.getStatus())) {
            failureCount.incrementAndGet();
            return BidResult.failure(auctionId, amount, ErrorCode.AUCTION_NOT_RUNNING,
                    auctionNotRunningMessage(auctionId, auction.getStatus()));
        }
        if (amount <= auction.getCurrentPrice()) {
            outbidCount.incrementAndGet();
            return BidResult.outbid(auctionId, amount, auction.getCurrentPrice());
        }

        // ===== 2. Vùng xử lý quan trọng: chỉ một thread được thao tác trên cùng một phiên =====
        ReentrantLock lock = getLock(auctionId);
        if (lock.isLocked()) contentionCount.incrementAndGet();

        long t0 = System.nanoTime();
        lock.lock();
        try {
            // Kiểm tra lại vì trạng thái phiên có thể đã thay đổi trong lúc thread đang chờ lock.
            auction = manager.getAuctionById(auctionId);
            if (auction == null) {
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, ErrorCode.AUCTION_NOT_FOUND,
                        "Phiên đấu giá không tồn tại.");
            }

            // Kiểm tra lại lịch sử bid vì đây mới là thời điểm quyết định giá cuối cùng.
            restorePersistedHistoryIfNeeded(auctionId, auction, bidDao);

            if (!"RUNNING".equals(auction.getStatus())) {
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, ErrorCode.AUCTION_NOT_RUNNING,
                        auctionNotRunningMessage(auctionId, auction.getStatus()));
            }
            
            double currentPrice = auction.getCurrentPrice();
            if (amount <= currentPrice) {
                outbidCount.incrementAndGet();
                return BidResult.outbid(auctionId, amount, currentPrice);
            }

            // ===== 2b. Kiểm tra và khóa số dư sau khi đã chắc chắn bid đủ cao =====
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
                            return BidResult.failure(auctionId, amount, ErrorCode.INSUFFICIENT_BALANCE,
                                    "Số dư không đủ để nâng giá.");
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
                            return BidResult.failure(auctionId, amount, ErrorCode.INSUFFICIENT_BALANCE,
                                    "Số dư không đủ để cam kết bid.");
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

            // ===== 3. Cập nhật bộ nhớ trước, sau đó mới lưu DB ở bước kế tiếp =====
            java.time.LocalDateTime endTimeBefore = auction.getEndTime();
            try {
                auction.placeBid(bidder, amount);
            } catch (Exception e) {
                if (lockAcquired) {
                    userDao.unlockBalance(bidder.getUserId(), amountToUnlockOnFailure);
                }
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, e);
            }

            // ===== 3b. Anti-sniping: nếu phiên vừa được gia hạn thì ghi endTime mới xuống DB =====
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

                // Gửi thông báo gia hạn cho seller để họ biết phiên được kéo dài do có bid phút chót.
                network.AuctionServer server = manager.getServer();
                if (server != null) {
                    String itemName = (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId;
                    // Thông báo trực tiếp cho người bán của phiên.
                    utils.NotificationService.notifyUser(server, auction.getSellerId(),
                        model.notification.Notification.Type.AUCTION_EXTENDED,
                        "Phiên đấu giá được gia hạn",
                        String.format("Phiên \"%s\" vừa được cộng thêm thời gian do có người bid vào phút chót.", itemName));
                    
                    // Có thể phát tín hiệu riêng để client nạp lại end_time.
                    // Hiện Auction object đã mang dữ liệu mới nên chưa cần tín hiệu riêng.
                }
            }

            // ===== 4. Lưu vào DB để đảm bảo trạng thái vẫn khôi phục được sau restart =====
            if (bidDao != null) {
                model.auction.BidTransaction latest = auction.getHighestBid();
                boolean saved = bidDao.save(auctionId, bidder.getUserId(), amount, 
                                          latest.getBidType(), latest.getTimestamp());
                if (!saved) {
                    System.err.println(">>> [WARN] Bid memory thành công nhưng DB lưu thất bại " +
                            "cho phiên " + auctionId);
                }
            }

            // ===== 5. Kích hoạt Auto-Bidding sau khi bid thủ công thành công =====
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

    /**
     * Tự vá state sau restart nếu phiên trong RAM đang thiếu history nhưng DB đã có bid.
     * Không ghi đè khi RAM đã có dữ liệu để tránh query thừa trong đường nóng bình thường.
     */
    private void restorePersistedHistoryIfNeeded(String auctionId, Auction auction,
                                                 BidTransactionDAO bidDao) {
        if (auction == null || bidDao == null || !auction.getBidHistory().isEmpty()) {
            return;
        }

        List<BidTransaction> persistedHistory = bidDao.findTransactionsByAuctionId(auctionId);
        if (!persistedHistory.isEmpty()) {
            auction.restoreBidHistory(persistedHistory);
            System.out.printf(">>> [HISTORY RESTORE] Phiên %s nạp lại %d bid từ DB, giá cao nhất $%.2f%n",
                    auctionId, persistedHistory.size(), auction.getCurrentPrice());
        }
    }

    private String auctionNotRunningMessage(String auctionId, String status) {
        return String.format("Phiên đấu giá '%s' không thể đặt giá (Trạng thái: %s).", auctionId, status);
    }

    /** Giải phóng lock khi phiên đấu giá kết thúc. */
    public void releaseLock(String auctionId) {
        auctionLocks.remove(auctionId);
    }

    public int activeLockCount() { 
        return auctionLocks.size(); 
    }

    // ===== Getter cho chỉ số vận hành, dùng khi báo cáo/demo bảo vệ đồ án =====
    public long getSuccessCount() {
        return successCount.get();
    }

    public long getOutbidCount() {
        return outbidCount.get();
    }

    public long getFailureCount() {
        return failureCount.get();
    }

    public long getContentionCount() {
        return contentionCount.get();
    }

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
