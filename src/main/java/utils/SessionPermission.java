package utils;

import java.util.Set;
import database.AuctionDAO;
import model.auction.Auction;
import model.manager.AppState;
import model.user.Admin;
import model.user.Seller;
import model.user.User;

/**
 * Tập trung logic kiểm tra quyền Sửa / Xóa / Hủy phiên đấu giá.
 *
 * Quy ước:
 *  - canEdit:   sửa thông tin phiên (chỉ OPEN, chủ phiên hoặc admin)
 *  - canDelete: xóa hẳn phiên khỏi DB (admin: mọi lúc; seller: chỉ phiên chưa FINISHED của mình)
 *  - canCancel: chuyển trạng thái sang CANCELED (admin: mọi status; seller: OPEN/RUNNING của mình)
 */
public class SessionPermission {

    private static String cachedSellerId = null;
    private static Set<String> cachedOwnedIds = null;

    private SessionPermission() {
    }

    public static boolean canEdit(Auction auction) {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || auction == null) return false;
        if (!"OPEN".equals(auction.getStatus())) return false;
        if (user instanceof Admin) return true;
        if (user instanceof Seller) {
            return isOwnedBy(auction.getAuctionId(), user.getUserId());
        }
        return false;
    }

    public static boolean canDelete(Auction auction) {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || auction == null) return false;
        if (user instanceof Admin) return true;
        if (user instanceof Seller) {
            if ("FINISHED".equals(auction.getStatus())) return false;
            return isOwnedBy(auction.getAuctionId(), user.getUserId());
        }
        return false;
    }

    /**
     * Quyền HỦY phiên (chuyển sang CANCELED — không xóa khỏi DB).
     *
     *  - Admin:  cho phép trong mọi trạng thái TRỪ CANCELED và PAID (đã kết thúc final)
     *  - Seller: chỉ cho hủy phiên của mình ở trạng thái OPEN hoặc RUNNING
     */
    public static boolean canCancel(Auction auction) {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || auction == null) return false;

        String status = auction.getStatus();
        // Đã CANCELED hoặc đã PAID thì không ai cancel được nữa
        if ("CANCELED".equals(status) || "PAID".equals(status)) return false;

        if (user instanceof Admin) return true;

        if (user instanceof Seller) {
            // Seller chỉ được hủy OPEN/RUNNING của mình
            if (!"OPEN".equals(status) && !"RUNNING".equals(status)) return false;
            return isOwnedBy(auction.getAuctionId(), user.getUserId());
        }

        return false;
    }

    public static boolean canEditOrDelete(Auction auction) {
        return canEdit(auction) || canDelete(auction);
    }

    /**
     * Quyền thanh toán phiên — winner mới được thanh toán phiên đã FINISHED chưa PAID.
     * Việc kiểm tra balance được thực hiện trên server (atomic), client chỉ check
     * sơ bộ để hiển thị nút.
     *
     * @param auction      phiên đấu giá
     * @param winnerId     userId của người thắng (lấy từ auction.getHighestBid())
     */
    public static boolean canPay(Auction auction, String winnerId) {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null || auction == null || winnerId == null) return false;
        // Chỉ FINISHED chưa PAID/CANCELED mới cho thanh toán
        if (!"FINISHED".equals(auction.getStatus())) return false;
        // Phải đúng là winner
        return winnerId.equals(user.getUserId());
    }

    private static boolean isOwnedBy(String auctionId, String sellerId) {
        if (!sellerId.equals(cachedSellerId)) {
            cachedSellerId = sellerId;
            cachedOwnedIds = new AuctionDAO().getAuctionIdsBySeller(sellerId);
        }
        return cachedOwnedIds != null && cachedOwnedIds.contains(auctionId);
    }

    public static void invalidateCache() {
        cachedSellerId = null;
        cachedOwnedIds = null;
    }
}