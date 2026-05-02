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
 *
 * Quy tắc:
 *   - Admin: được sửa/xóa MỌI phiên có status = OPEN
 *   - Seller: chỉ được sửa/xóa phiên CỦA MÌNH có status = OPEN
 *   - Bidder: không có quyền sửa/xóa
 *
 * Lý do giới hạn ở status = OPEN:
 *   Khi phiên đã chuyển sang RUNNING, có thể đã có người đặt giá.
 *   Nếu cho sửa giá khởi điểm hay xóa phiên lúc này → mất công bằng,
 *   gây tranh chấp và phá vỡ tính toàn vẹn dữ liệu (lost update).
 */
public class SessionPermission {

    // Cache nhỏ, tránh query DB lặp lại trong cùng 1 phiên người dùng
    private static String cachedSellerId = null;
    private static Set<String> cachedOwnedIds = null;

    private SessionPermission() {} // Utility class — không cho khởi tạo

    /**
     * Người dùng hiện tại có được sửa/xóa phiên này không?
     */
    /**
     * Có được SỬA phiên này không?
     * Quy tắc: Admin và Seller chỉ sửa được khi phiên đang OPEN.
     * Khi phiên đã RUNNING/FINISHED → không cho sửa nữa (đã có người tham gia hoặc đã đóng).
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
     * Quy tắc:
     *   - Admin: xóa được MỌI trạng thái (OPEN, RUNNING, FINISHED)
     *   - Seller: xóa được OPEN và RUNNING (KHÔNG xóa được FINISHED)
     *   - Bidder: không có quyền
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
     * Code cũ gọi method này để bật/tắt khu vực action — vẫn chạy được.
     */
    public static boolean canEditOrDelete(Auction auction) {
        return canEdit(auction) || canDelete(auction);
    }

    /**
     * Kiểm tra phiên đấu giá có thuộc sở hữu của seller không.
     * Có cache đơn giản để giảm query DB liên tiếp khi load nhiều phiên.
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
     * Đảm bảo cache không trả kết quả "stale" (cũ).
     */
    public static void invalidateCache() {
        cachedSellerId = null;
        cachedOwnedIds = null;
    }
}