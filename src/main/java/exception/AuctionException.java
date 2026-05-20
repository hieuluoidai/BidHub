package exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception gốc cho các lỗi liên quan trực tiếp đến phiên đấu giá.
 *
 * <p>Ngoài các thông tin chung từ AppException, class này luôn cố gắng gắn thêm auctionId vào details.
 * Nhờ vậy khi log hoặc gửi lỗi về client, ta biết lỗi xảy ra ở phiên nào
 * mà không phải parse từ message.
 */
public class AuctionException extends AppException {
    private static final long serialVersionUID = 1L;

    private final String auctionId;

    public AuctionException(ErrorCode errorCode, String auctionId, String message) {
        this(errorCode, auctionId, message, null, null);
    }

    public AuctionException(ErrorCode errorCode, String auctionId, String message,
                            Map<String, String> details) {
        this(errorCode, auctionId, message, details, null);
    }

    public AuctionException(ErrorCode errorCode, String auctionId, String message,
                            Map<String, String> details, Throwable cause) {
        super(errorCode, message, withAuctionId(auctionId, details), cause);
        this.auctionId = auctionId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    /**
     * Gộp auctionId vào details do subclass truyền lên. Nếu subclass cũng có details riêng
     * như currentPrice hoặc attemptedAmount thì các giá trị đó vẫn được giữ lại.
     */
    private static Map<String, String> withAuctionId(String auctionId, Map<String, String> details) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (auctionId != null) {
            merged.put("auctionId", auctionId);
        }
        if (details != null) {
            merged.putAll(details);
        }
        return merged;
    }
}
