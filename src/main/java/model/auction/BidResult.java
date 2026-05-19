package model.auction;

import java.io.Serializable;

/**
 * Kết quả của một lần đặt giá — được gửi từ Server về đúng Client đã bid.
 *
 * Có 3 trạng thái:
 * SUCCESS  — bid thành công, bạn đang dẫn đầu
 * OUTBID   — bid thất bại vì người khác đã vào giá cao hơn trong cùng lúc
 * FAILURE  — bid không hợp lệ (phiên đóng, sai định dạng, v.v.)
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

    private BidResult(Status status, String auctionId, double bidAmount,
                      double currentPrice, String message, String winnerUsername) {
        this.status         = status;
        this.auctionId      = auctionId;
        this.bidAmount      = bidAmount;
        this.currentPrice   = currentPrice;
        this.message        = message;
        this.winnerUsername = winnerUsername;
    }

    /** Bid thành công — bidder đang dẫn đầu. */
    public static BidResult success(String auctionId, double amount, String winnerUsername) {
        return new BidResult(Status.SUCCESS, auctionId, amount, amount,
                String.format("✅ Đặt giá %,.0f ₫ thành công! Bạn đang dẫn đầu.", amount),
                winnerUsername);
    }

    /** Bị vượt giá — người khác đã đặt giá cao hơn trong khoảnh khắc này. */
    public static BidResult outbid(String auctionId, double yourAmount,
                                   double currentPrice, String message) {
        return new BidResult(Status.OUTBID, auctionId, yourAmount, currentPrice, message, null);
    }

    /** Bid không hợp lệ — phiên đóng, sai số, người dùng không tồn tại... */
    public static BidResult failure(String auctionId, double amount, String reason) {
        return new BidResult(Status.FAILURE, auctionId, amount, -1, reason, null);
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

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isOutbid() {
        return status == Status.OUTBID;
    }

    @Override
    public String toString() {
        return String.format("[BidResult %s | auction=%s | bid=$%.2f | current=$%.2f | %s]",
                status, auctionId, bidAmount, currentPrice, message);
    }
}
