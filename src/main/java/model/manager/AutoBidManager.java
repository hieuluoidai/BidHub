package model.manager;

import database.AutoBidDAO;
import database.BidTransactionDAO;
import database.UserDAO;
import model.auction.Auction;
import model.auction.AutoBid;
import model.auction.BidResult;
import model.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Auto-Bidding logic.
 */
public class AutoBidManager {
    private static volatile AutoBidManager instance;
    private final ConcurrentHashMap<String, List<AutoBid>> auctionAutoBids = new ConcurrentHashMap<>();
    private final AutoBidDAO autoBidDAO = new AutoBidDAO();
    private final UserDAO userDAO = new UserDAO();

    private AutoBidManager() {
        loadAllFromDB();
    }

    public static AutoBidManager getInstance() {
        if (instance == null) {
            synchronized (AutoBidManager.class) {
                if (instance == null) instance = new AutoBidManager();
            }
        }
        return instance;
    }

    private void loadAllFromDB() {
        List<AutoBid> all = autoBidDAO.findAll();
        for (AutoBid ab : all) {
            auctionAutoBids.computeIfAbsent(ab.getAuctionId(), id -> new ArrayList<>()).add(ab);
        }
        System.out.println(">>> [AutoBid] Loaded " + all.size() + " auto-bids from DB.");
    }

    /**
     * Registers a new Auto-Bid for a user.
     * Locks the balance immediately.
     */
    public synchronized String registerAutoBid(String userId, String auctionId, double maxBid, double increment) {
        // 1. Check if user has enough balance to lock
        User user = userDAO.findById(userId);
        if (user == null) return "USER_NOT_FOUND";
        
        // If there's an existing auto-bid, release its lock first to simplify update
        AutoBid existing = autoBidDAO.findByUserAndAuction(userId, auctionId);
        if (existing != null) {
            userDAO.unlockBalance(userId, existing.getMaxBid());
            removeAutoBidFromMemory(existing);
        } else {
            // FIX: If no existing AutoBid, check if they have a manual bid as leader
            // to avoid double-locking (manual bid lock + new autobid lock)
            Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
            if (auction != null && auction.getHighestBid() != null 
                    && auction.getHighestBid().getBidder().getUserId().equals(userId)) {
                double currentManualAmount = auction.getHighestBid().getBidAmount();
                userDAO.unlockBalance(userId, currentManualAmount);
                System.out.printf(">>> [AutoBid Upgrade] Unlocking manual bid $%.2f for %s before locking maxBid $%.2f%n",
                        currentManualAmount, userId, maxBid);
            }
        }

        if (userDAO.getBalance(userId) < maxBid) {
            return "INSUFFICIENT_BALANCE";
        }

        // 2. Lock the balance
        if (!userDAO.lockBalance(userId, maxBid)) {
            return "LOCK_FAILED";
        }

        // 3. Save to DB and Memory
        AutoBid newAutoBid = new AutoBid(UUID.randomUUID().toString(), auctionId, userId, maxBid, increment);
        if (autoBidDAO.save(newAutoBid)) {
            auctionAutoBids.computeIfAbsent(auctionId, id -> new ArrayList<>()).add(newAutoBid);
            return "SUCCESS";
        } else {
            userDAO.unlockBalance(userId, maxBid); // Rollback lock if DB save fails
            return "DB_ERROR";
        }
    }

    public synchronized boolean cancelAutoBid(String userId, String auctionId) {
        AutoBid existing = autoBidDAO.findByUserAndAuction(userId, auctionId);
        if (existing == null) return false;

        // 1. Release lock
        userDAO.unlockBalance(userId, existing.getMaxBid());

        // 2. Remove from DB and Memory
        if (autoBidDAO.deleteByUserAndAuction(userId, auctionId)) {
            removeAutoBidFromMemory(existing);
            return true;
        }
        return false;
    }

    private void removeAutoBidFromMemory(AutoBid ab) {
        List<AutoBid> list = auctionAutoBids.get(ab.getAuctionId());
        if (list != null) {
            list.removeIf(item -> item.getUserId().equals(ab.getUserId()));
        }
    }

    /**
     * The core logic: Executes Auto-Bid battle after a manual bid.
     * Should be called from ConcurrentBidManager while holding the auction lock.
     */
    public void executeAutoBids(String auctionId, BidTransactionDAO bidDAO) {
        List<AutoBid> autoBids = auctionAutoBids.get(auctionId);
        if (autoBids == null || autoBids.isEmpty()) return;

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null || !"RUNNING".equals(auction.getStatus())) return;

        double currentPrice = auction.getCurrentPrice();
        
        // 1. PhÃ¢n loáº¡i: Auto-Bids cÃ²n hiá»‡u lá»±c vÃ  Auto-Bids Ä‘Ã£ háº¿t háº¡n (Max < Current)
        List<AutoBid> validAutoBids = new ArrayList<>();
        List<AutoBid> exhaustedAutoBids = new ArrayList<>();

        for (AutoBid ab : autoBids) {
            if (ab.getMaxBid() > currentPrice) {
                validAutoBids.add(ab);
            } else {
                exhaustedAutoBids.add(ab);
            }
        }

        // 2. Dá»n dáº¹p ngay cÃ¡c Auto-Bids Ä‘Ã£ tháº¥t báº¡i (Unlock tiá»n cho ngÆ°á»i dÃ¹ng)
        for (AutoBid ab : exhaustedAutoBids) {
            System.out.printf(">>> [AutoBid RETIRE] User %s outbid. Unlocking $%.2f%n", ab.getUserId(), ab.getMaxBid());
            userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
            autoBidDAO.delete(ab.getAutoBidId());
            removeAutoBidFromMemory(ab);
        }

        if (validAutoBids.isEmpty()) return;

        // 3. TÃ¬m ngÆ°á»i tháº¯ng cuá»™c trong danh sÃ¡ch Auto-Bidders
        AutoBid best = null;
        AutoBid secondBest = null;

        for (AutoBid ab : validAutoBids) {
            if (best == null) {
                best = ab;
            } else if (ab.getMaxBid() > best.getMaxBid()) {
                secondBest = best;
                best = ab;
            } else if (ab.getMaxBid() == best.getMaxBid()) {
                if (ab.getCreatedAt().isBefore(best.getCreatedAt())) {
                    secondBest = best;
                    best = ab;
                } else {
                    if (secondBest == null || ab.getMaxBid() > secondBest.getMaxBid()) {
                        secondBest = ab;
                    }
                }
            } else {
                if (secondBest == null || ab.getMaxBid() > secondBest.getMaxBid()) {
                    secondBest = ab;
                }
            }
        }

        String currentWinnerId = (auction.getHighestBid() != null) ? auction.getHighestBid().getBidder().getUserId() : null;

        // 4. Náº¿u ngÆ°á»i tháº¯ng hiá»‡n táº¡i lÃ  ngÆ°á»i cÃ³ Auto-Bid tá»‘t nháº¥t, chá»‰ cáº§n nÃ¢ng giÃ¡ náº¿u cáº§n thiáº¿t
        if (best.getUserId().equals(currentWinnerId)) {
            if (secondBest != null) {
                double neededPrice = secondBest.getMaxBid() + best.getIncrement();
                if (neededPrice > best.getMaxBid()) neededPrice = best.getMaxBid();
                if (neededPrice > currentPrice) {
                    applyAutoBid(auction, best, neededPrice, bidDAO);
                    // Sau khi apply, check láº¡i xem cÃ³ ai vá»«a bá»‹ loáº¡i khÃ´ng (Recursion-like check)
                    executeAutoBids(auctionId, bidDAO); 
                }
            }
            return;
        }

        // 5. TÃ­nh toÃ¡n giÃ¡ nháº£y (Instant Jump)
        double priceToJump;
        if (secondBest != null) {
            priceToJump = secondBest.getMaxBid() + best.getIncrement();
        } else {
            priceToJump = currentPrice + best.getIncrement();
        }

        if (priceToJump > best.getMaxBid()) priceToJump = best.getMaxBid();
        if (priceToJump <= currentPrice) priceToJump = currentPrice + 0.01;

        applyAutoBid(auction, best, priceToJump, bidDAO);
        
        // 6. Äá»‡ quy kiá»ƒm tra láº¡i Ä‘á»ƒ loáº¡i bá» các Auto-Bid vá»«a bá»‹ outbid bởi giá mới
        executeAutoBids(auctionId, bidDAO);
    }

    private void applyAutoBid(Auction auction, AutoBid ab, double amount, BidTransactionDAO bidDAO) {
        User bidder = userDAO.findById(ab.getUserId());
        if (bidder == null) return;

        // Xác định người bị vượt mặt
        model.auction.BidTransaction prevBid = auction.getHighestBid();
        String prevBidderId = (prevBid != null) ? prevBid.getBidder().getUserId() : null;
        double prevAmount   = (prevBid != null) ? prevBid.getBidAmount() : 0;

        try {
            // 1. Memory update
            auction.placeBid(bidder, amount);
            
            // 2. DB update
            if (bidDAO != null) {
                bidDAO.save(auction.getAuctionId(), bidder.getUserId(), amount);
            }

            // 3. Giải phóng tiền cho người bị outbid (nếu người đó chỉ dùng manual bid)
            if (prevBidderId != null && !prevBidderId.equals(ab.getUserId())) {
                AutoBid prevAB = autoBidDAO.findByUserAndAuction(prevBidderId, auction.getAuctionId());
                if (prevAB == null) {
                    userDAO.unlockBalance(prevBidderId, prevAmount);
                }
            }
            
            System.out.printf(">>> [AutoBid SUCCESS] User %s auto-bid $%.2f on auction %s%n", 
                bidder.getUsername(), amount, auction.getAuctionId());
            
        } catch (Exception e) {
            System.err.println(">>> [AutoBid ERROR] " + e.getMessage());
        }
    }

    public void cleanup(String auctionId) {
        List<AutoBid> autoBids = auctionAutoBids.remove(auctionId);
        if (autoBids == null) return;

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        String winnerId = (auction != null && auction.getHighestBid() != null) 
            ? auction.getHighestBid().getBidder().getUserId() : null;
        double finalPrice = (auction != null) ? auction.getCurrentPrice() : 0;

        for (AutoBid ab : autoBids) {
            if (ab.getUserId().equals(winnerId)) {
                // Winner: Chỉ giải phóng phần THỪA (MaxBid - Giá thắng)
                // Giữ lại đúng 'finalPrice' trong locked_balance để phục vụ thanh toán
                double excess = ab.getMaxBid() - finalPrice;
                if (excess > 0) {
                    userDAO.unlockBalance(ab.getUserId(), excess);
                    System.out.printf(">>> [AutoBid CLEANUP] Winner %s: Unlocking excess $%.2f, keeping $%.2f locked for payment.%n",
                            ab.getUserId(), excess, finalPrice);
                }
            } else {
                // Loser: giải phóng toàn bộ quỹ đã cam kết
                userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
            }
            autoBidDAO.delete(ab.getAutoBidId());
        }
    }
}
