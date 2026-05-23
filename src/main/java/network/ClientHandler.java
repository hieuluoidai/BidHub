package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import exception.ErrorCode;
import exception.ErrorResponse;
import exception.ExceptionMapper;
import model.auction.Auction;
import model.auction.BidResult;
import model.manager.AuctionManager;
import model.manager.ConcurrentBidManager;
import model.user.User;
import model.user.Admin;
import database.UserDAO;
import database.AuctionDAO;
import database.BidTransactionDAO;
import utils.NotificationService;
import model.notification.Notification;

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
                try {
                    handleRequest(request);
                } catch (Exception e) {
                    System.err.println(">>> Lỗi xử lý request: " + ExceptionMapper.logMessage(e));
                    send(ErrorResponse.from(e));
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println(">>> Thông báo: Một Client đã thoát.");
        } catch (Exception e) {
            System.err.println(">>> Lỗi ClientHandler: " + ExceptionMapper.logMessage(e));
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
                // Lưu phía server: khi client gửi một phiên mới, server phải ghi DB ngay
                // để restart không làm mất phiên vừa tạo.
                new database.ItemDAO().save(incomingAuction.getItem(), incomingAuction.getSellerId());
                new database.AuctionDAO().save(incomingAuction);
                
                AuctionManager.getInstance().addAuction(incomingAuction);
                System.out.println(">>> [SERVER] Đã tiếp nhận và lưu phiên mới: " + incomingAuction.getAuctionId());

                // Gửi Notification ITEM_POSTED cho Seller
                NotificationService.notifyUser(server, incomingAuction.getSellerId(),
                    Notification.Type.ITEM_POSTED,
                    "Đăng bán thành công",
                    String.format("Sản phẩm \"%s\" của bạn đã được đăng lên hệ thống.", 
                        incomingAuction.getItem().getItemName()));

                // Gửi Notification cho Admin
                NotificationService.notifyAdmins(server,
                    Notification.Type.ADMIN_NEW_AUCTION,
                    "Phiên đấu giá mới",
                    String.format("Phiên đấu giá cho \"%s\" vừa được tạo bởi %s.", 
                        incomingAuction.getItem().getItemName(), incomingAuction.getSellerId()));
            }
            // Broadcast tối ưu: chỉ gửi phiên vừa thay đổi/tạo mới thay vì gửi lại toàn bộ danh sách.
            server.broadcast(incomingAuction);
        // TRƯỜNG HỢP 2: Nhận lệnh dạng chuỗi văn bản (String)
        } else if (request instanceof String msg) {
            handleStringRequest(msg);
        } else {
            send(ErrorResponse.of(ErrorCode.COMMAND_FORMAT_INVALID,
                    "Server không hỗ trợ kiểu dữ liệu request này."));
        }
    }

    private void handleStringRequest(String msg) {
        try {
            String[] parts = msg.split(":");
            if (msg.startsWith("BID:")) {
                if (parts.length >= 4) this.currentUserId = parts[3];
                handleConcurrentBid(msg);
            } else if (msg.startsWith("DELETE_AUCTION:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleDeleteAuction(msg);
            } else if (msg.startsWith("CANCEL_AUCTION:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleCancelAuction(msg);
            } else if (msg.startsWith("PAY_AUCTION:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handlePayAuction(msg);
            } else if (msg.startsWith("TOPUP:")) {
                if (parts.length >= 2) this.currentUserId = parts[1];
                handleTopUp(msg);
            } else if (msg.startsWith("SET_AUTOBID:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleSetAutoBid(msg);
            } else if (msg.startsWith("CANCEL_AUTOBID:")) {
                if (parts.length >= 3) this.currentUserId = parts[2];
                handleCancelAutoBid(msg);
            } else if (msg.startsWith("IDENTIFY:")) {
                if (parts.length >= 2) {
                    this.currentUserId = parts[1];
                    System.out.println(">>> [SERVER] Connection tagged as user: " + currentUserId);
                }
            } else if (msg.equals("REFRESH_DATA")) {
                send(AuctionManager.getInstance().getAllAuctions());
            } else if (msg.startsWith("GET_MY_AUTOBID:")) {
                handleGetMyAutoBid(msg);
            } else if (msg.startsWith("UPDATE_PROFILE:")) {
                handleUpdateProfile(msg);
            } else if (msg.startsWith("UPDATE_AVATAR:")) {
                handleUpdateAvatar(msg);
            } else if (msg.startsWith("CHANGE_PASSWORD:")) {
                handleChangePassword(msg);
            } else if (msg.startsWith("RELOAD_AUCTION:")) {
                handleReloadAuction(msg);
            } else if (msg.startsWith("NEW_USER_REGISTERED:")) {
                handleNewUserRegistered(msg);
            } else if (msg.startsWith("REQUEST_SELLER:")) {
                handleRequestSeller(msg);
            } else if (msg.startsWith("APPROVE_SELLER:")) {
                handleApproveSeller(msg);
            } else if (msg.startsWith("REVOKE_SELLER:")) {
                handleRevokeSeller(msg);
            } else if (msg.startsWith("FETCH_NOTIFICATIONS:")) {
                handleFetchNotifications(msg);
            } else if (msg.startsWith("MARK_NOTIFICATION_READ:")) {
                handleMarkNotificationRead(msg);
            } else if (msg.startsWith("MARK_ALL_NOTIFICATIONS_READ:")) {
                handleMarkAllNotificationsRead(msg);
            } else if (msg.startsWith("DEPOSIT_REQUEST:")) {
                if (parts.length >= 2) this.currentUserId = parts[1];
                handleDepositRequest(msg);
            } else if (msg.startsWith("DEPOSIT_REVIEW:")) {
                handleDepositReview(msg);
            } else if (msg.startsWith("CHAT_SEND:")) {
                handleChatSend(msg);
            } else if (msg.startsWith("CHAT_FETCH:")) {
                handleChatFetch(msg);
            } else if (msg.startsWith("CHAT_FETCH_LIST:")) {
                handleChatFetchList(msg);
            } else if (msg.startsWith("CHAT_MARK_READ:")) {
                handleChatMarkRead(msg);
            } else if (msg.startsWith("CHAT_LIKE:")) {
                handleChatLike(msg);
            } else if (msg.startsWith("CHAT_RECALL:")) {
                handleChatRecall(msg);
            } else if (msg.startsWith("FRIEND_REQUEST:")) {
                handleFriendRequest(msg);
            } else if (msg.startsWith("FRIEND_ACCEPT:")) {
                handleFriendAccept(msg);
            } else if (msg.startsWith("FRIEND_DECLINE:")) {
                handleFriendDecline(msg);
            } else if (msg.startsWith("FRIEND_LIST:")) {
                handleFriendList(msg);
            } else if (msg.startsWith("FRIEND_STATUS:")) {
                handleFriendStatus(msg);
            } else if (msg.startsWith("USER_SEARCH:")) {
                handleUserSearch(msg);
            }
        } catch (Exception e) {
            System.err.println(">>> Lỗi xử lý lệnh: " + ExceptionMapper.logMessage(e));
            send(ExceptionMapper.protocolMessage("ERROR", e));
        }
    }

    /**
     * Thiết lập Auto-Bid cho User.
     * Định dạng: "SET_AUTOBID:<auctionId>:<userId>:<maxBid>:<increment>:<isAnonymous>"
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
        boolean isAnon = parts.length >= 6 && Boolean.parseBoolean(parts[5]);

        String result = model.manager.AutoBidManager.getInstance()
                .registerAutoBid(userId, auctionId, maxBid, increment, isAnon);
        
        if ("SUCCESS".equals(result)) {
            send("AUTOBID_OK:" + auctionId + ":" + (new UserDAO().getBalance(userId)));
            
            // Push thêm Balance Update chi tiết
            UserDAO userDao = new UserDAO();
            double avail = userDao.getBalance(userId);
            double locked = userDao.getLockedBalance(userId);
            send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", avail, locked));

            // Kích hoạt lại Auto-Bid và broadcast để các client thấy giá mới theo thời gian thực.
            BidTransactionDAO bidDao = new BidTransactionDAO();
            model.manager.AutoBidManager.getInstance().executeAutoBids(auctionId, bidDao);
            
            // Broadcast tối ưu: chỉ gửi phiên vừa thay đổi.
            Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
            if (updated != null) server.broadcast(updated);
        } else {
            send("AUTOBID_FAILED:" + result);
        }
    }

    /**
     * Hủy Auto-Bid cho User.
     * Định dạng: "CANCEL_AUTOBID:<auctionId>:<userId>"
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
     * Định dạng: "GET_MY_AUTOBID:<auctionId>:<userId>"
     */
    private void handleGetMyAutoBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String auctionId = parts[1];
        String userId    = parts[2];

        model.auction.AutoBid ab = new database.AutoBidDAO().findByUserAndAuction(userId, auctionId);
        if (ab != null) {
            send(String.format(java.util.Locale.US, "MY_AUTOBID:%s:%.2f:%.2f:%b", 
                auctionId, ab.getMaxBid(), ab.getIncrement(), ab.isAnonymous()));
        } else {
            send("MY_AUTOBID_NONE:" + auctionId);
        }
    }

    /**
     * Xóa 1 phiên đấu giá khỏi hệ thống.
     * Định dạng: "DELETE_AUCTION:<auctionId>:<requesterId>"
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
            if (result == 0) {
                send("DELETE_FAILED: Bạn không có quyền xóa phiên này");
                return;
            }
            ok = (result == 1);
        }

        if (ok) {
            // GIẢI PHÓNG TIỀN cho TOÀN BỘ mọi người đã tham gia khi xóa phiên
            unlockAllBidders(auction);

            // Gửi Notification cho Seller
            NotificationService.notifyUser(server, auction.getSellerId(),
                Notification.Type.AUCTION_CANCELED,
                "Phiên đấu giá bị xóa",
                String.format("Phiên \"%s\" của bạn đã bị xóa khỏi hệ thống.", 
                    (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId));

            // Gửi Notification REFUND cho người đang dẫn đầu (nếu có)
            if (auction.getHighestBid() != null) {
                String winnerId = auction.getHighestBid().getBidder().getUserId();
                NotificationService.notifyUser(server, winnerId,
                    Notification.Type.WALLET_REFUND,
                    "Hoàn tiền cam kết",
                    String.format("Phiên \"%s\" bị xóa. Số tiền cam kết %,.0f ₫ đã được hoàn trả vào ví của bạn.",
                        (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId,
                        auction.getHighestBid().getBidAmount()));
            }

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
     * Định dạng: "UPDATE_PROFILE:<userId>:<email>:<phone>:<dob>"
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
     * Cập nhật Avatar User.
     * Định dạng: "UPDATE_AVATAR:<userId>:<avatarPath>"
     */
    private void handleUpdateAvatar(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        
        String userId = parts[1];
        String avatarPath = parts[2];

        // Do path có thể chứa dấu ":" nếu có scheme file: //, nhưng ở đây db lưu relative path (items/...) 
        // Tuy nhiên tốt nhất ghép lại từ phần tử thứ 2 trở đi
        if (parts.length > 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                sb.append(parts[i]);
                if (i < parts.length - 1) sb.append(":");
            }
            avatarPath = sb.toString();
        }

        database.UserDAO dao = new database.UserDAO();
        boolean ok = dao.updateAvatar(userId, avatarPath);
        
        if (ok) {
            send("UPDATE_AVATAR_OK");
        } else {
            send("UPDATE_AVATAR_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    /**
     * Đổi mật khẩu User.
     * Định dạng: "CHANGE_PASSWORD:<userId>:<oldPass>:<newPass>"
     */
    private void handleChangePassword(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) return;

        String userId  = parts[1];
        String oldPass = parts[2];
        String newPass = parts[3];

        // Kiểm tra bảo mật: chỉ cho phép user đổi mật khẩu của chính mình.
        if (this.currentUserId == null || !this.currentUserId.equals(userId)) {
            send("CHANGE_PASSWORD_FAILED: Bạn không có quyền đổi mật khẩu của người khác!");
            return;
        }

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

// Băm mật khẩu mới trước khi lưu DB.
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

        // 2. Kiểm tra nếu người đang dẫn đầu là bid thủ công, tức không có AutoBid.
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

                // Gửi Notification cho Seller
                NotificationService.notifyUser(server, auction.getSellerId(),
                    Notification.Type.AUCTION_CANCELED,
                    "Phiên đấu giá bị hủy",
                    String.format("Phiên \"%s\" của bạn đã bị hủy.", 
                        (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId));

                // Gửi Notification REFUND cho người đang dẫn đầu (nếu có)
                if (auction.getHighestBid() != null) {
                    String winnerId = auction.getHighestBid().getBidder().getUserId();
                    NotificationService.notifyUser(server, winnerId,
                        Notification.Type.WALLET_REFUND,
                        "Hoàn tiền cam kết",
                        String.format("Phiên \"%s\" bị hủy. Số tiền cam kết %,.0f ₫ đã được hoàn trả vào ví của bạn.",
                            (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId,
                            auction.getHighestBid().getBidAmount()));
                }

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
            // Tuy nhiên để an toàn, vẫn kiểm tra mở khóa bid thủ công tại đây.
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
     * Định dạng: "PAY_AUCTION:<auctionId>:<requesterId>"
     *
     * Quy trình nguyên tử (transaction trong UserDAO.transferAtomic):
     *   1. Tra winner_id, seller_id, final_price từ DB
     *   2. Kiểm tra requester có đúng là winner không
     *   3. Chuyển tiền nguyên tử (khóa row, kiểm balance, trừ winner, cộng seller)
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

        // 2. Xác minh requester là winner thật sự để chống client tự sửa request.
        if (!winnerId.equals(requesterId)) {
            send("PAY_FAILED: Bạn không phải người thắng phiên này");
            return;
        }

        // 3. Chuyển tiền nguyên tử từ locked_balance của winner sang balance của seller.
        UserDAO userDao = new UserDAO();
        double currentLocked = userDao.getLockedBalance(winnerId);
        if (currentLocked < finalPrice) {
            // Trường hợp hy hữu: locked_balance < giá thắng do bid thủ công đè lên AutoBid nhưng khóa hụt.
            // Ta thử dùng transferAtomic thường nếu user có đủ tiền ở balance chính
            double currentBalance = userDao.getBalance(winnerId);
            if (currentBalance >= finalPrice) {
                if (userDao.transferAtomic(winnerId, sellerId, finalPrice)) {
                    finalizePayment(auctionId, winnerId, sellerId, finalPrice, userDao);
                    return;
                }
            }
            send(String.format("PAY_FAILED: Số dư cam kết không đủ (cần %,.0f ₫, bạn có %,.0f ₫ locked)",
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

        // 5. LƯU LỊCH SỬ GIAO DỊCH VÍ CHO CẢ 2 BÊN
        database.WalletTransactionDAO walletDao = new database.WalletTransactionDAO();
        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        String itemName = (auction != null && auction.getItem() != null) ? auction.getItem().getItemName() : auctionId;
        
        // Winner chi tiền
        walletDao.save(winnerId, -finalPrice, 
            model.auction.WalletTransaction.TransactionType.PAYMENT, 
            "Thanh toán sản phẩm: " + itemName);
        
        // Seller nhận tiền
        walletDao.save(sellerId, finalPrice, 
            model.auction.WalletTransaction.TransactionType.EARNING, 
            "Tiền bán sản phẩm: " + itemName);

        // 5b. Gửi Persistent Notifications
        NotificationService.notifyUser(server, winnerId,
            Notification.Type.WALLET_PAYMENT,
            "Thanh toán thành công",
            String.format("Bạn đã thanh toán %,.0f ₫ cho sản phẩm \"%s\".", finalPrice, itemName));
        
        NotificationService.notifyUser(server, sellerId,
            Notification.Type.WALLET_EARNING,
            "Bạn nhận được tiền bán hàng",
            String.format("Người mua đã thanh toán %,.0f ₫ cho sản phẩm \"%s\" của bạn.", finalPrice, itemName));

        // 6. Cập nhật bộ nhớ và broadcast trạng thái mới cho client.
        if (auction != null) auction.setStatus("PAID");

        double newBalance = userDao.getBalance(winnerId);
        System.out.printf(">>> [PAID] Phiên %s — %s trả $%.2f cho %s | balance còn $%.2f%n",
                auctionId, winnerId, finalPrice, sellerId, newBalance);

        send("PAY_OK:" + auctionId + ":" + newBalance);
        
        // Push thêm Balance Update chi tiết (avail + locked) cho người thắng
        double locked = userDao.getLockedBalance(winnerId);
        send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", newBalance, locked));
        
        // Gửi cập nhật số dư cho seller vì họ vừa nhận tiền.
        double sellerAvail = userDao.getBalance(sellerId);
        double sellerLocked = userDao.getLockedBalance(sellerId);
        server.sendToUser(sellerId, String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", sellerAvail, sellerLocked));

        server.broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    /**
     * Nạp tiền vào ví user.
     * Định dạng: "TOPUP:<userId>:<amount>"
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

        System.out.println(">>> [TOPUP] Đang xử lý $" + amount + " cho " + user.getUsername() + "...");

        // Cộng tiền: lấy balance hiện tại + amount, set lại
        // (không dùng transferAtomic vì đây không phải transfer giữa 2 user)
        double current = userDao.getBalance(userId);
        double newBalance = current + amount;
        boolean ok = userDao.setBalance(userId, newBalance);

        if (ok) {
            // LƯU LỊCH SỬ GIAO DỊCH VÍ
            new database.WalletTransactionDAO().save(userId, amount, 
                model.auction.WalletTransaction.TransactionType.TOPUP, 
                "Nạp tiền vào ví qua hệ thống");

            // Gửi Persistent Notification
            NotificationService.notifyUser(server, userId,
                Notification.Type.WALLET_TOPUP,
                "Nạp tiền thành công",
                String.format("Bạn đã nạp thành công %,.0f ₫ vào ví.", amount));

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
     * Định dạng: "BID:<auctionId>:<amount>:<bidderId>[:<isAnonymous>]"
     */
    private void handleConcurrentBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) {
            send(BidResult.failure("?", 0, ErrorCode.COMMAND_FORMAT_INVALID,
                    "Lệnh BID sai định dạng."));
            return;
        }

        String auctionId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            send(BidResult.failure(auctionId, 0, ErrorCode.VALIDATION_ERROR,
                    "Số tiền không hợp lệ."));
            return;
        }
        
        String bidderId = parts[3];
        boolean isAnonymous = false;
        if (parts.length >= 5) {
            isAnonymous = Boolean.parseBoolean(parts[4]);
        }

        User bidder = new UserDAO().findById(bidderId);
        if (bidder == null) {
            send(BidResult.failure(auctionId, amount, ErrorCode.USER_NOT_FOUND,
                    "Người dùng không tồn tại."));
            return;
        }

        // Trước khi bid, lấy thông tin người đang dẫn đầu để thông báo nếu họ bị vượt giá.
        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        String prevBidderId = (auction != null && auction.getHighestBid() != null) 
                                ? auction.getHighestBid().getBidder().getUserId() : null;

        // Tích hợp luồng lưu DB Atomic
        BidTransactionDAO bidDao = new BidTransactionDAO();
        BidResult result = ConcurrentBidManager.getInstance()
                .processBid(auctionId, amount, bidder, isAnonymous, bidDao);

        System.out.printf(">>> [BID] %s $%.2f phiên %s (Anon: %b) → %s | %s%n",
                bidder.getUsername(), amount, auctionId, isAnonymous, result.getStatus(),
                ConcurrentBidManager.getInstance().metricsSummary());

        // Phản hồi riêng cho Client vừa đặt giá
        send(result);

        // Nếu bid thành công, thông báo giá mới cho toàn bộ Client và cập nhật số dư các bên liên quan
        if (result.isSuccess()) {
            UserDAO userDao = new UserDAO();
            Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
            String itemName = (updated != null && updated.getItem() != null) 
                                ? updated.getItem().getItemName() : auctionId;
            
            // 1. Cập nhật số dư cho người vừa BID thành công (Current Bidder)
            double availCurrent = userDao.getBalance(bidderId);
            double lockedCurrent = userDao.getLockedBalance(bidderId);
            send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", availCurrent, lockedCurrent));

            // 2. Cập nhật số dư cho người vừa bị vượt giá nếu có và khác người hiện tại.
            if (prevBidderId != null && !prevBidderId.equals(bidderId)) {
                double availPrev = userDao.getBalance(prevBidderId);
                double lockedPrev = userDao.getLockedBalance(prevBidderId);
                String balanceMsg = String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", availPrev, lockedPrev);
                server.sendToUser(prevBidderId, balanceMsg);

                // Gửi notification cho người vừa bị vượt giá.
                NotificationService.notifyUser(server, prevBidderId,
                    Notification.Type.AUCTION_OUTBID,
                    "Bạn đã bị vượt giá!",
                    String.format("Có người đã đặt giá %,.0f ₫ cho \"%s\". Hãy đặt giá cao hơn để dẫn đầu!",
                        amount, itemName));
            }

            // 3. Thông báo cho SELLER có bid mới
            if (updated != null && updated.getSellerId() != null && !updated.getSellerId().equals(bidderId)) {
                String displayBidder = isAnonymous ? "Một người dùng ẩn danh" : bidder.getUsername();
                NotificationService.notifyUser(server, updated.getSellerId(),
                    Notification.Type.AUCTION_NEW_BID,
                    "Có lượt đặt giá mới",
                    String.format("%s đã đặt giá %,.0f ₫ cho \"%s\" của bạn.",
                        displayBidder, amount, itemName));
            }

            // 4. Broadcast tối ưu: chỉ gửi phiên vừa thay đổi.
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
                // Che giấu thông tin nếu cần thiết trước khi gửi
                Object safeData = sanitizeData(data);
                out.writeObject(safeData);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            active = false;
        }
    }

    /**
     * Nếu client nhận không phải là Admin, thay thế thông tin thật của người đặt ẩn danh
     * bằng thông tin ảo. Trả về một bản copy để không làm hỏng dữ liệu gốc trên Server.
     */
    private Object sanitizeData(Object data) {
        // Kiểm tra xem người nhận hiện tại có phải Admin không
        boolean isAdmin = false;
        if (currentUserId != null) {
            User receiver = new UserDAO().findById(currentUserId);
            isAdmin = (receiver instanceof Admin);
        }

        if (isAdmin) {
            return data; // Admin xem full không che
        }

        // 1. Sanitize Auction (khi broadcast hoặc gửi refresh)
        if (data instanceof Auction auction) {
            // Kiểm tra xem trong history có bid ẩn danh nào không
            boolean hasAnon = false;
            for (model.auction.BidTransaction tx : auction.getBidHistory()) {
                if (tx.isAnonymous()) {
                    hasAnon = true;
                    break;
                }
            }

            if (hasAnon) {
                // Phải tạo một bản sao của Auction để gửi đi, không sửa trực tiếp obj gốc
                Auction safeAuction = new Auction(auction.getAuctionId(), auction.getItem(), 
                                                 auction.getStartTime(), auction.getEndTime());
                safeAuction.setSellerId(auction.getSellerId());
                safeAuction.setStatus(auction.getStatus());
                
                // Copy history và che tên
                java.util.List<model.auction.BidTransaction> safeHistory = new java.util.ArrayList<>();
                for (model.auction.BidTransaction tx : auction.getBidHistory()) {
                    if (tx.isAnonymous()) {
                        // Tạo User ảo
                        User fakeUser = new model.user.Bidder(
                                "anon_id", 
                                tx.getAnonymousDisplayName(), 
                                "anon@hidden.com", 
                                ""
                        );
                        // Dùng icon ẩn danh (phương án 4)
                        fakeUser.setAvatarPath("https://cdn-icons-png.flaticon.com/512/3208/3208903.png");
                        
                        model.auction.BidTransaction safeTx = new model.auction.BidTransaction(
                                fakeUser, tx.getBidAmount(), tx.getTimestamp(), tx.getBidType(), true
                        );
                        safeTx.setAnonymousDisplayName(tx.getAnonymousDisplayName());
                        safeHistory.add(safeTx);
                    } else {
                        safeHistory.add(tx);
                    }
                }
                safeAuction.restoreBidHistory(safeHistory);
                return safeAuction;
            }
        } else if (data instanceof java.util.List<?> list) {
            // 2. Sanitize danh sách Auctions (khi client login và nhận danh sách)
            if (!list.isEmpty() && list.get(0) instanceof Auction) {
                java.util.List<Auction> safeList = new java.util.ArrayList<>();
                for (Object item : list) {
                    safeList.add((Auction) sanitizeData(item));
                }
                return safeList;
            }
        }

        return data;
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

    private void handleNewUserRegistered(String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        User u = userDao.findById(userId);
        String username = (u != null) ? u.getUsername() : userId;

        System.out.println(">>> [NEW_USER] User " + username + " registered.");
        
        // 1. Tạo notification cho tất cả admin trong DB và đẩy tín hiệu theo thời gian thực.
        NotificationService.notifyAdmins(server, 
            Notification.Type.ADMIN_NEW_USER, 
            "Người dùng mới", 
            "Người dùng " + username + " vừa đăng ký tài khoản mới.");

        // 2. Broadcast để bảng quản lý user của admin refresh theo thời gian thực.
        server.broadcastToRole("ADMIN", "USERS_UPDATED");
    }

    private void handleRequestSeller(String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        if (userDao.updatePendingSeller(userId, true)) {
            System.out.println(">>> [SELLER_REQ] User " + userId + " requested to become Seller.");
            // 1. Thông báo qua socket để bảng admin refresh theo thời gian thực.
            server.broadcastToRole("ADMIN", "NEW_SELLER_REQUEST:" + userId);
            
            // 2. Lưu notification vào DB cho tất cả Admin
            User u = userDao.findById(userId);
            String username = (u != null) ? u.getUsername() : userId;
            NotificationService.notifyAdmins(server, 
                Notification.Type.ADMIN_NEW_SELLER_REQUEST, 
                "Yêu cầu nâng cấp Seller", 
                "Người dùng " + username + " đang chờ bạn phê duyệt quyền Seller.");
        }
    }

    private void handleApproveSeller(String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        if (userDao.approveSeller(userId)) {
            System.out.println(">>> [SELLER_APP] User " + userId + " approved as Seller.");
            
            // 1. Thông báo socket cũ (để UI ẩn nút ngay lập tức nếu đang mở)
            // Lệnh này kích hoạt DashboardController.reloadUserFromDB giúp cập nhật Class (Seller/Bidder)
            server.sendToUser(userId, "SELLER_APPROVED");

            // 2. Tạo Notification chính thức trong DB và push refresh signal (cho bell icon)
            NotificationService.notifyUser(server, userId, 
                Notification.Type.SELLER_APPROVED, 
                "Chúc mừng! Bạn đã là Seller", 
                "Tài khoản của bạn đã được Admin phê duyệt quyền Seller. "
                + "Bây giờ bạn có thể đăng bán sản phẩm của riêng mình.");

            // Thông báo cho tất cả Admin để refresh bảng người dùng
            server.broadcastToRole("ADMIN", "USERS_UPDATED");

            // Broadcast lại danh sách phiên để UI phụ thuộc role cập nhật nếu cần.
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        }
    }

    private void handleRevokeSeller(String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        if (userDao.revokeSeller(userId)) {
            System.err.println(">>> [SELLER_REVOKE] User " + userId + " revoked from Seller.");
            
            // 1. Thông báo socket (để UI reload user object ngay lập tức)
            server.sendToUser(userId, "SELLER_REVOKED");

            // 2. Tạo Notification chính thức trong DB và push refresh signal
            NotificationService.notifyUser(server, userId, 
                Notification.Type.SELLER_REVOKED, 
                "Thông báo thay đổi quyền hạn", 
                "Admin đã hủy quyền Seller của bạn. Tài khoản đã được chuyển về vai trò BIDDER.");

            // Thông báo cho tất cả Admin để refresh bảng người dùng
            server.broadcastToRole("ADMIN", "USERS_UPDATED");

            // Broadcast lại danh sách phiên để UI phụ thuộc role cập nhật nếu cần.
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        }
    }

    private void handleFetchNotifications(String msg) {
        String userId = msg.split(":")[1];
        database.NotificationDAO notifDao = new database.NotificationDAO();
        java.util.List<Notification> list = notifDao.findRecent(userId, 20);
        send(new Notification.Bundle(list));
    }

    private void handleMarkNotificationRead(String msg) {
        String notifId = msg.split(":")[1];
        new database.NotificationDAO().markAsRead(notifId);
    }

    private void handleMarkAllNotificationsRead(String msg) {
        String userId = msg.split(":")[1];
        new database.NotificationDAO().markAllAsRead(userId);
    }

    /**
     * Lưu yêu cầu nạp tiền qua chuyển khoản ngân hàng.
     * Định dạng: "DEPOSIT_REQUEST:<userId>:<amount>:<requestId>"
     * Phản hồi: DEPOSIT_REQUEST_OK | DEPOSIT_REQUEST_FAILED:<reason>
     */
    private void handleDepositRequest(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) {
            send("DEPOSIT_REQUEST_FAILED:Lệnh sai định dạng");
            return;
        }
        String userId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            send("DEPOSIT_REQUEST_FAILED:Số tiền không hợp lệ");
            return;
        }
        String requestId = parts[3];

        if (amount <= 0) {
            send("DEPOSIT_REQUEST_FAILED:Số tiền phải lớn hơn 0");
            return;
        }

        UserDAO userDao = new UserDAO();
        User user = userDao.findById(userId);
        if (user == null) {
            send("DEPOSIT_REQUEST_FAILED:Người dùng không tồn tại");
            return;
        }

        boolean saved = new database.DepositRequestDAO().save(requestId, userId, amount);
        if (!saved) {
            send("DEPOSIT_REQUEST_FAILED:Lỗi lưu yêu cầu");
            return;
        }

        send("DEPOSIT_REQUEST_OK");
        System.out.printf(">>> [DEPOSIT] %s yêu cầu nạp $%.2f (mã: %s)%n",
                user.getUsername(), amount, requestId);

        NotificationService.notifyAdmins(server,
                Notification.Type.ADMIN_DEPOSIT_REQUEST,
                "Yêu cầu nạp tiền mới",
                String.format("%s muốn nạp %,.0f ₫ (mã: %s)", user.getUsername(), amount, requestId));
        server.broadcastToRole("ADMIN", "NEW_DEPOSIT_REQUEST:" + userId);
    }

    /**
     * Admin duyệt hoặc từ chối yêu cầu nạp tiền.
     * Định dạng: "DEPOSIT_REVIEW:<requestId>:<APPROVED|REJECTED>:<adminNote>"
     * Phản hồi: DEPOSIT_REVIEW_OK | DEPOSIT_REVIEW_FAILED:<reason>
     */
    private void handleDepositReview(String msg) {
        String[] parts = msg.split(":", 4);
        if (parts.length < 3) {
            send("DEPOSIT_REVIEW_FAILED:Lệnh sai định dạng");
            return;
        }
        String requestId = parts[1];
        String statusStr  = parts[2];
        String adminNote  = parts.length >= 4 ? parts[3] : "";

        model.auction.DepositRequest.Status status;
        try {
            status = model.auction.DepositRequest.Status.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            send("DEPOSIT_REVIEW_FAILED:Trạng thái không hợp lệ");
            return;
        }
        if (status == model.auction.DepositRequest.Status.PENDING) {
            send("DEPOSIT_REVIEW_FAILED:Trạng thái không hợp lệ");
            return;
        }

        database.DepositRequestDAO dao = new database.DepositRequestDAO();
        model.auction.DepositRequest dr = dao.findById(requestId);
        if (dr == null) {
            send("DEPOSIT_REVIEW_FAILED:Không tìm thấy yêu cầu");
            return;
        }

        boolean ok = dao.review(requestId, status, adminNote);
        if (!ok) {
            send("DEPOSIT_REVIEW_FAILED:Lỗi cập nhật DB");
            return;
        }

        if (status == model.auction.DepositRequest.Status.APPROVED) {
            UserDAO userDao = new UserDAO();
            double current    = userDao.getBalance(dr.getUserId());
            double newBalance = current + dr.getAmount();
            userDao.setBalance(dr.getUserId(), newBalance);

            new database.WalletTransactionDAO().save(dr.getUserId(), dr.getAmount(),
                    model.auction.WalletTransaction.TransactionType.TOPUP,
                    "Nạp tiền qua chuyển khoản - Mã: " + requestId);

            NotificationService.notifyUser(server, dr.getUserId(),
                    Notification.Type.WALLET_TOPUP,
                    "Nạp tiền thành công",
                    String.format("Yêu cầu nạp %,.0f ₫ (mã: %s) đã được duyệt.", dr.getAmount(), requestId));

            double locked = userDao.getLockedBalance(dr.getUserId());
            server.sendToUser(dr.getUserId(), String.format(java.util.Locale.US,
                    "BALANCE_UPDATE:%.2f:%.2f", newBalance, locked));

            System.out.printf(">>> [DEPOSIT] Duyệt nạp $%.2f cho user %s%n",
                    dr.getAmount(), dr.getUserId());
        } else {
            NotificationService.notifyUser(server, dr.getUserId(),
                    Notification.Type.DEPOSIT_REJECTED,
                    "Yêu cầu nạp tiền bị từ chối",
                    "Yêu cầu nạp tiền (mã: " + requestId + ") bị từ chối."
                            + (adminNote.isEmpty() ? "" : " Lý do: " + adminNote));

            System.out.printf(">>> [DEPOSIT] Từ chối nạp tiền cho user %s%n", dr.getUserId());
        }

        send("DEPOSIT_REVIEW_OK");
        server.broadcastToRole("ADMIN", "USERS_UPDATED");
    }

    /**
     * Định dạng: "CHAT_SEND:<senderId>:<receiverId>:<content...>"
     * Nội dung có thể chứa dấu ':' và ký tự Unicode.
     */
    private void handleChatSend(String msg) {
        String[] parts = msg.split(":", 4);
        if (parts.length < 4) {
            send("CHAT_SEND_FAILED:Sai định dạng");
            return;
        }
        String senderId = parts[1];
        String receiverId = parts[2];
        String content = parts[3];
        if (content == null || content.trim().isEmpty()) {
            send("CHAT_SEND_FAILED:Nội dung rỗng");
            return;
        }
        if (content.length() > 2000) content = content.substring(0, 2000);

        database.ChatMessageDAO dao = new database.ChatMessageDAO();
        model.chat.ChatMessage saved = dao.insert(senderId, receiverId, content);
        if (saved == null) {
            send("CHAT_SEND_FAILED:Lỗi DB");
            return;
        }

        // Gửi lại cho người gửi để xác nhận, đồng thời đẩy tin mới cho người nhận theo thời gian thực.
        send(saved);
        server.sendToUser(receiverId, saved);

        // Notification cho receiver — gộp thay vì tạo mới từng tin
        UserDAO userDao = new UserDAO();
        User sender = userDao.findById(senderId);
        String senderName = sender != null ? sender.getUsername() : senderId;
        String preview = content.length() > 80 ? content.substring(0, 77) + "..." : content;
        NotificationService.notifyChat(server, receiverId, senderId, senderName, preview);
    }

    private void handleChatFetch(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String userId = parts[1];
        String partnerId = parts[2];
        java.util.List<model.chat.ChatMessage> list =
                new database.ChatMessageDAO().findConversation(userId, partnerId, 200);
        send(new model.chat.ChatMessage.Bundle(partnerId, list));
    }

    private void handleChatFetchList(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 2) return;
        String userId = parts[1];
        database.ChatMessageDAO dao = new database.ChatMessageDAO();
        java.util.List<model.chat.ChatMessage.Summary> summaries = dao.findSummaries(userId);
        int total = dao.getTotalUnread(userId);
        send(new model.chat.ChatMessage.SummaryBundle(summaries, total));
    }

    private void handleChatMarkRead(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String userId = parts[1];
        String partnerId = parts[2];
        java.util.List<String> marked = new database.ChatMessageDAO()
                .markConversationRead(userId, partnerId);
        if (marked.isEmpty()) return;

        // Đồng bộ: Đánh dấu các thông báo chat tương ứng là đã đọc
        new database.NotificationDAO().markChatAsRead(userId, partnerId);

        // Báo cho sender (partner) biết tin nhắn của họ đã được đọc
        server.sendToUser(partnerId, "CHAT_READ:" + userId + ":" + String.join(",", marked));
        // Báo cho receiver biết unread đã giảm (refresh badge)
        send("CHAT_UNREAD_UPDATED:" + userId);
    }

    private void handleChatLike(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String messageId = parts[1];
        boolean liked = "1".equals(parts[2]);
        database.ChatMessageDAO dao = new database.ChatMessageDAO();
        if (!dao.setLiked(messageId, liked)) return;
        model.chat.ChatMessage updated = dao.findById(messageId);
        if (updated == null) return;

        // Push cho cả 2 phía
        server.sendToUser(updated.getSenderId(), updated);
        server.sendToUser(updated.getReceiverId(), updated);

        // Notification cho sender khi tin của họ được liked (chỉ khi liked=true)
        if (liked && this.currentUserId != null
                && !this.currentUserId.equals(updated.getSenderId())) {
            UserDAO userDao = new UserDAO();
            User liker = userDao.findById(this.currentUserId);
            String likerName = liker != null ? liker.getUsername() : this.currentUserId;
            String preview = updated.getContent();
            if (preview != null && preview.length() > 60) preview = preview.substring(0, 57) + "...";
            NotificationService.notifyUser(server, updated.getSenderId(),
                    Notification.Type.CHAT_LIKED,
                    likerName + " đã thích tin nhắn của bạn",
                    preview);
        }
    }

    /**
     * CHAT_RECALL:{@code <messageId>}:ALL  — Thu hồi với tất cả (chỉ sender).
     * CHAT_RECALL:{@code <messageId>}:SELF — Ẩn với chính mình (sender hoặc receiver).
     */
    private void handleChatRecall(String msg) {
        String[] parts = msg.split(":", 3);
        if (parts.length < 3) {
            send("CHAT_RECALL_FAILED:Sai định dạng");
            return;
        }
        String messageId = parts[1];
        String mode = parts[2];
        String requesterId = this.currentUserId;
        if (requesterId == null) {
            send("CHAT_RECALL_FAILED:Chưa xác thực");
            return;
        }

        database.ChatMessageDAO dao = new database.ChatMessageDAO();
        if ("ALL".equals(mode)) {
            model.chat.ChatMessage updated = dao.recallForAll(messageId, requesterId);
            if (updated == null) {
                send("CHAT_RECALL_FAILED:Không thể thu hồi");
                return;
            }
            // Đẩy ChatMessage đã cập nhật (recalled=true) cho cả 2 phía
            server.sendToUser(updated.getSenderId(), updated);
            server.sendToUser(updated.getReceiverId(), updated);
        } else if ("SELF".equals(mode)) {
            if (!dao.hideSelf(messageId, requesterId)) {
                send("CHAT_RECALL_FAILED:Không thể ẩn");
                return;
            }
            // Chỉ thông báo cho người yêu cầu
            send("CHAT_RECALLED_SELF:" + messageId);
        } else {
            send("CHAT_RECALL_FAILED:Mode không hợp lệ");
        }
    }

    // =========================================================================
    // Các handler xử lý luồng bạn bè.
    // =========================================================================

    /** FRIEND_REQUEST:<fromId>:<toId> */
    private void handleFriendRequest(String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts("FRIEND_REQUEST", p, 3)) {
            return;
        }
        String fromId = p[1], toId = p[2];
        if (fromId.equals(toId)) {
            send("FRIEND_REQUEST_FAILED:Không thể kết bạn với chính mình");
            return;
        }
        database.FriendshipDAO dao = new database.FriendshipDAO();
        if (!dao.sendRequest(fromId, toId)) {
            send("FRIEND_REQUEST_FAILED:Đã tồn tại quan hệ");
            return;
        }
        send("FRIEND_REQUEST_OK:" + toId);
        // Refresh bundle cho cả 2 phía
        pushFriendBundle(fromId);
        pushFriendBundle(toId);
        // Gửi notification cho người được mời kết bạn.
        UserDAO userDao = new UserDAO();
        User from = userDao.findById(fromId);
        String fromName = from != null ? from.getUsername() : fromId;
        utils.NotificationService.notifyUser(server, toId,
                Notification.Type.FRIEND_REQUEST,
                "Lời mời kết bạn",
                fromName + " muốn kết bạn với bạn");
    }

    /** FRIEND_ACCEPT:<requesterId>:<addresseeId(=me)> */
    private void handleFriendAccept(String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts("FRIEND_ACCEPT", p, 3)) {
            return;
        }
        String requesterId = p[1], addresseeId = p[2];
        database.FriendshipDAO dao = new database.FriendshipDAO();
        if (!dao.accept(requesterId, addresseeId)) {
            send("FRIEND_ACCEPT_FAILED");
            return;
        }
        send("FRIEND_ACCEPT_OK:" + requesterId);
        pushFriendBundle(requesterId);
        pushFriendBundle(addresseeId);
        // Gửi notification cho người đã gửi lời mời.
        UserDAO userDao = new UserDAO();
        User addressee = userDao.findById(addresseeId);
        String addresseeName = addressee != null ? addressee.getUsername() : addresseeId;
        utils.NotificationService.notifyUser(server, requesterId,
                Notification.Type.FRIEND_ACCEPTED,
                "Lời mời kết bạn",
                addresseeName + " đã chấp nhận lời mời kết bạn của bạn");
    }

    /** FRIEND_DECLINE:<requesterId>:<addresseeId> */
    private void handleFriendDecline(String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts("FRIEND_DECLINE", p, 3)) {
            return;
        }
        if (!new database.FriendshipDAO().decline(p[1], p[2])) {
            send("FRIEND_DECLINE_FAILED");
            return;
        }
        pushFriendBundle(p[1]);
        pushFriendBundle(p[2]);
    }

    /** FRIEND_LIST:<userId> */
    private void handleFriendList(String msg) {
        String[] p = msg.split(":", 2);
        if (!requireFriendCommandParts("FRIEND_LIST", p, 2)) {
            return;
        }
        send(new database.FriendshipDAO().getFriendBundle(p[1]));
    }

    /** FRIEND_STATUS:<myId>:<otherId> */
    private void handleFriendStatus(String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts("FRIEND_STATUS", p, 3)) {
            return;
        }
        String status = new database.FriendshipDAO().getStatus(p[1], p[2]);
        send("FRIEND_STATUS_RESULT:" + p[2] + ":" + status);
    }

    /** USER_SEARCH:<myId>:<query...> */
    private void handleUserSearch(String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts("USER_SEARCH", p, 3)) {
            return;
        }
        send(new database.FriendshipDAO().search(p[1], p[2]));
    }

    private boolean requireFriendCommandParts(String command, String[] parts, int minLength) {
        if (parts.length >= minLength) {
            return true;
        }
        send(ErrorResponse.of(ErrorCode.COMMAND_FORMAT_INVALID,
                "Lệnh " + command + " sai định dạng."));
        return false;
    }

    /** Push FriendBundle tới user nếu đang online. */
    private void pushFriendBundle(String userId) {
        model.friendship.Friendship.Bundle bundle =
                new database.FriendshipDAO().getFriendBundle(userId);
        server.sendToUser(userId, bundle);
    }
}
