package model.manager;

import model.auction.Auction;
import model.auction.BidResult;
import model.user.User;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentBidManager {

    private static ConcurrentBidManager instance;
    private final ConcurrentHashMap<String, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    private ConcurrentBidManager() {}

    public static synchronized ConcurrentBidManager getInstance() {
        if (instance == null) instance = new ConcurrentBidManager();
        return instance;
    }

    private ReentrantLock getLock(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
    }

    public BidResult processBid(String auctionId, double amount, User bidder) {
        AuctionManager manager = AuctionManager.getInstance();
        Auction auction = manager.getAuctionById(auctionId);

        if (auction == null) {
            return BidResult.failure(auctionId, amount, "Phiên đấu giá không tồn tại!");
        }

        // Optimistic pre-check (không lock)
        if (!"RUNNING".equals(auction.getStatus())) {
            return BidResult.failure(auctionId, amount,
                    "Phiên không đang diễn ra (Trạng thái: " + auction.getStatus() + ")");
        }
        if (amount <= auction.getCurrentPrice()) {
            return BidResult.failure(auctionId, amount,
                    String.format("Giá $%.2f phải cao hơn giá hiện tại $%.2f!", amount, auction.getCurrentPrice()));
        }

        // Acquire lock riêng của phiên này
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            // Double-check dưới lock
            auction = manager.getAuctionById(auctionId);
            if (auction == null) {
                return BidResult.failure(auctionId, amount, "Phiên đấu giá không tồn tại!");
            }
            if (!"RUNNING".equals(auction.getStatus())) {
                return BidResult.failure(auctionId, amount, "Phiên đã kết thúc trong lúc bạn chờ!");
            }

            double currentPrice = auction.getCurrentPrice();
            if (amount <= currentPrice) {
                return BidResult.outbid(auctionId, amount, currentPrice,
                        String.format("Bạn đã bị vượt giá! Giá hiện tại là $%.2f, giá của bạn $%.2f không đủ.",
                                currentPrice, amount));
            }

            try {
                auction.placeBid(bidder, amount);
                return BidResult.success(auctionId, amount, bidder.getUsername());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return BidResult.failure(auctionId, amount, e.getMessage());
            }

        } finally {
            lock.unlock();
        }
    }

    public void releaseLock(String auctionId) {
        auctionLocks.remove(auctionId);
    }

    public int activeLockCount() {
        return auctionLocks.size();
    }
}