package exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lỗi đặt giá không hợp lệ ở tầng model đấu giá.
 *
 * <p>Class này bao phủ hai nhóm lỗi gần nhau: giá đặt không cao hơn giá hiện tại
 * và số tiền đặt không phải số dương hợp lệ. Mỗi trường hợp vẫn có ErrorCode riêng
 * để client phân biệt lỗi nghiệp vụ với lỗi nhập liệu.
 */
public class InvalidBidException extends AuctionException {
    private static final long serialVersionUID = 1L;

    private final double attemptedAmount;
    private final double currentPrice;

    public InvalidBidException(double attemptedAmount, double currentPrice) {
        this(null, attemptedAmount, currentPrice);
    }

    public InvalidBidException(String auctionId, double attemptedAmount, double currentPrice) {
        this(ErrorCode.BID_TOO_LOW, auctionId, attemptedAmount, currentPrice,
                String.format("Giá đặt (%,.0f ₫) phải cao hơn giá hiện tại (%,.0f ₫).",
                        attemptedAmount, currentPrice));
    }

    private InvalidBidException(ErrorCode errorCode, String auctionId, double attemptedAmount,
                                double currentPrice, String message) {
        super(errorCode, auctionId, message, details(attemptedAmount, currentPrice));
        this.attemptedAmount = attemptedAmount;
        this.currentPrice = currentPrice;
    }

    /**
     * Factory cho trường hợp số tiền không hợp lệ ngay từ đầu: âm, bằng 0, NaN hoặc Infinity.
     */
    public static InvalidBidException invalidAmount(String auctionId, double attemptedAmount) {
        return new InvalidBidException(ErrorCode.VALIDATION_ERROR, auctionId, attemptedAmount, Double.NaN,
                "Số tiền đặt giá phải là số dương hợp lệ.");
    }

    public double getAttemptedAmount() {
        return attemptedAmount;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    /**
     * Lưu số tiền user nhập và giá hiện tại để log/test có thể kiểm tra chính xác nguyên nhân lỗi.
     */
    private static Map<String, String> details(double attemptedAmount, double currentPrice) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("attemptedAmount", Double.toString(attemptedAmount));
        if (!Double.isNaN(currentPrice)) {
            details.put("currentPrice", Double.toString(currentPrice));
        }
        return details;
    }
}
