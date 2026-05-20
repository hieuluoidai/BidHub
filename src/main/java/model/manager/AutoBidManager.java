package model.manager;

import database.AutoBidDAO;
import database.BidTransactionDAO;
import database.UserDAO;
import exception.ExceptionMapper;
import model.auction.Auction;
import model.auction.AutoBid;
import model.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý toàn bộ logic Auto-Bid trong bộ nhớ và đồng bộ với database.
 *
 * <p>Class này chịu trách nhiệm đăng ký, hủy, thực thi và dọn dẹp Auto-Bid.
 * Các thay đổi tiền bị khóa cũng được xử lý tại đây để tránh khóa trùng tiền
 * giữa bid thủ công và Auto-Bid.
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
     * Đăng ký hoặc cập nhật Auto-Bid cho một user trong một phiên.
     *
     * <p>Khi đăng ký thành công, hệ thống khóa ngay số tiền maxBid để đảm bảo user
     * thật sự có khả năng thanh toán nếu thắng. Nếu user đang dẫn đầu bằng bid thủ công,
     * phần tiền thủ công đang khóa sẽ được mở trước để tránh khóa cùng một khoản tiền hai lần.
     */
    public synchronized String registerAutoBid(String userId, String auctionId, double maxBid, double increment) {
        // 1. Kiểm tra user tồn tại trước khi thao tác với số dư.
        User user = userDAO.findById(userId);
        if (user == null) return "USER_NOT_FOUND";
        
        // Nếu đã có Auto-Bid cũ, mở khóa khoản cũ trước để thao tác cập nhật rõ ràng.
        // Nếu bước lưu mới thất bại, hệ thống có thể khôi phục tiền cho user ngay.
        AutoBid existing = autoBidDAO.findByUserAndAuction(userId, auctionId);
        if (existing != null) {
            userDAO.unlockBalance(userId, existing.getMaxBid());
            removeAutoBidFromMemory(existing);
        } else {
            // Nếu user đang dẫn đầu bằng bid thủ công, mở khóa khoản bid đó trước
            // khi khóa maxBid mới.
            // Việc này tránh locked_balance bị cộng cả bid thủ công lẫn Auto-Bid cho cùng một user.
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

        // 2. Khóa số tiền maxBid để giữ cam kết thanh toán cho Auto-Bid.
        if (!userDAO.lockBalance(userId, maxBid)) {
            return "LOCK_FAILED";
        }

        // 3. Lưu Auto-Bid xuống DB trước, sau đó mới đưa vào bộ nhớ để trạng thái không bị lệch.
        AutoBid newAutoBid = new AutoBid(UUID.randomUUID().toString(), auctionId, userId, maxBid, increment);
        if (autoBidDAO.save(newAutoBid)) {
            auctionAutoBids.computeIfAbsent(auctionId, id -> new ArrayList<>()).add(newAutoBid);
            return "SUCCESS";
        } else {
            // Nếu DB lưu thất bại, mở khóa lại ngay để user không bị treo tiền vô lý.
            userDAO.unlockBalance(userId, maxBid);
            return "DB_ERROR";
        }
    }

    public synchronized boolean cancelAutoBid(String userId, String auctionId) {
        AutoBid existing = autoBidDAO.findByUserAndAuction(userId, auctionId);
        if (existing == null) return false;

        // 1. Mở khóa toàn bộ maxBid đang được giữ cho Auto-Bid này.
        userDAO.unlockBalance(userId, existing.getMaxBid());

        // 2. Xóa khỏi DB trước, sau đó xóa khỏi bộ nhớ để dữ liệu sau restart vẫn đúng.
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
     * Thực thi cuộc đua Auto-Bid sau khi có một bid thủ công hoặc một Auto-Bid mới.
     *
     * <p>Hàm này cần được gọi khi ConcurrentBidManager đang giữ lock của phiên.
     * Nhờ vậy toàn bộ quá trình chọn Auto-Bid thắng, đặt giá tự động và dọn Auto-Bid thua
     * không bị race condition.
     */
    public void executeAutoBids(String auctionId, BidTransactionDAO bidDAO) {
        List<AutoBid> autoBids = auctionAutoBids.get(auctionId);
        if (autoBids == null || autoBids.isEmpty()) return;

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null || !"RUNNING".equals(auction.getStatus())) return;

        double currentPrice = auction.getCurrentPrice();
        
        // 1. Phân loại: Auto-Bids còn hiệu lực và Auto-Bids đã hết hạn (Max < Current)
        List<AutoBid> validAutoBids = new ArrayList<>();
        List<AutoBid> exhaustedAutoBids = new ArrayList<>();

        for (AutoBid ab : autoBids) {
            if (ab.getMaxBid() > currentPrice) {
                validAutoBids.add(ab);
            } else {
                exhaustedAutoBids.add(ab);
            }
        }

        // 2. Dọn dẹp ngay các Auto-Bids đã thất bại (Unlock tiền cho người dùng)
        for (AutoBid ab : exhaustedAutoBids) {
            System.out.printf(">>> [AutoBid RETIRE] User %s outbid. Unlocking $%.2f%n", ab.getUserId(), ab.getMaxBid());
            userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
            autoBidDAO.delete(ab.getAutoBidId());
            removeAutoBidFromMemory(ab);
        }

        if (validAutoBids.isEmpty()) return;

        // 3. Tìm người thắng cuộc trong danh sách Auto-Bidders
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

        // 4. Nếu người thắng hiện tại có Auto-Bid tốt nhất,
        // chỉ cần nâng giá khi mức hiện tại chưa đủ để vượt người thứ hai.
        if (best.getUserId().equals(currentWinnerId)) {
            if (secondBest != null) {
                double neededPrice = secondBest.getMaxBid() + best.getIncrement();
                if (neededPrice > best.getMaxBid()) neededPrice = best.getMaxBid();
                if (neededPrice > currentPrice) {
                    applyAutoBid(auction, best, neededPrice, bidDAO);
                    // Sau khi áp dụng bid tự động, kiểm tra lại để loại Auto-Bid vừa không còn đủ giá.
                    executeAutoBids(auctionId, bidDAO); 
                }
            }
            return;
        }

        // 5. Tính toán giá nhảy tức thì để Auto-Bid tốt nhất vượt lên đúng mức cần thiết.
        double priceToJump;
        if (secondBest != null) {
            priceToJump = secondBest.getMaxBid() + best.getIncrement();
        } else {
            priceToJump = currentPrice + best.getIncrement();
        }

        if (priceToJump > best.getMaxBid()) priceToJump = best.getMaxBid();
        if (priceToJump <= currentPrice) priceToJump = currentPrice + 0.01;

        applyAutoBid(auction, best, priceToJump, bidDAO);
        
        // 6. Kiểm tra lại để loại bỏ các Auto-Bid vừa bị vượt giá bởi mức giá mới.
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
            // 1. Cập nhật phiên trong bộ nhớ trước để các observer thấy giá mới ngay lập tức.
            auction.placeBid(bidder, amount, model.auction.BidTransaction.BidType.AUTO_BID);
            
            // 2. Ghi giao dịch Auto-Bid xuống DB nếu luồng gọi có truyền DAO.
            if (bidDAO != null) {
                model.auction.BidTransaction latest = auction.getHighestBid();
                bidDAO.save(auction.getAuctionId(), bidder.getUserId(), amount,
                        latest.getBidType(), latest.getTimestamp());
            }

            // 3. Giải phóng tiền cho người bị vượt giá nếu người đó chỉ dùng bid thủ công.
            if (prevBidderId != null && !prevBidderId.equals(ab.getUserId())) {
                AutoBid prevAB = autoBidDAO.findByUserAndAuction(prevBidderId, auction.getAuctionId());
                if (prevAB == null) {
                    userDAO.unlockBalance(prevBidderId, prevAmount);
                }
            }
            
            System.out.printf(">>> [AutoBid SUCCESS] User %s auto-bid $%.2f on auction %s%n", 
                bidder.getUsername(), amount, auction.getAuctionId());
            
        } catch (Exception e) {
            System.err.println(">>> [AutoBid ERROR] " + ExceptionMapper.logMessage(e));
        }
    }

    /**
     * Dọn dẹp toàn bộ Auto-Bids khi phiên bị HỦY hoặc XÓA.
     * Giải phóng TOÀN BỘ số tiền maxBid đã cam kết cho mọi người tham gia.
     * @return Danh sách ID người dùng đã được giải phóng Auto-Bid
     *         để tránh mở khóa trùng bid thủ công.
     */
    public synchronized java.util.Set<String> cleanupForCancellation(String auctionId) {
        java.util.Set<String> unlockedUserIds = new java.util.HashSet<>();
        List<AutoBid> autoBids = auctionAutoBids.remove(auctionId);
        
        if (autoBids == null) {
            // Đề phòng trường hợp bộ nhớ bị trống, nạp lại danh sách Auto-Bid từ DB.
            autoBids = autoBidDAO.findAll().stream()
                    .filter(ab -> ab.getAuctionId().equals(auctionId))
                    .toList();
        }

        for (AutoBid ab : autoBids) {
            userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
            autoBidDAO.delete(ab.getAutoBidId());
            unlockedUserIds.add(ab.getUserId());
        }
        
        System.out.println(">>> [AutoBid CLEANUP] Đã giải phóng toàn bộ tiền cho " 
                + unlockedUserIds.size() + " Auto-Bidders của phiên " + auctionId);
        return unlockedUserIds;
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
                // Người thắng: chỉ giải phóng phần THỪA (MaxBid - Giá thắng).
                // Giữ lại đúng 'finalPrice' trong locked_balance để phục vụ thanh toán
                double excess = ab.getMaxBid() - finalPrice;
                if (excess > 0) {
                    userDAO.unlockBalance(ab.getUserId(), excess);
                    System.out.printf(
                            ">>> [AutoBid CLEANUP] Winner %s: Unlocking excess $%.2f, keeping $%.2f locked.%n",
                            ab.getUserId(), excess, finalPrice);
                }
            } else {
                // Người thua: giải phóng toàn bộ quỹ đã cam kết.
                userDAO.unlockBalance(ab.getUserId(), ab.getMaxBid());
            }
            autoBidDAO.delete(ab.getAutoBidId());
        }
    }
}
