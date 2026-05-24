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
 */
public class ConcurrentBidManager {

    private static volatile ConcurrentBidManager instance;
    private final ConcurrentHashMap<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();
    
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> anonymousSequences = new ConcurrentHashMap<>();

    private final AtomicLong successCount    = new AtomicLong();
    private final AtomicLong outbidCount     = new AtomicLong();
    private final AtomicLong failureCount    = new AtomicLong();
    private final AtomicLong contentionCount = new AtomicLong();

    private ConcurrentBidManager() {}

    public static ConcurrentBidManager getInstance() {
        if (instance == null) {
            synchronized (ConcurrentBidManager.class) {
                if (instance == null) instance = new ConcurrentBidManager();
            }
        }
        return instance;
    }

    private ReentrantLock getLock(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
    }

    public BidResult processBid(String auctionId, double amount, User bidder) {
        return processBid(auctionId, amount, bidder, false, null);
    }

    public BidResult processBid(String auctionId, double amount, User bidder,
                                BidTransactionDAO bidDao) {
        return processBid(auctionId, amount, bidder, false, bidDao);
    }

    public BidResult processBid(String auctionId, double amount, User bidder,
                                boolean isAnonymous, BidTransactionDAO bidDao) {
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

        restorePersistedHistoryIfNeeded(auctionId, auction, bidDao);

        String anonName = null;
        if (isAnonymous) {
            anonName = getOrCreateAnonymousName(auctionId, bidder.getUserId());
        }

        if (!"RUNNING".equals(auction.getStatus())) {
            failureCount.incrementAndGet();
            return isAnonymous 
                ? BidResult.outbidAnonymous(auctionId, amount, auction.getCurrentPrice(), anonName) 
                : BidResult.failure(auctionId, amount, ErrorCode.AUCTION_NOT_RUNNING,
                    auctionNotRunningMessage(auctionId, auction.getStatus()));
        }
        if (amount <= auction.getCurrentPrice()) {
            outbidCount.incrementAndGet();
            return isAnonymous 
                ? BidResult.outbidAnonymous(auctionId, amount, auction.getCurrentPrice(), anonName)
                : BidResult.outbid(auctionId, amount, auction.getCurrentPrice());
        }

        ReentrantLock lock = getLock(auctionId);
        if (lock.isLocked()) contentionCount.incrementAndGet();

        long t0 = System.nanoTime();
        lock.lock();
        try {
            auction = manager.getAuctionById(auctionId);
            if (auction == null) {
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, ErrorCode.AUCTION_NOT_FOUND,
                        "Phiên đấu giá không tồn tại.");
            }

            restorePersistedHistoryIfNeeded(auctionId, auction, bidDao);

            if (!"RUNNING".equals(auction.getStatus())) {
                failureCount.incrementAndGet();
                return isAnonymous
                    ? BidResult.outbidAnonymous(auctionId, amount, auction.getCurrentPrice(), anonName)
                    : BidResult.failure(auctionId, amount, ErrorCode.AUCTION_NOT_RUNNING,
                        auctionNotRunningMessage(auctionId, auction.getStatus()));
            }
            
            double currentPrice = auction.getCurrentPrice();
            if (amount <= currentPrice) {
                outbidCount.incrementAndGet();
                return isAnonymous
                    ? BidResult.outbidAnonymous(auctionId, amount, currentPrice, anonName)
                    : BidResult.outbid(auctionId, amount, currentPrice);
            }

            database.UserDAO userDao = new database.UserDAO();
            database.AutoBidDAO autoBidDao = new database.AutoBidDAO();
            boolean lockAcquired = false;
            double amountToUnlockOnFailure = 0;

            if (bidDao != null) {
                model.auction.BidTransaction prevBid = auction.getHighestBid();
                String prevBidderId = (prevBid != null) ? prevBid.getBidder().getUserId() : null;
                double prevAmount   = (prevBid != null) ? prevBid.getBidAmount() : 0;

                model.auction.AutoBid currentAB = autoBidDao.findByUserAndAuction(bidder.getUserId(), auctionId);

                // TỰ ĐỘNG HỦY AUTO-BID nếu đặt giá thủ công cao hơn maxBid hiện tại
                if (currentAB != null && amount > currentAB.getMaxBid()) {
                    System.out.printf(">>> [Auto-Cancel] Hủy Auto-Bid cho %s vì bid thủ công %.2f > maxBid %.2f%n",
                            bidder.getUserId(), amount, currentAB.getMaxBid());
                    AutoBidManager.getInstance().cancelAutoBid(bidder.getUserId(), auctionId);
                    
                    // THÔNG BÁO CHO CLIENT để cập nhật UI ngay lập tức
                    network.AuctionServer server = manager.getServer();
                    if (server != null) {
                        String msg = "CANCEL_AUTOBID_OK:" + auctionId + ":" + userDao.getBalance(bidder.getUserId());
                        server.sendToUser(bidder.getUserId(), msg);
                    }
                    
                    // Cập nhật lại reference sau khi hủy
                    currentAB = null;
                }

                if (bidder.getUserId().equals(prevBidderId)) {
                    double baseLocked = (currentAB != null) ? currentAB.getMaxBid() : prevAmount;
                    if (amount > baseLocked) {
                        double diff = Math.round((amount - baseLocked) * 100.0) / 100.0;
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
                        double diff = Math.round((amount - baseLocked) * 100.0) / 100.0;
                        if (!userDao.lockBalance(bidder.getUserId(), diff)) {
                            failureCount.incrementAndGet();
                            return BidResult.failure(auctionId, amount, ErrorCode.INSUFFICIENT_BALANCE,
                                    "Số dư không đủ để cam kết bid.");
                        }
                        lockAcquired = true;
                        amountToUnlockOnFailure = diff;
                    }

                    if (prevBidderId != null) {
                        // FIX: Chỉ giải phóng tiền nếu người bị outbid KHÔNG có Auto-Bid.
                        // Nếu họ có Auto-Bid, chúng ta giữ nguyên trạng thái khóa tiền để AutoBidManager 
                        // có thể thực hiện so kè ngay sau đây. Nếu Auto-Bid của họ hết hạn, 
                        // AutoBidManager sẽ tự động dọn dẹp và hoàn tiền.
                        model.auction.AutoBid prevAB = autoBidDao.findByUserAndAuction(prevBidderId, auctionId);
                        if (prevAB == null) {
                            userDao.unlockBalance(prevBidderId, prevAmount);
                        }
                    }
                }
            }

            java.time.LocalDateTime endTimeBefore = auction.getEndTime();
            try {
                auction.placeBid(bidder, amount, BidTransaction.BidType.MANUAL, isAnonymous, anonName);
            } catch (Exception e) {
                if (lockAcquired) {
                    userDao.unlockBalance(bidder.getUserId(), amountToUnlockOnFailure);
                }
                failureCount.incrementAndGet();
                return BidResult.failure(auctionId, amount, e);
            }

            java.time.LocalDateTime endTimeAfter = auction.getEndTime();
            boolean wasExtended = endTimeBefore != null
                    && endTimeAfter != null
                    && endTimeAfter.isAfter(endTimeBefore);
            if (wasExtended && bidDao != null) {
                boolean tetUpdated = new database.AuctionDAO().updateEndTime(auctionId, endTimeAfter);
                if (!tetUpdated) {
                    System.err.println(">>> [WARN] Anti-snipe: gia hạn memory OK nhưng ghi xuống DB thất bại");
                }
                network.AuctionServer server = manager.getServer();
                if (server != null) {
                    String itemName = (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId;
                    utils.NotificationService.notifyUser(server, auction.getSellerId(),
                        model.notification.Notification.Type.AUCTION_EXTENDED,
                        "Phiên đấu giá được gia hạn",
                        String.format("Phiên \"%s\" vừa được cộng thêm thời gian.", itemName));
                }
            }

            if (bidDao != null) {
                model.auction.BidTransaction latest = auction.getHighestBid();
                boolean saved = bidDao.save(auctionId, bidder.getUserId(), amount, 
                                          latest.getBidType(), latest.getTimestamp(), isAnonymous, anonName);
                if (!saved) {
                    System.err.println(">>> [WARN] Bid memory thành công nhưng DB lưu thất bại " + auctionId);
                }
            }

            AutoBidManager.getInstance().executeAutoBids(auctionId, bidDao);

            successCount.incrementAndGet();
            long dt = System.nanoTime() - t0;
            System.out.printf(">>> [LOCK SUCCESS] phiên %s | giữ lock %.2fms%n", auctionId, dt / 1_000_000.0);

            return isAnonymous 
                ? BidResult.successAnonymous(auctionId, amount, bidder.getUsername(), anonName)
                : BidResult.success(auctionId, amount, bidder.getUsername());

        } finally {
            lock.unlock();
        }
    }

    public String getAnonymousName(String auctionId, String userId) {
        return getOrCreateAnonymousName(auctionId, userId);
    }

    private String getOrCreateAnonymousName(String auctionId, String userId) {
        ConcurrentHashMap<String, Integer> auctionSeqs = 
            anonymousSequences.computeIfAbsent(auctionId, k -> new ConcurrentHashMap<>());
        
        Integer seq = auctionSeqs.get(userId);
        if (seq == null) {
            synchronized (auctionSeqs) {
                seq = auctionSeqs.get(userId);
                if (seq == null) {
                    seq = auctionSeqs.size() + 1;
                    auctionSeqs.put(userId, seq);
                }
            }
        }
        return String.format("AnonymousBidder_%03d", seq);
    }

    private void restorePersistedHistoryIfNeeded(String auctionId, Auction auction,
                                                 BidTransactionDAO bidDao) {
        if (auction == null || bidDao == null || !auction.getBidHistory().isEmpty()) {
            return;
        }

        List<BidTransaction> persistedHistory = bidDao.findTransactionsByAuctionId(auctionId);
        if (!persistedHistory.isEmpty()) {
            auction.restoreBidHistory(persistedHistory);
        }
    }

    private String auctionNotRunningMessage(String auctionId, String status) {
        return String.format("Phiên đấu giá '%s' không thể đặt giá (Trạng thái: %s).", auctionId, status);
    }

    public void releaseLock(String auctionId) {
        auctionLocks.remove(auctionId);
    }

    public int activeLockCount() {
        return auctionLocks.size();
    }

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

    public void resetMetrics() {
        successCount.set(0);
        outbidCount.set(0);
        failureCount.set(0);
        contentionCount.set(0);
    }
}
