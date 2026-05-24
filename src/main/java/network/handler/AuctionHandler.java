package network.handler;

import database.AuctionDAO;
import database.BidTransactionDAO;
import database.UserDAO;
import exception.ErrorCode;
import model.auction.Auction;
import model.auction.BidResult;
import model.manager.AuctionManager;
import model.manager.ConcurrentBidManager;
import model.notification.Notification;
import model.user.Admin;
import model.user.User;
import network.ClientHandler;
import utils.NotificationService;

/**
 * Handles auction-related commands.
 */
public class AuctionHandler implements RequestHandler {

    @Override
    public void handle(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (msg.startsWith("BID:")) {
            if (parts.length >= 4) context.setUserId(parts[3]);
            handleConcurrentBid(context, msg);
        } else if (msg.startsWith("DELETE_AUCTION:")) {
            if (parts.length >= 3) context.setUserId(parts[2]);
            handleDeleteAuction(context, msg);
        } else if (msg.startsWith("CANCEL_AUCTION:")) {
            if (parts.length >= 3) context.setUserId(parts[2]);
            handleCancelAuction(context, msg);
        } else if (msg.startsWith("PAY_AUCTION:")) {
            if (parts.length >= 3) context.setUserId(parts[2]);
            handlePayAuction(context, msg);
        } else if (msg.startsWith("SET_AUTOBID:")) {
            if (parts.length >= 3) context.setUserId(parts[2]);
            handleSetAutoBid(context, msg);
        } else if (msg.startsWith("CANCEL_AUTOBID:")) {
            if (parts.length >= 3) context.setUserId(parts[2]);
            handleCancelAutoBid(context, msg);
        } else if (msg.startsWith("GET_MY_AUTOBID:")) {
            handleGetMyAutoBid(context, msg);
        } else if (msg.startsWith("RELOAD_AUCTION:")) {
            handleReloadAuction(context, msg);
        }
    }

    private void handleConcurrentBid(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) {
            context.send(BidResult.failure("?", 0, ErrorCode.COMMAND_FORMAT_INVALID,
                    "Lệnh BID sai định dạng."));
            return;
        }

        String auctionId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            context.send(BidResult.failure(auctionId, 0, ErrorCode.VALIDATION_ERROR,
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
            context.send(BidResult.failure(auctionId, amount, ErrorCode.USER_NOT_FOUND,
                    "Người dùng không tồn tại."));
            return;
        }

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        String prevBidderId = (auction != null && auction.getHighestBid() != null) 
                                ? auction.getHighestBid().getBidder().getUserId() : null;

        BidTransactionDAO bidDao = new BidTransactionDAO();
        BidResult result = ConcurrentBidManager.getInstance()
                .processBid(auctionId, amount, bidder, isAnonymous, bidDao);

        context.send(result);

        if (result.isSuccess()) {
            UserDAO userDao = new UserDAO();
            Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
            String itemName = (updated != null && updated.getItem() != null) 
                                ? updated.getItem().getItemName() : auctionId;
            
            double availCurrent = userDao.getBalance(bidderId);
            double lockedCurrent = userDao.getLockedBalance(bidderId);
            context.send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", availCurrent, lockedCurrent));

            if (prevBidderId != null && !prevBidderId.equals(bidderId)) {
                double availPrev = userDao.getBalance(prevBidderId);
                double lockedPrev = userDao.getLockedBalance(prevBidderId);
                String balanceMsg = String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", availPrev, lockedPrev);
                context.getServer().sendToUser(prevBidderId, balanceMsg);

                NotificationService.notifyUser(context.getServer(), prevBidderId,
                    Notification.Type.AUCTION_OUTBID,
                    "Bạn đã bị vượt giá!",
                    String.format("Có người đã đặt giá %,.0f ₫ cho \"%s\". Hãy đặt giá cao hơn để dẫn đầu!",
                        amount, itemName));
            }

            if (updated != null && updated.getSellerId() != null && !updated.getSellerId().equals(bidderId)) {
                String displayBidder = isAnonymous ? "Một người dùng ẩn danh" : bidder.getUsername();
                NotificationService.notifyUser(context.getServer(), updated.getSellerId(),
                    Notification.Type.AUCTION_NEW_BID,
                    "Có lượt đặt giá mới",
                    String.format("%s đã đặt giá %,.0f ₫ cho \"%s\" của bạn.",
                        displayBidder, amount, itemName));
            }

            if (updated != null) context.getServer().broadcast(updated);
        }
    }

    private void handleDeleteAuction(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            context.send("DELETE_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        User requester = new UserDAO().findById(requesterId);
        if (requester == null) {
            context.send("DELETE_FAILED: Không tìm thấy người yêu cầu");
            return;
        }

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null) {
            context.send("DELETE_FAILED: Phiên không tồn tại");
            return;
        }

        boolean ok;
        if (requester instanceof Admin) {
            ok = new AuctionDAO().delete(auctionId);
        } else {
            if ("FINISHED".equals(auction.getStatus())) {
                context.send("DELETE_FAILED: Phiên đã kết thúc, không thể xóa!");
                return;
            }
            int result = new AuctionDAO().deleteIfOwner(auctionId, requesterId);
            if (result == 0) {
                context.send("DELETE_FAILED: Bạn không có quyền xóa phiên này");
                return;
            }
            ok = (result == 1);
        }

        if (ok) {
            unlockAllBidders(auction);

            NotificationService.notifyUser(context.getServer(), auction.getSellerId(),
                Notification.Type.AUCTION_CANCELED,
                "Phiên đấu giá bị xóa",
                String.format("Phiên \"%s\" của bạn đã bị xóa khỏi hệ thống.", 
                    (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId));

            if (auction.getHighestBid() != null) {
                String winnerId = auction.getHighestBid().getBidder().getUserId();
                NotificationService.notifyUser(context.getServer(), winnerId,
                    Notification.Type.WALLET_REFUND,
                    "Hoàn tiền cam kết",
                    String.format("Phiên \"%s\" bị xóa. Số tiền cam kết %,.0f ₫ đã được hoàn trả vào ví của bạn.",
                        (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId,
                        auction.getHighestBid().getBidAmount()));
            }

            AuctionManager.getInstance().removeAuction(auctionId);
            context.send("DELETE_OK:" + auctionId);
            // Thông báo cho tất cả client xóa phiên này khỏi bộ nhớ local ngay lập tức
            context.getServer().broadcast("AUCTION_REMOVED:" + auctionId);
            // Đồng thời gửi list mới để đảm bảo tính nhất quán (fallback)
            context.getServer().broadcast(AuctionManager.getInstance().getAllAuctions());
        } else {
            context.send("DELETE_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    private void handleCancelAuction(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            context.send("CANCEL_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        User requester = new UserDAO().findById(requesterId);
        if (requester == null) {
            context.send("CANCEL_FAILED: Không tìm thấy người yêu cầu");
            return;
        }

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null) {
            context.send("CANCEL_FAILED: Phiên không tồn tại");
            return;
        }

        unlockAllBidders(auction);

        boolean isAdmin = (requester instanceof Admin);
        int result = new AuctionDAO().cancelAuction(auctionId, requesterId, isAdmin);

        switch (result) {
            case 1 -> {
                auction.setStatus("CANCELED");

                NotificationService.notifyUser(context.getServer(), auction.getSellerId(),
                    Notification.Type.AUCTION_CANCELED,
                    "Phiên đấu giá bị hủy",
                    String.format("Phiên \"%s\" của bạn đã bị hủy.", 
                        (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId));

                if (auction.getHighestBid() != null) {
                    String winnerId = auction.getHighestBid().getBidder().getUserId();
                    NotificationService.notifyUser(context.getServer(), winnerId,
                        Notification.Type.WALLET_REFUND,
                        "Hoàn tiền cam kết",
                        String.format("Phiên \"%s\" bị hủy. Số tiền cam kết %,.0f ₫ đã được hoàn trả vào ví của bạn.",
                            (auction.getItem() != null) ? auction.getItem().getItemName() : auctionId,
                            auction.getHighestBid().getBidAmount()));
                }

                model.manager.ConcurrentBidManager.getInstance().releaseLock(auctionId);
                context.send("CANCEL_OK:" + auctionId);
                context.getServer().broadcast(AuctionManager.getInstance().getAllAuctions());
            }
            case 0  -> context.send("CANCEL_FAILED: Bạn không có quyền hủy phiên này");
            case -1 -> context.send("CANCEL_FAILED: Trạng thái phiên không cho phép hủy "
                    + "(chỉ OPEN/RUNNING với seller, hoặc OPEN/RUNNING/FINISHED với admin)");
            default -> context.send("CANCEL_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    private void handlePayAuction(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            context.send("PAY_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        AuctionDAO auctionDao = new AuctionDAO();
        String[] winnerInfo = auctionDao.findWinnerInfo(auctionId);
        if (winnerInfo == null) {
            context.send("PAY_FAILED: Phiên chưa kết thúc hoặc không có người thắng");
            return;
        }

        String winnerId  = winnerInfo[0];
        String sellerId  = winnerInfo[1];
        double finalPrice;
        try {
            finalPrice = Double.parseDouble(winnerInfo[2]);
        } catch (NumberFormatException e) {
            context.send("PAY_FAILED: Dữ liệu phiên bị lỗi");
            return;
        }

        if (!winnerId.equals(requesterId)) {
            context.send("PAY_FAILED: Bạn không phải người thắng phiên này");
            return;
        }

        UserDAO userDao = new UserDAO();
        double currentLocked = userDao.getLockedBalance(winnerId);
        if (currentLocked < finalPrice) {
            double currentBalance = userDao.getBalance(winnerId);
            if (currentBalance >= finalPrice) {
                if (userDao.transferAtomic(winnerId, sellerId, finalPrice)) {
                    finalizePayment(context, auctionId, winnerId, sellerId, finalPrice, userDao);
                    return;
                }
            }
            context.send(String.format("PAY_FAILED: Số dư cam kết không đủ (cần %,.0f ₫, bạn có %,.0f ₫ locked)",
                    finalPrice, currentLocked));
            return;
        }

        boolean transferOk = userDao.transferFromLockedAtomic(winnerId, sellerId, finalPrice);
        if (!transferOk) {
            context.send("PAY_FAILED: Lỗi chuyển tiền từ quỹ cam kết");
            return;
        }

        finalizePayment(context, auctionId, winnerId, sellerId, finalPrice, userDao);
    }

    private void finalizePayment(ClientHandler context, String auctionId, String winnerId,
                                 String sellerId, double finalPrice, UserDAO userDao) {
        AuctionDAO auctionDao = new AuctionDAO();
        boolean paidOk = auctionDao.markAsPaid(auctionId);
        if (!paidOk) {
            context.send("PAY_FAILED: Lỗi cập nhật trạng thái phiên");
            return;
        }

        database.WalletTransactionDAO walletDao = new database.WalletTransactionDAO();
        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        String itemName = (auction != null && auction.getItem() != null) ? auction.getItem().getItemName() : auctionId;
        
        walletDao.save(winnerId, -finalPrice, 
            model.auction.WalletTransaction.TransactionType.PAYMENT, 
            "Thanh toán sản phẩm: " + itemName);
        
        walletDao.save(sellerId, finalPrice, 
            model.auction.WalletTransaction.TransactionType.EARNING, 
            "Tiền bán sản phẩm: " + itemName);

        NotificationService.notifyUser(context.getServer(), winnerId,
            Notification.Type.WALLET_PAYMENT,
            "Thanh toán thành công",
            String.format("Bạn đã thanh toán %,.0f ₫ cho sản phẩm \"%s\".", finalPrice, itemName));
        
        NotificationService.notifyUser(context.getServer(), sellerId,
            Notification.Type.WALLET_EARNING,
            "Bạn nhận được tiền bán hàng",
            String.format("Người mua đã thanh toán %,.0f ₫ cho sản phẩm \"%s\" của bạn.", finalPrice, itemName));

        if (auction != null) auction.setStatus("PAID");

        double newBalance = userDao.getBalance(winnerId);
        context.send("PAY_OK:" + auctionId + ":" + newBalance);
        
        double locked = userDao.getLockedBalance(winnerId);
        context.send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", newBalance, locked));
        
        double sellerAvail = userDao.getBalance(sellerId);
        double sellerLocked = userDao.getLockedBalance(sellerId);
        context.getServer().sendToUser(sellerId, String.format(java.util.Locale.US,
                "BALANCE_UPDATE:%.2f:%.2f", sellerAvail, sellerLocked));

        context.getServer().broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    private void handleReloadAuction(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 2) {
            context.send("RELOAD_FAILED: Sai định dạng");
            return;
        }
        String auctionId = parts[1];
        AuctionManager.getInstance().reloadAuctionFromDB(auctionId);
        context.send("RELOAD_OK:" + auctionId);
        context.getServer().broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    private void handleSetAutoBid(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 5) {
            context.send("AUTOBID_FAILED: Sai định dạng lệnh");
            return;
        }
        String auctionId = parts[1];
        String userId    = parts[2];
        double maxBid, increment;
        try {
            maxBid    = Double.parseDouble(parts[3]);
            increment = Double.parseDouble(parts[4]);
        } catch (NumberFormatException e) {
            context.send("AUTOBID_FAILED: Số tiền không hợp lệ");
            return;
        }
        boolean isAnon = parts.length >= 6 && Boolean.parseBoolean(parts[5]);

        String result = model.manager.AutoBidManager.getInstance()
                .registerAutoBid(userId, auctionId, maxBid, increment, isAnon);
        
        if ("SUCCESS".equals(result)) {
            context.send("AUTOBID_OK:" + auctionId + ":" + (new UserDAO().getBalance(userId)));
            
            UserDAO userDao = new UserDAO();
            double avail = userDao.getBalance(userId);
            double locked = userDao.getLockedBalance(userId);
            context.send(String.format(java.util.Locale.US, "BALANCE_UPDATE:%.2f:%.2f", avail, locked));

            BidTransactionDAO bidDao = new BidTransactionDAO();
            model.manager.AutoBidManager.getInstance().executeAutoBids(auctionId, bidDao);
            
            Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
            if (updated != null) context.getServer().broadcast(updated);
        } else {
            context.send("AUTOBID_FAILED:" + result);
        }
    }

    private void handleCancelAutoBid(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            context.send("CANCEL_AUTOBID_FAILED: Sai định dạng lệnh");
            return;
        }
        String auctionId = parts[1];
        String userId    = parts[2];

        boolean ok = model.manager.AutoBidManager.getInstance().cancelAutoBid(userId, auctionId);
        if (ok) {
            context.send("CANCEL_AUTOBID_OK:" + auctionId + ":" + (new UserDAO().getBalance(userId)));
        } else {
            context.send("CANCEL_AUTOBID_FAILED: Không tìm thấy Auto-Bid để hủy");
        }
    }

    private void handleGetMyAutoBid(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String auctionId = parts[1];
        String userId    = parts[2];

        model.auction.AutoBid ab = new database.AutoBidDAO().findByUserAndAuction(userId, auctionId);
        if (ab != null) {
            context.send(String.format(java.util.Locale.US, "MY_AUTOBID:%s:%.2f:%.2f:%b", 
                auctionId, ab.getMaxBid(), ab.getIncrement(), ab.isAnonymous()));
        } else {
            context.send("MY_AUTOBID_NONE:" + auctionId);
        }
    }

    private void unlockAllBidders(Auction auction) {
        String auctionId = auction.getAuctionId();
        java.util.Set<String> autoBidderIds = model.manager.AutoBidManager.getInstance()
                .cleanupForCancellation(auctionId);

        if (auction.getHighestBid() != null) {
            String bidderId = auction.getHighestBid().getBidder().getUserId();
            double amount   = auction.getHighestBid().getBidAmount();
            
            if (!autoBidderIds.contains(bidderId)) {
                new UserDAO().unlockBalance(bidderId, amount);
            }
        }
    }
}
