package model.auction;

import exception.AppException;
import exception.ErrorCode;
import exception.ExceptionMapper;
import java.io.Serializable;

/**
 * Kết quả của một lần đặt giá, được server gửi riêng về client vừa thực hiện bid.
 *
 * <p>BidResult khác ErrorResponse ở chỗ nó là phản hồi nghiệp vụ của thao tác đặt giá:
 * thành công, bị vượt giá, hoặc thất bại có kiểm soát. Khi thất bại, object này vẫn mang ErrorCode
 * để UI có thể hiển thị đúng thông báo và test có thể kiểm tra lỗi mà không phụ thuộc vào text.
 */
public class BidResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status { SUCCESS, OUTBID, FAILURE }

    private final Status status;
    private final String auctionId;
    private final double bidAmount;
    private final double currentPrice;
    private final String message;
    private final String winnerUsername;
    private final ErrorCode errorCode;

    private BidResult(Status status, String auctionId, double bidAmount,
                      double currentPrice, String message, String winnerUsername,
                      ErrorCode errorCode) {
        this.status = status;
        this.auctionId = auctionId;
        this.bidAmount = bidAmount;
        this.currentPrice = currentPrice;
        this.message = message;
        this.winnerUsername = winnerUsername;
        this.errorCode = errorCode;
    }

    /**
     * Tạo kết quả thành công. currentPrice được đặt bằng amount vì sau bid thành công,
     * giá hiện tại của phiên chính là số tiền vừa được chấp nhận.
     */
    public static BidResult success(String auctionId, double amount, String winnerUsername) {
        return new BidResult(Status.SUCCESS, auctionId, amount, amount,
                String.format("Đặt giá %,.0f ₫ thành công! Bạn đang dẫn đầu.", amount),
                winnerUsername, null);
    }

    /**
     * Tạo kết quả OUTBID mặc định. Trường hợp này xảy ra khi giá user gửi lên
     * không còn cao hơn giá hiện tại sau khi server kiểm tra lại trong lock.
     */
    public static BidResult outbid(String auctionId, double yourAmount, double currentPrice) {
        return outbid(auctionId, yourAmount, currentPrice, ErrorCode.BID_TOO_LOW,
                String.format("Giá đặt (%,.0f ₫) phải cao hơn giá hiện tại (%,.0f ₫).",
                        yourAmount, currentPrice));
    }

    /**
     * Giữ tương thích với các chỗ gọi cũ truyền message trực tiếp.
     * Dù message là custom, mã lỗi vẫn được chuẩn hóa là BID_TOO_LOW.
     */
    public static BidResult outbid(String auctionId, double yourAmount,
                                   double currentPrice, String message) {
        return outbid(auctionId, yourAmount, currentPrice, ErrorCode.BID_TOO_LOW, message);
    }

    public static BidResult outbid(String auctionId, double yourAmount,
                                   double currentPrice, ErrorCode errorCode, String message) {
        ErrorCode resolvedCode = resolveErrorCode(errorCode, ErrorCode.BID_TOO_LOW);
        return new BidResult(Status.OUTBID, auctionId, yourAmount, currentPrice,
                resolveMessage(resolvedCode, message), null, resolvedCode);
    }

    public static BidResult outbid(String auctionId, double yourAmount,
                                   double currentPrice, AppException exception) {
        return outbid(auctionId, yourAmount, currentPrice,
                exception.getErrorCode(), exception.getUserMessage());
    }

    /**
     * Tạo kết quả thất bại từ chuỗi reason cũ. Các chỗ mới nên dùng overload có ErrorCode
     * để lỗi rõ nghĩa hơn, nhưng overload này vẫn giữ để không phá vỡ code/test hiện có.
     */
    public static BidResult failure(String auctionId, double amount, String reason) {
        return failure(auctionId, amount, ErrorCode.INTERNAL_ERROR, reason);
    }

    public static BidResult failure(String auctionId, double amount, ErrorCode errorCode, String message) {
        ErrorCode resolvedCode = resolveErrorCode(errorCode, ErrorCode.INTERNAL_ERROR);
        return new BidResult(Status.FAILURE, auctionId, amount, -1,
                resolveMessage(resolvedCode, message), null, resolvedCode);
    }

    /**
     * Chuyển AppException thành BidResult để tầng bid không phải tự tách code/message.
     */
    public static BidResult failure(String auctionId, double amount, AppException exception) {
        return failure(auctionId, amount, exception.getErrorCode(), exception.getUserMessage());
    }

    /**
     * Chuyển mọi lỗi bất ngờ thành BidResult thông qua ExceptionMapper.
     */
    public static BidResult failure(String auctionId, double amount, Throwable throwable) {
        return failure(auctionId, amount, ExceptionMapper.map(throwable));
    }

    public Status getStatus() {
        return status;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public String getMessage() {
        return message;
    }

    public String getWinnerUsername() {
        return winnerUsername;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorCodeValue() {
        return errorCode == null ? null : errorCode.getCode();
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isOutbid() {
        return status == Status.OUTBID;
    }

    @Override
    public String toString() {
        return String.format("[BidResult %s | code=%s | auction=%s | bid=%.2f | current=%.2f | %s]",
                status, getErrorCodeValue(), auctionId, bidAmount, currentPrice, message);
    }

    private static ErrorCode resolveErrorCode(ErrorCode errorCode, ErrorCode fallback) {
        return errorCode != null ? errorCode : fallback;
    }

    private static String resolveMessage(ErrorCode errorCode, String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return errorCode.getDefaultMessage();
    }
}
