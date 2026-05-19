package model.notification;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Đơn vị thông báo gửi cho 1 người dùng cụ thể.
 * Truyền qua socket (Serializable) hoặc lưu DB.
 */
public class Notification implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        // ===== User-facing =====
        WALLET_TOPUP,
        WALLET_PAYMENT,
        WALLET_EARNING,
        WALLET_REFUND,

        ITEM_POSTED,             // Phiên của bạn đã được đăng thành công
        AUCTION_NEW_BID,         // Có người vừa bid vào phiên của bạn (cho seller)
        AUCTION_OUTBID,          // Bạn bị mất quyền dẫn đầu
        AUCTION_WON,             // Bạn thắng phiên — cần thanh toán
        AUCTION_ENDED_SOLD,      // Phiên của bạn kết thúc — đã bán
        AUCTION_ENDED_NO_BID,    // Phiên của bạn kết thúc — không có bid
        AUCTION_EXTENDED,        // Anti-snipe: phiên được gia hạn
        SELLER_APPROVED,
        SELLER_REVOKED,

        // ===== Admin-only =====
        ADMIN_NEW_USER,
        ADMIN_NEW_SELLER_REQUEST,
        ADMIN_NEW_AUCTION
    }

    private String notificationId;
    private String userId;
    private Type type;
    private String title;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;

    public Notification() {}

    public Notification(String notificationId, String userId, Type type,
                        String title, String message, boolean read,
                        LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
    }

    public String getNotificationId() { return notificationId; }
    public String getUserId()         { return userId; }
    public Type getType()             { return type; }
    public String getTitle()          { return title; }
    public String getMessage()        { return message; }
    public boolean isRead()           { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setNotificationId(String v) { this.notificationId = v; }
    public void setUserId(String v) { this.userId = v; }
    public void setType(Type v)     { this.type = v; }
    public void setTitle(String v)  { this.title = v; }
    public void setMessage(String v){ this.message = v; }
    public void setRead(boolean v)  { this.read = v; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    /** Wrapper signal: client nhận object này → fetch lại danh sách thông báo. */
    public static final class RefreshSignal implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /** Wrapper signal: server gửi 1 danh sách thông báo về client. */
    public static final class Bundle implements Serializable {
        private static final long serialVersionUID = 1L;
        public final java.util.List<Notification> items;
        public Bundle(java.util.List<Notification> items) { this.items = items; }
    }
}
