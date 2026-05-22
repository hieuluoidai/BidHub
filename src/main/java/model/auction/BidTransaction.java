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
    private boolean isAnonymous = false;
    private String anonymousDisplayName; // Ví dụ: AnonymousBidder_001

    public BidTransaction(User bidder, double bidAmount) {
        this(bidder, bidAmount, LocalDateTime.now(), BidType.MANUAL, false);
    }

    public BidTransaction(User bidder, double bidAmount, BidType bidType) {
        this(bidder, bidAmount, LocalDateTime.now(), bidType, false);
    }

    public BidTransaction(User bidder, double bidAmount, LocalDateTime timestamp, BidType bidType) {
        this(bidder, bidAmount, timestamp, bidType, false);
    }

    public BidTransaction(User bidder, double bidAmount, LocalDateTime timestamp, BidType bidType, boolean isAnonymous) {
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.bidType = bidType != null ? bidType : BidType.MANUAL;
        this.isAnonymous = isAnonymous;
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

    public boolean isAnonymous() {
        return isAnonymous;
    }

    public void setAnonymous(boolean anonymous) {
        isAnonymous = anonymous;
    }

    public String getAnonymousDisplayName() {
        return anonymousDisplayName;
    }

    public void setAnonymousDisplayName(String anonymousDisplayName) {
        this.anonymousDisplayName = anonymousDisplayName;
    }

    @Override
    public String toString() {
        String name;
        if (isAnonymous) {
            name = (anonymousDisplayName != null) ? anonymousDisplayName : "AnonymousBidder";
        } else {
            name = (bidder != null) ? bidder.getUsername() : "Ẩn danh";
        }
        return String.format("[%s] %s đã trả giá $%.2f (%s)",
                timestamp, name, bidAmount, bidType.getDisplayName());
    }
}
