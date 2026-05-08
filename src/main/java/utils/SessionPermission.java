package utils;

import java.util.Set;
import database.AuctionDAO;
import model.auction.Auction;
import model.manager.AppState;
import model.user.Admin;
import model.user.Seller;
import model.user.User;

/**
 * Tập trung logic kiểm tra quyền Sửa / Xóa phiên đấu giá.
 */
public class SessionPermission {

    // Cache nhỏ, tránh query DB lặp lại trong cùng 1 phiên người dùng
    private static String cachedSellerId = null;
    private static Set<String> cachedOwnedIds = null;

    private SessionPermission() {} // Utility class — không cho khởi tạo

    /**
     * Người dùng hiện tại có được sửa/xóa phiên này không?
     */
    public static boolean canEdit(Auction auction) {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || auction == null) return false;

        // Chỉ sửa khi phiên đang OPEN
        if (!"OPEN".equals(auction.getStatus())) return false;

        if (user instanceof Admin) return true;
        if (user instanceof Seller) {
            return isOwnedBy(auction.getAuctionId(), user.getUserId());
        }
        return false;
    }

    /**
     * Có được XÓA phiên này không?
     */
    public static boolean canDelete(Auction auction) {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || auction == null) return false;

        if (user instanceof Admin) return true;

        if (user instanceof Seller) {
            // Seller không xóa được phiên đã FINISHED
            if ("FINISHED".equals(auction.getStatus())) return false;
            return isOwnedBy(auction.getAuctionId(), user.getUserId());
        }
        return false;
    }

    /**
     * Backward-compat: trả về true nếu được sửa HOẶC xóa.
     */
    public static boolean canEditOrDelete(Auction auction) {
        return canEdit(auction) || canDelete(auction);
    }

    /**
     * Kiểm tra phiên đấu giá có thuộc sở hữu của seller không.
     */
    private static boolean isOwnedBy(String auctionId, String sellerId) {
        if (!sellerId.equals(cachedSellerId)) {
            cachedSellerId = sellerId;
            cachedOwnedIds = new AuctionDAO().getAuctionIdsBySeller(sellerId);
        }
        return cachedOwnedIds != null && cachedOwnedIds.contains(auctionId);
    }

    /**
     * Xóa cache — gọi khi user đăng nhập/đăng xuất hoặc khi tạo phiên mới.
     */
    public static void invalidateCache() {
        cachedSellerId = null;
        cachedOwnedIds = null;
    }
}