package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import model.auction.Auction;
import model.auction.BidResult;
import model.manager.AuctionManager;
import model.manager.ConcurrentBidManager;
import model.user.User;
import model.user.Admin;
import database.UserDAO;
import database.AuctionDAO;
import database.BidTransactionDAO;

/**
 * Xử lý từng Client kết nối tới Server.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String currentUserId; // Lưu ID người dùng của connection này
    private boolean active = true;

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    public boolean isAlive() {
        return active && !socket.isClosed();
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (active) {
                Object request = in.readObject();
                handleRequest(request);
            }
        } catch (EOFException | SocketException e) {
            System.out.println(">>> Thông báo: Một Client đã thoát.");
        } catch (Exception e) {
            System.err.println(">>> Lỗi ClientHandler: " + e.getMessage());
        } finally {
            close();
        }
    }

    public String getUserId() {
        return currentUserId;
    }

    private void handleRequest(Object request) {
        // TRƯỜNG HỢP 1: Nhận Object Auction (Tạo mới hoặc Update theo luồng cũ)
        if (request instanceof Auction incomingAuction) {
            // Khi tạo phiên, cập nhật currentUserId từ owner nếu có
            if (incomingAuction.getSellerId() != null) {
                this.currentUserId = incomingAuction.getSellerId();
            }
            
            Auction existing = AuctionManager.getInstance().getAuctionById(incomingAuction.getAuctionId());

            if (existing != null) {
                AuctionManager.getInstance().updateAuction(incomingAuction);
                if (incomingAuction.getHighestBid() != null) {
                    saveBidToDatabase(incomingAuction);
                }
            } else {
                // SERVER SIDE PERSISTENCE: Khi client gửi 1 phiên mới, server phải lưu nó
                // để đảm bảo restart không mất dữ liệu.
                new database.ItemDAO().save(incomingAuction.getItem(), incomingAuction.getSellerId());
                new database.AuctionDAO().save(incomingAuction);
                
                AuctionManager.getInstance().addAuction(incomingAuction);
                System.out.println(">>> [SERVER] Đã tiếp nhận và lưu phiên mới: " + incomingAuction.getAuctionId());
            }
            // Smart Broadcast: Chỉ gửi phiên vừa thay đổi/tạo mới
            server.broadcast(incomingAuction);
        } 
        // TRƯỜNG HỢP 2: Nhận lệnh dạng chuỗi văn bản (String)
        else if (request instanceof String msg) {
            handleStringRequest(msg);
        }
    }

    private void handleStringRequest(String msg) {
        try {
            String[] parts = msg.split(":");
            if (msg.startsWith("BID:")) {
                if (parts.length >= 4) this.currentUserId = parts[3];
                handleConcurrentBid(msg);
            }
            else if (msg.startsWith("DELETE_AUCTION:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleDeleteAuction(msg);
            }
            else if (msg.startsWith("CANCEL_AUCTION:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleCancelAuction(msg);
            }
            else if (msg.startsWith("PAY_AUCTION:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handlePayAuction(msg);
            }
            else if (msg.startsWith("TOPUP:")) {
                if (parts.length >= 2) this.currentUserId = parts[1];
                handleTopUp(msg);
            }
            else if (msg.startsWith("SET_AUTOBID:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleSetAutoBid(msg);
            }
            else if (msg.startsWith("CANCEL_AUTOBID:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleCancelAutoBid(msg);
            }
            else if (msg.startsWith("IDENTIFY:")) {
                if (parts.length >= 2) {
                    this.currentUserId = parts[1];
                    System.out.println(">>> [SERVER] Connection tagged as user: " + currentUserId);
                }
            }
            else if (msg.equals("REFRESH_DATA")) {
                send(AuctionManager.getInstance().getAllAuctions());
            }
            else if (msg.startsWith("GET_MY_AUTOBID:")) {
                handleGetMyAutoBid(msg);
            }
            else if (msg.startsWith("UPDATE_PROFILE:")) {
                handleUpdateProfile(msg);
            }
            else if (msg.startsWith("CHANGE_PASSWORD:")) {
                handleChangePassword(msg);
            }
            else if (msg.startsWith("RELOAD_AUCTION:")) {
                handleReloadAuction(msg);
            }
        } catch (Exception e) {
            send("ERROR: Lệnh sai định dạng - " + e.getMessage());
        }
    }

    /**
     * Thiết lập Auto-Bid cho User.
     * Format: "SET_AUTOBID:<auctionId>:<userId>:<maxBid>:<increment>"
     */
    private void handleSetAutoBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 5) {
            send("AUTOBID_FAILED: Sai định dạng lệnh");
            return;
        }
        String auctionId = parts[1];
        String userId    = parts[2];
        double maxBid, increment;
        try {
            maxBid    = Double.parseDouble(parts[3]);
            increment = Double.parseDouble(parts[4]);
        } catch (NumberFormatException e) {
            send("AUTOBID_FAILED: Số tiền không hợp lệ");
            return;
        }

        String result = model.manager.AutoBidManager.getInstance()
                .registerAutoBid(userId, auctionId, maxBid, increment);
        
        if ("SUCCESS".equals(result)) {
            send("AUTOBID_OK:" + auctionId + ":" + (new UserDAO().getBalance(userId)));
            
            // Push thêm Balance Update chi tiết
            UserDAO userDao = new UserDAO();
            double avail = userDao.getBalance(userId);
            double locked = userDao.getLockedBalance(userId);
            send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", avail, locked));

            // Re-trigger auto-bids and BROADCAST (Crucial for real-time)
            BidTransactionDAO bidDao = new BidTransactionDAO();
            model.manager.AutoBidManager.getInstance().executeAutoBids(auctionId, bidDao);
            
            // Smart Broadcast: Chỉ gửi phiên vừa thay đổi
            Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
            if (updated != null) server.broadcast(updated);
        } else {
            send("AUTOBID_FAILED:" + result);
        }
    }

    /**
     * Hủy Auto-Bid cho User.
     * Format: "CANCEL_AUTOBID:<auctionId>:<userId>"
     */
    private void handleCancelAutoBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("CANCEL_AUTOBID_FAILED: Sai định dạng lệnh");
            return;
        }
        String auctionId = parts[1];
        String userId    = parts[2];

        boolean ok = model.manager.AutoBidManager.getInstance().cancelAutoBid(userId, auctionId);
        if (ok) {
            send("CANCEL_AUTOBID_OK:" + auctionId + ":" + (new UserDAO().getBalance(userId)));
        } else {
            send("CANCEL_AUTOBID_FAILED: Không tìm thấy Auto-Bid để hủy");
        }
    }

    /**
     * Lấy cấu hình Auto-Bid của User hiện tại cho 1 phiên.
     * Format: "GET_MY_AUTOBID:<auctionId>:<userId>"
     */
    private void handleGetMyAutoBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String auctionId = parts[1];
        String userId    = parts[2];

        model.auction.AutoBid ab = new database.AutoBidDAO().findByUserAndAuction(userId, auctionId);
        if (ab != null) {
            send(String.format("MY_AUTOBID:%s:%.2f:%.2f", auctionId, ab.getMaxBid(), ab.getIncrement()));
        } else {
            send("MY_AUTOBID_NONE:" + auctionId);
        }
    }

    /**
     * Xóa 1 phiên đấu giá khỏi hệ thống.
     * Format: "DELETE_AUCTION:<auctionId>:<requesterId>"
     */
    private void handleDeleteAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("DELETE_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        User requester = new UserDAO().findById(requesterId);
        if (requester == null) {
            send("DELETE_FAILED: Không tìm thấy người yêu cầu");
            return;
        }

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null) {
            send("DELETE_FAILED: Phiên không tồn tại");
            return;
        }

        boolean ok;
        if (requester instanceof Admin) {
            // Admin có quyền xóa mọi phiên
            ok = new AuctionDAO().delete(auctionId);
        } else {
            // Seller: Không cho xóa phiên đã kết thúc
            if ("FINISHED".equals(auction.getStatus())) {
                send("DELETE_FAILED: Phiên đã kết thúc, không thể xóa!");
                return;
            }
            int result = new AuctionDAO().deleteIfOwner(auctionId, requesterId);
            if (result == 0) { send("DELETE_FAILED: Bạn không có quyền xóa phiên này"); return; }
            ok = (result == 1);
        }

        if (ok) {
            // GIẢI PHÓNG TIỀN cho TOÀN BỘ mọi người đã tham gia khi xóa phiên
            unlockAllBidders(auction);

            AuctionManager.getInstance().removeAuction(auctionId);
            System.out.println(">>> Phiên " + auctionId + " đã bị xóa bởi " + requester.getUsername());
            send("DELETE_OK:" + auctionId);
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        } else {
            send("DELETE_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    /**
     * Cập nhật thông tin hồ sơ User.
     * Format: "UPDATE_PROFILE:<userId>:<email>:<phone>:<dob>"
     */
    private void handleUpdateProfile(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 5) return;
        
        String userId = parts[1];
        String email  = parts[2];
        String phone  = parts[3];
        String dobStr = parts[4];

        database.UserDAO dao = new database.UserDAO();
        boolean ok = dao.updateProfile(userId, email, phone, dobStr);
        
        if (ok) {
            send("UPDATE_PROFILE_OK");
        } else {
            send("UPDATE_PROFILE_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    /**
     * Đổi mật khẩu User.
     * Format: "CHANGE_PASSWORD:<userId>:<oldPass>:<newPass>"
     */
    private void handleChangePassword(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) return;

        String userId  = parts[1];
        String oldPass = parts[2];
        String newPass = parts[3];

        database.UserDAO dao = new database.UserDAO();
        model.user.User user = dao.findById(userId);

        if (user == null) {
            send("CHANGE_PASSWORD_FAILED: User không tồn tại");
            return;
        }
if (!utils.PasswordUtils.verify(oldPass, user.getPassword())) {
    send("CHANGE_PASSWORD_FAILED: Mật khẩu cũ không chính xác");
    return;
}

// Hash mật khẩu mới
String hashedNew = utils.PasswordUtils.hash(newPass);

        boolean ok = dao.updatePassword(userId, hashedNew);

        if (ok) {
            send("CHANGE_PASSWORD_OK");
        } else {
            send("CHANGE_PASSWORD_FAILED: Lỗi DB");
        }
    }

    /**
     * Reload 1 phiên từ DB sau khi Client cập nhật DB (thông qua DAO).
     */
    private void handleReloadAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 2) {
            send("RELOAD_FAILED: Sai định dạng");
            return;
        }
        String auctionId = parts[1];
        AuctionManager.getInstance().reloadAuctionFromDB(auctionId);
        send("RELOAD_OK:" + auctionId);
        server.broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    /**
     * GIẢI PHÓNG TIỀN cho TẤT CẢ mọi người đã tham gia đấu giá trong phiên này
     * (bao gồm cả người thắng hiện tại và những người dùng Auto-Bid).
     */
    private void unlockAllBidders(Auction auction) {
        String auctionId = auction.getAuctionId();
        
        // 1. Giải phóng tiền cho TẤT CẢ những người dùng AutoBid trong phiên này (và xóa AutoBid)
        java.util.Set<String> autoBidderIds = model.manager.AutoBidManager.getInstance()
                .cleanupForCancellation(auctionId);

        // 2. Kiểm tra nếu người đang dẫn đầu là Manual Bid (không có AutoBid)
        // thì phải giải phóng thủ công số tiền họ đang cam kết.
        if (auction.getHighestBid() != null) {
            String bidderId = auction.getHighestBid().getBidder().getUserId();
            double amount   = auction.getHighestBid().getBidAmount();
            
            // Nếu người này không nằm trong danh sách Auto-Bid vừa được giải phóng
            // (vì nếu có Auto-Bid, tiền maxBid của họ đã được giải phóng ở bước 1)
            if (!autoBidderIds.contains(bidderId)) {
                new UserDAO().unlockBalance(bidderId, amount);
                System.out.printf(">>> [UNLOCK] Giải phóng Manual Bid $%.2f cho %s do phiên %s bị hủy/xóa%n",
                        amount, bidderId, auctionId);
            }
        }
    }

    private void handleCancelAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("CANCEL_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        User requester = new UserDAO().findById(requesterId);
        if (requester == null) {
            send("CANCEL_FAILED: Không tìm thấy người yêu cầu");
            return;
        }

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null) {
            send("CANCEL_FAILED: Phiên không tồn tại");
            return;
        }

        // GIẢI PHÓNG TIỀN cho TOÀN BỘ mọi người trước khi hủy
        unlockAllBidders(auction);

        boolean isAdmin = (requester instanceof Admin);
        int result = new AuctionDAO().cancelAuction(auctionId, requesterId, isAdmin);

        switch (result) {
            case 1 -> {
                // Cập nhật memory để client thấy ngay status mới
                auction.setStatus("CANCELED");
                // Giải phóng lock nếu phiên đang RUNNING bị hủy
                model.manager.ConcurrentBidManager.getInstance().releaseLock(auctionId);
                System.out.println(">>> Phiên " + auctionId + " đã bị HỦY bởi "
                        + requester.getUsername());
                send("CANCEL_OK:" + auctionId);
                server.broadcast(AuctionManager.getInstance().getAllAuctions());
            }
            case 0  -> send("CANCEL_FAILED: Bạn không có quyền hủy phiên này");
            case -1 -> send("CANCEL_FAILED: Trạng thái phiên không cho phép hủy "
                    + "(chỉ OPEN/RUNNING với seller, hoặc OPEN/RUNNING/FINISHED với admin)");
            default -> send("CANCEL_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    private void unlockHighestBidder(Auction auction) {
        if (auction.getHighestBid() != null) {
            String winnerId = auction.getHighestBid().getBidder().getUserId();
            double amount   = auction.getHighestBid().getBidAmount();
            
            // Nếu người này có AutoBid, nó sẽ được unlock bởi AutoBidManager.cleanup hoặc cancel
            // Tuy nhiên để an toàn, ta check manual bid unlock tại đây
            model.auction.AutoBid ab = new database.AutoBidDAO().findByUserAndAuction(winnerId, auction.getAuctionId());
            if (ab == null) {
                new UserDAO().unlockBalance(winnerId, amount);
                System.out.printf(">>> [UNLOCK] Giải phóng $%.2f cho %s do hủy/xóa phiên %s%n",
                        amount, winnerId, auction.getAuctionId());
            } else {
                // Nếu có AutoBid, ta nhờ AutoBidManager dọn dẹp (bao gồm unlock maxBid)
                model.manager.AutoBidManager.getInstance().cancelAutoBid(winnerId, auction.getAuctionId());
            }
        }
    }

    /**
     * Thanh toán phiên đấu giá đã FINISHED. Chỉ winner mới được gọi.
     * Format: "PAY_AUCTION:<auctionId>:<requesterId>"
     *
     * Quy trình ATOMIC (transaction trong UserDAO.transferAtomic):
     *   1. Tra winner_id, seller_id, final_price từ DB
     *   2. Kiểm tra requester có đúng là winner không
     *   3. Transfer atomic (lock row, kiểm balance, trừ winner, cộng seller)
     *   4. Nếu OK → markAsPaid → broadcast
     *
     * Phản hồi:
     *   PAY_OK:<auctionId>:<newBalance>      — thành công, kèm số dư mới của winner
     *   PAY_FAILED:<reason>                  — thiếu tiền hoặc không phải winner
     */
    private void handlePayAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("PAY_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        // 1. Lấy info winner từ DB
        AuctionDAO auctionDao = new AuctionDAO();
        String[] winnerInfo = auctionDao.findWinnerInfo(auctionId);
        if (winnerInfo == null) {
            send("PAY_FAILED: Phiên chưa kết thúc hoặc không có người thắng");
            return;
        }

        String winnerId  = winnerInfo[0];
        String sellerId  = winnerInfo[1];
        double finalPrice;
        try {
            finalPrice = Double.parseDouble(winnerInfo[2]);
        } catch (NumberFormatException e) {
            send("PAY_FAILED: Dữ liệu phiên bị lỗi");
            return;
        }

        // 2. Verify requester là winner thật sự (chống tampering)
        if (!winnerId.equals(requesterId)) {
            send("PAY_FAILED: Bạn không phải người thắng phiên này");
            return;
        }

        // 3. Transfer atomic (từ locked_balance sang seller balance)
        UserDAO userDao = new UserDAO();
        double currentLocked = userDao.getLockedBalance(winnerId);
        if (currentLocked < finalPrice) {
            // Trường hợp hy hữu: locked_balance < giá thắng (có thể do Manual Bid đè lên AutoBid nhưng lock hụt)
            // Ta thử dùng transferAtomic thường nếu user có đủ tiền ở balance chính
            double currentBalance = userDao.getBalance(winnerId);
            if (currentBalance >= finalPrice) {
                if (userDao.transferAtomic(winnerId, sellerId, finalPrice)) {
                    finalizePayment(auctionId, winnerId, sellerId, finalPrice, userDao);
                    return;
                }
            }
            send(String.format("PAY_FAILED: Số dư cam kết không đủ (cần $%.2f, bạn có $%.2f locked)",
                    finalPrice, currentLocked));
            return;
        }

        boolean transferOk = userDao.transferFromLockedAtomic(winnerId, sellerId, finalPrice);
        if (!transferOk) {
            send("PAY_FAILED: Lỗi chuyển tiền từ quỹ cam kết");
            return;
        }

        finalizePayment(auctionId, winnerId, sellerId, finalPrice, userDao);
    }

    private void finalizePayment(String auctionId, String winnerId, String sellerId, double finalPrice, UserDAO userDao) {
        AuctionDAO auctionDao = new AuctionDAO();
        // 4. Cập nhật status PAID
        boolean paidOk = auctionDao.markAsPaid(auctionId);
        if (!paidOk) {
            System.err.println(">>> [PAY] DB inconsistent! Transfer OK nhưng PAID fail cho phiên " + auctionId);
            send("PAY_FAILED: Lỗi cập nhật trạng thái phiên");
            return;
        }

        // 5. Cập nhật memory + broadcast
        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction != null) auction.setStatus("PAID");

        double newBalance = userDao.getBalance(winnerId);
        System.out.printf(">>> [PAID] Phiên %s — %s trả $%.2f cho %s | balance còn $%.2f%n",
                auctionId, winnerId, finalPrice, sellerId, newBalance);

        send("PAY_OK:" + auctionId + ":" + newBalance);
        
        // Push thêm Balance Update chi tiết (avail + locked) cho người thắng
        double locked = userDao.getLockedBalance(winnerId);
        send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", newBalance, locked));

        server.broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    /**
     * Nạp tiền vào ví user.
     * Format: "TOPUP:<userId>:<amount>"
     *
     * Mô phỏng nạp tiền: server "xử lý giao dịch" trong 1.5s rồi cộng vào balance.
     * Tương lai sẽ tích hợp payment gateway thật.
     *
     * Phản hồi:
     *   TOPUP_OK:<newBalance>      — thành công
     *   TOPUP_FAILED:<reason>      — số tiền không hợp lệ / user không tồn tại
     */
    private void handleTopUp(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("TOPUP_FAILED: Lệnh sai định dạng");
            return;
        }
        String userId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            send("TOPUP_FAILED: Số tiền không hợp lệ");
            return;
        }

        if (amount <= 0) {
            send("TOPUP_FAILED: Số tiền phải lớn hơn 0");
            return;
        }
        if (amount > 1_000_000_000) {
            send("TOPUP_FAILED: Số tiền vượt quá giới hạn cho phép ($1,000,000,000)");
            return;
        }

        UserDAO userDao = new UserDAO();
        User user = userDao.findById(userId);
        if (user == null) {
            send("TOPUP_FAILED: Người dùng không tồn tại");
            return;
        }

        // Mô phỏng "xử lý giao dịch" — sleep 1.5s
        try {
            System.out.println(">>> [TOPUP] Đang xử lý $" + amount + " cho " + user.getUsername() + "...");
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Cộng tiền: lấy balance hiện tại + amount, set lại
        // (không dùng transferAtomic vì đây không phải transfer giữa 2 user)
        double current = userDao.getBalance(userId);
        double newBalance = current + amount;
        boolean ok = userDao.setBalance(userId, newBalance);

        if (ok) {
            System.out.printf(">>> [TOPUP] %s nạp $%.2f thành công | balance: $%.2f → $%.2f%n",
                    user.getUsername(), amount, current, newBalance);
            send("TOPUP_OK:" + newBalance);
            
            // Push thêm Balance Update chi tiết
            double locked = userDao.getLockedBalance(userId);
            send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", newBalance, locked));
        } else {
            send("TOPUP_FAILED: Lỗi cập nhật số dư");
        }
    }

    /**
     * Xử lý bid đồng thời cực nhanh và an toàn.
     * Format: "BID:<auctionId>:<amount>:<bidderId>"
     */
    private void handleConcurrentBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) {
            send(BidResult.failure("?", 0, "Lệnh BID sai định dạng!"));
            return;
        }

        String auctionId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            send(BidResult.failure(auctionId, 0, "Số tiền không hợp lệ!"));
            return;
        }
        
        String bidderId = parts[3];
        User bidder = new UserDAO().findById(bidderId);
        if (bidder == null) {
            send(BidResult.failure(auctionId, amount, "Người dùng không tồn tại!"));
            return;
        }

        // Trước khi bid, lấy thông tin người đang dẫn đầu để notify nếu họ bị outbid
        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        String prevBidderId = (auction != null && auction.getHighestBid() != null) 
                                ? auction.getHighestBid().getBidder().getUserId() : null;

        // Tích hợp luồng lưu DB Atomic của Hiếu
        BidTransactionDAO bidDao = new BidTransactionDAO();
        BidResult result = ConcurrentBidManager.getInstance()
                .processBid(auctionId, amount, bidder, bidDao);

        System.out.printf(">>> [BID] %s $%.2f phiên %s → %s | %s%n",
                bidder.getUsername(), amount, auctionId, result.getStatus(),
                ConcurrentBidManager.getInstance().metricsSummary());

        // Phản hồi riêng cho Client vừa đặt giá
        send(result);

        // Nếu bid thành công, thông báo giá mới cho toàn bộ Client và cập nhật số dư các bên liên quan
        if (result.isSuccess()) {
            UserDAO userDao = new UserDAO();
            
            // 1. Cập nhật số dư cho người vừa BID thành công (Current Bidder)
            double availCurrent = userDao.getBalance(bidderId);
            double lockedCurrent = userDao.getLockedBalance(bidderId);
            send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", availCurrent, lockedCurrent));

            // 2. Cập nhật số dư cho người vừa bị OUTBID (nếu có và khác người hiện tại)
            if (prevBidderId != null && !prevBidderId.equals(bidderId)) {
                double availPrev = userDao.getBalance(prevBidderId);
                double lockedPrev = userDao.getLockedBalance(prevBidderId);
                server.sendToUser(prevBidderId, String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", availPrev, lockedPrev));
            }

            // 3. Smart Broadcast: Chỉ gửi phiên vừa thay đổi
            Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
            if (updated != null) server.broadcast(updated);
        }
    }

    /**
     * Lưu giao dịch xuống MySQL (Dành cho luồng Object cũ).
     */
    private void saveBidToDatabase(Auction auction) {
        BidTransactionDAO bidDao = new BidTransactionDAO();
        String auctionId = auction.getAuctionId();
        model.auction.BidTransaction highest = auction.getHighestBid();
        
        if (highest == null) return;
        
        String bidderId = highest.getBidder().getUserId();
        double amount = highest.getBidAmount();
        java.time.LocalDateTime time = highest.getTimestamp();
        model.auction.BidTransaction.BidType type = highest.getBidType();

        if (bidDao.save(auctionId, bidderId, amount, type, time)) {
            System.out.println(">>> Backup thành công giá $" + amount + " cho phiên " + auctionId);
        }
    }

    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            active = false;
        }
    }

    private void close() {
        active = false;
        try {
            if (socket != null) socket.close();
            server.removeObserver(this); // Rút tên khỏi danh sách theo dõi của Server
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}