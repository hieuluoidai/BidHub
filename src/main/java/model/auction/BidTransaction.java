package model.auction;

import model.user.User;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Class cho 1 lần bid trong hệ thống.
 */
public class BidTransaction implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum BidType {
        MANUAL("Đặt thủ công"),
        AUTO_BID("Đặt tự động/Auto-bid");

        private final String displayName;

        BidType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final User bidder;
    private final double bidAmount;
    private final LocalDateTime timestamp;
    private final BidType bidType;

    public BidTransaction(User bidder, double bidAmount) {
        this(bidder, bidAmount, LocalDateTime.now(), BidType.MANUAL);
    }

    public BidTransaction(User bidder, double bidAmount, BidType bidType) {
        this(bidder, bidAmount, LocalDateTime.now(), bidType);
    }

    public BidTransaction(User bidder, double bidAmount, LocalDateTime timestamp, BidType bidType) {
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.bidType = bidType != null ? bidType : BidType.MANUAL;
    }

    public User getBidder() {
        return bidder;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public BidType getBidType() {
        return bidType;
    }

    @Override
    public String toString() {
        String name = (bidder != null) ? bidder.getUsername() : "Ẩn danh";
        return String.format("[%s] %s đã trả giá $%.2f (%s)",
                timestamp, name, bidAmount, bidType.getDisplayName());
    }
}
