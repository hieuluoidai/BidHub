package exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lỗi xảy ra khi người dùng đặt giá vào một phiên không ở trạng thái RUNNING.
 *
 * <p>Tên class vẫn giữ là AuctionClosedException để tương thích với code cũ, nhưng mã lỗi chuẩn là
 * AUCTION_NOT_RUNNING vì phiên có thể chưa bắt đầu, đã kết thúc hoặc đã bị hủy,
 * chứ không chỉ là trạng thái "closed".
 */
public class AuctionClosedException extends AuctionException {
    private static final long serialVersionUID = 1L;

    private final String status;

    public AuctionClosedException(String auctionId, String status) {
        super(ErrorCode.AUCTION_NOT_RUNNING, auctionId,
                String.format("Phiên đấu giá '%s' không thể đặt giá (Trạng thái: %s).", auctionId, status),
                details(status));
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    /**
     * Ghi rõ trạng thái bắt buộc và trạng thái thực tế để log/debug không phải đọc lại message.
     */
    private static Map<String, String> details(String status) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("requiredStatus", "RUNNING");
        if (status != null) {
            details.put("actualStatus", status);
        }
        return details;
    }
}
