package model.manager;

import database.AutoBidDAO;
import database.BidTransactionDAO;
import database.UserDAO;
import model.auction.Auction;
import model.auction.AutoBid;
import model.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lớp quản lý các thiết lập đặt giá tự động (Auto-Bid).
 */
public class AutoBidManager {

    private static AutoBidManager instance;

    private final AutoBidDAO autoBidDAO = new AutoBidDAO();
    private final UserDAO userDAO = new UserDAO();

    // Map auctionId -> List các Auto-Bid đang hoạt động trong bộ nhớ.
    // Dùng CopyOnWriteArrayList để an toàn khi vừa lặp vừa xóa/thêm trong cuộc đua bid.
    private final Map<String, List<AutoBid>> auctionAutoBids = new ConcurrentHashMap<>();

    private AutoBidManager() {
        loadAllAutoBids();
    }

    public static synchronized AutoBidManager getInstance() {
        if (instance == null) {
            instance = new AutoBidManager();
        }
        return instance;
    }

    /**
     * Nạp toàn bộ Auto-Bid từ Database vào bộ nhớ khi Server khởi động.
     */
    private void loadAllAutoBids() {
        List<AutoBid> all = autoBidDAO.findAll();
        for (AutoBid ab : all) {
            auctionAutoBids.computeIfAbsent(ab.getAuctionId(), id -> new CopyOnWriteArrayList<>()).add(ab);
        }
        System.out.println(">>> [AutoBid] Loaded " + all.size() + " auto-bids from DB.");
    }

    /**
     * Đăng ký hoặc cập nhật Auto-Bid cho một user trong một phiên.
     */
    public synchronized String registerAutoBid(String userId, String auctionId, double maxBid, double increment) {
        return registerAutoBid(userId, auctionId, maxBid, increment, false);
    }

    public synchronized String registerAutoBid(String userId, String auctionId, 
                                               double maxBid, double increment, boolean isAnonymous) {
        User user = userDAO.findById(userId);
        if (user == null) return "USER_NOT_FOUND";
        
        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        AutoBid existing = autoBidDAO.findByUserAndAuction(userId, auctionId);
        
        // GIẢI PHÓNG TIỀN CŨ
        double amountToUnlock = 0;
        if (existing != null) {
            amountToUnlock = existing.getMaxBid();
        }
        
        if (auction != null && auction.getHighestBid() != null 
                && auction.getHighestBid().getBidder().getUserId().equals(userId)) {
            amountToUnlock = Math.max(amountToUnlock, auction.getHighestBid().getBidAmount());
        }
        
        if (amountToUnlock > 0) {
            userDAO.unlockBalance(userId, amountToUnlock);
        }
        
        if (existing != null) {
            removeAutoBidFromMemory(existing);
        }

        if (userDAO.getBalance(userId) < maxBid) {
            return "INSUFFICIENT_BALANCE";
        }

        if (!userDAO.lockBalance(userId, maxBid)) {
            return "LOCK_FAILED";
        }

        // QUAN TRỌNG: Nếu update, phải giữ nguyên ID cũ để xóa đúng dòng trong DB sau này
        String bidId = (existing != null) ? existing.getAutoBidId() : UUID.randomUUID().toString();
        AutoBid newAutoBid = new AutoBid(bidId, auctionId, userId, maxBid, increment, isAnonymous);
        
        if (autoBidDAO.save(newAutoBid)) {
            auctionAutoBids.computeIfAbsent(auctionId, id -> new CopyOnWriteArrayList<>()).add(newAutoBid);
            return "SUCCESS";
        } else {
            userDAO.unlockBalance(userId, maxBid);
            return "DB_ERROR";
        }
    }

    public synchronized boolean cancelAutoBid(String userId, String auctionId) {
        AutoBid existing = autoBidDAO.findByUserAndAuction(userId, auctionId);
        if (existing == null) return false;

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        boolean isLeading = (auction != null && auction.getHighestBid() != null 
                             && auction.getHighestBid().getBidder().getUserId().equals(userId));
        
        if (isLeading) {
            double currentBid = auction.getHighestBid().getBidAmount();
            double diff = existing.getMaxBid() - currentBid;
            if (diff > 0) {
                userDAO.unlockBalance(userId, diff);
            }
        } else {
            userDAO.unlockBalance(userId, existing.getMaxBid());
        }

        if (autoBidDAO.deleteByUserAndAuction(userId, auctionId)) {
            removeAutoBidFromMemory(existing);
            return true;
        }
        return false;
    }

    public synchronized java.util.Set<String> cleanupForCancellation(String auctionId) {
        java.util.Set<String> bidderIds = new java.util.HashSet<>();
        List<AutoBid> bids = auctionAutoBids.get(auctionId);
        if (bids != null) {
            for (AutoBid ab : bids) {
                userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
                autoBidDAO.delete(ab.getAutoBidId());
                bidderIds.add(ab.getUserId());
            }
            auctionAutoBids.remove(auctionId);
        }
        return bidderIds;
    }

    /**
     * Dọn dẹp Auto-Bid khi phiên kết thúc BÌNH THƯỜNG.
     * Giải phóng tiền của người thua, và giải phóng phần dư của người thắng.
     */
    public synchronized void cleanupForFinish(String auctionId, String winnerId, double finalPrice) {
        List<AutoBid> bids = auctionAutoBids.get(auctionId);
        if (bids != null) {
            for (AutoBid ab : bids) {
                if (ab.getUserId().equals(winnerId)) {
                    // Người thắng: Chỉ hoàn lại phần dư (maxBid - giá thắng)
                    double excess = ab.getMaxBid() - finalPrice;
                    if (excess > 0) {
                        userDAO.unlockBalance(ab.getUserId(), excess);
                    }
                } else {
                    // Người thua: Hoàn lại toàn bộ maxBid
                    userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
                }
                autoBidDAO.delete(ab.getAutoBidId());
            }
            auctionAutoBids.remove(auctionId);
        }
    }

    private void removeAutoBidFromMemory(AutoBid ab) {
        List<AutoBid> list = auctionAutoBids.get(ab.getAuctionId());
        if (list != null) {
            list.removeIf(item -> item.getUserId().equals(ab.getUserId()));
        }
    }

    /**
     * Thực thi cuộc đua Auto-Bid (Dueling logic).
     */
    public void executeAutoBids(String auctionId, BidTransactionDAO bidDAO) {
        List<AutoBid> autoBids = auctionAutoBids.get(auctionId);
        if (autoBids == null || autoBids.isEmpty()) return;

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null || !"RUNNING".equals(auction.getStatus())) return;

        double currentPrice = auction.getCurrentPrice();
        List<AutoBid> validAutoBids = new ArrayList<>();
        List<AutoBid> exhaustedAutoBids = new ArrayList<>();

        for (AutoBid ab : autoBids) {
            if (ab.getMaxBid() > currentPrice) {
                validAutoBids.add(ab);
            } else {
                exhaustedAutoBids.add(ab);
            }
        }

        // Dọn dẹp các AutoBid đã đạt đỉnh
        for (AutoBid ab : exhaustedAutoBids) {
            boolean isWinner = (auction.getHighestBid() != null && 
                                auction.getHighestBid().getBidder().getUserId().equals(ab.getUserId()));
            if (!isWinner) {
                userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
                autoBidDAO.delete(ab.getAutoBidId());
                removeAutoBidFromMemory(ab);
                
                network.AuctionServer server = AuctionManager.getInstance().getServer();
                if (server != null) {
                    String msg = "CANCEL_AUTOBID_OK:" + auctionId + ":" + userDAO.getBalance(ab.getUserId());
                    server.sendToUser(ab.getUserId(), msg);
                }
            }
        }

        if (validAutoBids.isEmpty()) return;

        // Tìm người có maxBid cao nhất
        AutoBid best = null;
        for (AutoBid ab : validAutoBids) {
            if (best == null || ab.getMaxBid() > best.getMaxBid()) {
                best = ab;
            } else if (ab.getMaxBid() == best.getMaxBid()) {
                if (ab.getCreatedAt().isBefore(best.getCreatedAt())) {
                    best = ab;
                }
            }
        }

        if (best == null) return;

        // Xác định giá cần đặt để duy trì vị thế dẫn đầu
        double neededPrice = currentPrice + best.getIncrement();
        
        // Nếu có đối thủ Auto-Bid khác, giá phải vượt qua maxBid của đối thủ đó
        AutoBid secondBest = null;
        for (AutoBid ab : validAutoBids) {
            if (ab == best) continue;
            if (secondBest == null || ab.getMaxBid() > secondBest.getMaxBid()) {
                secondBest = ab;
            }
        }
        
        if (secondBest != null) {
            neededPrice = Math.max(neededPrice, secondBest.getMaxBid() + best.getIncrement());
        }

        // Không bao giờ bid quá giới hạn của bản thân
        if (neededPrice > best.getMaxBid()) {
            neededPrice = best.getMaxBid();
        }

        // Kiểm tra xem có thực sự cần đặt bid mới không
        boolean isAlreadyWinner = (auction.getHighestBid() != null && 
                                   auction.getHighestBid().getBidder().getUserId().equals(best.getUserId()));
        
        // Nếu đã thắng rồi:
        // - Nếu KHÔNG có đối thủ (secondBest == null), ta dừng lại ngay.
        // - Nếu CÓ đối thủ, ta chỉ bid tiếp nếu giá hiện tại vẫn chưa đủ để đè bẹp maxBid của đối thủ đó.
        if (isAlreadyWinner) {
            if (secondBest == null || currentPrice >= neededPrice) {
                return;
            }
        }

        if (neededPrice > currentPrice) {
            System.out.printf(">>> [AutoBid Exec] Applying bid for %s. Amount: %.2f%n", best.getUserId(), neededPrice);
            applyAutoBid(auction, best, neededPrice, bidDAO);
            executeAutoBids(auctionId, bidDAO); // Đệ quy cho đến khi ngã ngũ
        }
    }

    private void applyAutoBid(Auction auction, AutoBid ab, double amount, BidTransactionDAO bidDAO) {
        User bidder = userDAO.findById(ab.getUserId());
        if (bidder == null) return;

        model.auction.BidTransaction prevBid = auction.getHighestBid();
        String prevBidderId = (prevBid != null) ? prevBid.getBidder().getUserId() : null;
        double prevAmount   = (prevBid != null) ? prevBid.getBidAmount() : 0;

        try {
            String anonName = null;
            if (ab.isAnonymous()) {
                anonName = ConcurrentBidManager.getInstance()
                        .getAnonymousName(auction.getAuctionId(), ab.getUserId());
            }

            auction.placeBid(bidder, amount, model.auction.BidTransaction.BidType.AUTO_BID, ab.isAnonymous(), anonName);

            if (bidDAO != null) {
                model.auction.BidTransaction latest = auction.getHighestBid();
                if (latest != null) {
                    bidDaoSave(bidDAO, auction.getAuctionId(), bidder.getUserId(), amount,
                            latest.getBidType(), latest.getTimestamp(), ab.isAnonymous(), anonName);
                }
            }

            if (prevBidderId != null && !prevBidderId.equals(ab.getUserId())) {
                AutoBid prevAB = autoBidDAO.findByUserAndAuction(prevBidderId, auction.getAuctionId());
                if (prevAB == null) {
                    userDAO.unlockBalance(prevBidderId, prevAmount);
                }
            }
        } catch (Exception e) {
            System.err.println(">>> [AutoBid Error] Lỗi khi áp dụng bid tự động: " + e.getMessage());
        }
    }

    private void bidDaoSave(BidTransactionDAO dao, String auctionId, String bidderId, double amount, 
                           model.auction.BidTransaction.BidType type, java.time.LocalDateTime time, 
                           boolean isAnon, String anonName) {
        dao.save(auctionId, bidderId, amount, type, time, isAnon, anonName);
    }
}
