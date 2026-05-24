package network.service;

import database.UserDAO;
import model.auction.Auction;
import model.manager.AuctionManager;
import model.notification.Notification;
import model.user.User;
import network.AuctionServer;
import network.ClientHandler;
import utils.NotificationService;

import java.util.Locale;

/**
 * Dịch vụ hỗ trợ phát sóng và thông báo các sự kiện đấu giá tới người dùng.
 */
public class AuctionBroadcastService {

    /**
     * Sau khi một lượt đặt giá thành công, thông báo cho tất cả các bên liên quan.
     */
    public static void broadcastBidSuccess(AuctionServer server, ClientHandler context, 
                                           Auction updatedAuction, String bidderId, 
                                           String prevBidderId, double amount, boolean isAnonymous) {
        if (updatedAuction == null || server == null) return;
        
        UserDAO userDao = new UserDAO();
        String auctionId = updatedAuction.getAuctionId();
        String itemName = (updatedAuction.getItem() != null) ? updatedAuction.getItem().getItemName() : auctionId;

        // 1. Cập nhật số dư cho người vừa đặt giá (Client hiện tại)
        if (context != null) {
            pushBalanceUpdate(server, bidderId);
        }

        // 2. Thông báo cho người bị vượt giá
        if (prevBidderId != null && !prevBidderId.equals(bidderId)) {
            pushBalanceUpdate(server, prevBidderId);
            
            NotificationService.notifyUser(server, prevBidderId,
                Notification.Type.AUCTION_OUTBID,
                "Bạn đã bị vượt giá!",
                String.format("Có người đã đặt giá %,.0f ₫ cho \"%s\". Hãy đặt giá cao hơn để dẫn đầu!",
                    amount, itemName));
        }

        // 3. Thông báo cho người bán
        if (updatedAuction.getSellerId() != null && !updatedAuction.getSellerId().equals(bidderId)) {
            User bidder = userDao.findById(bidderId);
            String displayBidder = (isAnonymous || bidder == null) ? "Một người dùng ẩn danh" : bidder.getUsername();
            
            NotificationService.notifyUser(server, updatedAuction.getSellerId(),
                Notification.Type.AUCTION_NEW_BID,
                "Có lượt đặt giá mới",
                String.format("%s đã đặt giá %,.0f ₫ cho \"%s\" của bạn.",
                    displayBidder, amount, itemName));
        }

        // 4. Phát sóng phiên bản mới tới tất cả mọi người
        server.broadcast(updatedAuction);
    }

    /**
     * Thông báo khi thanh toán thành công.
     */
    public static void broadcastAuctionPaySuccess(AuctionServer server, Auction auction, 
                                                  String winnerId, String sellerId, double amount) {
        if (server == null || auction == null) return;

        // 1. Cập nhật số dư cho cả 2 người
        pushBalanceUpdate(server, winnerId);
        pushBalanceUpdate(server, sellerId);

        // 2. Phát sóng trạng thái mới (PAID)
        server.broadcast(auction);
        
        // 3. Thông báo cho tất cả về danh sách mới (đồng bộ fallback)
        server.broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    /**
     * Thông báo khi phiên đấu giá kết thúc.
     */
    public static void broadcastAuctionFinish(AuctionServer server, Auction auction) {
        if (server == null || auction == null) return;
        
        String itemName = (auction.getItem() != null) ? auction.getItem().getItemName() : auction.getAuctionId();
        String sellerId = auction.getSellerId();
        
        if (auction.getHighestBid() != null && auction.getHighestBid().getBidder() != null) {
            String winnerId = auction.getHighestBid().getBidder().getUserId();
            double price = auction.getHighestBid().getBidAmount();

            // 1. Thông báo cho người thắng
            NotificationService.notifyUser(server, winnerId,
                Notification.Type.AUCTION_WON,
                "Bạn đã thắng phiên đấu giá!",
                String.format(Locale.US, "Bạn đã thắng \"%s\" với giá %,.0f ₫. Vào ví để thanh toán.",
                    itemName, price)
            );

            // 2. Thông báo cho người bán
            if (sellerId != null) {
                String displayWinner = (auction.getHighestBid().isAnonymous()) 
                        ? auction.getHighestBid().getAnonymousDisplayName() 
                        : auction.getHighestBid().getBidder().getUsername();
                
                NotificationService.notifyUser(server, sellerId,
                    Notification.Type.AUCTION_ENDED_SOLD,
                    "Phiên của bạn đã kết thúc — có người thắng",
                    String.format(Locale.US, "Phiên \"%s\" kết thúc. Người thắng: %s, giá %,.0f ₫. Chờ thanh toán.",
                        itemName, displayWinner, price)
                );
            }
            
            // 3. Cập nhật số dư cho người thắng (vì có thể họ được hoàn một phần tiền Auto-Bid)
            pushBalanceUpdate(server, winnerId);
            
        } else {
            // Trường hợp không có ai bid
            if (sellerId != null) {
                NotificationService.notifyUser(server, sellerId,
                    Notification.Type.AUCTION_ENDED_NO_BID,
                    "Phiên của bạn kết thúc — không có bid",
                    String.format("Phiên \"%s\" kết thúc nhưng không có ai đặt giá.", itemName)
                );
            }
        }
    }

    /**
     * Gửi tín hiệu BALANCE_UPDATE tới một user cụ thể.
     */
    public static void pushBalanceUpdate(AuctionServer server, String userId) {
        if (server == null || userId == null) return;
        UserDAO userDao = new UserDAO();
        double avail = userDao.getBalance(userId);
        double locked = userDao.getLockedBalance(userId);
        String balanceMsg = String.format(Locale.US, "BALANCE_UPDATE:%.2f:%.2f", avail, locked);
        server.sendToUser(userId, balanceMsg);
    }

    /**
     * Thông báo khi một phiên bị xóa hoặc hủy.
     */
    public static void broadcastAuctionRemoval(AuctionServer server, Auction auction, String reasonPrefix) {
        if (server == null || auction == null) return;
        String auctionId = auction.getAuctionId();
        
        // Thông báo cho tất cả client xóa khỏi bộ nhớ local
        server.broadcast("AUCTION_REMOVED:" + auctionId);
        
        // Gửi danh sách mới để đồng bộ hóa hoàn toàn
        server.broadcast(AuctionManager.getInstance().getAllAuctions());
    }
}
