package model.friendship;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class Friendship implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { PENDING, ACCEPTED, DECLINED }

    private String requesterId;
    private String addresseeId;
    private Status status;
    private LocalDateTime createdAt;

    // Thông tin partner (người còn lại) — điền khi query
    private String partnerId;
    private String partnerUsername;
    private String partnerAvatarPath;
    private String partnerRole;

    public Friendship() {
    }

    public String getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(String v) {
        this.requesterId = v;
    }

    public String getAddresseeId() {
        return addresseeId;
    }

    public void setAddresseeId(String v) {
        this.addresseeId = v;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status v) {
        this.status = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime v) {
        this.createdAt = v;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(String v) {
        this.partnerId = v;
    }

    public String getPartnerUsername() {
        return partnerUsername;
    }

    public void setPartnerUsername(String v) {
        this.partnerUsername = v;
    }

    public String getPartnerAvatarPath() {
        return partnerAvatarPath;
    }

    public void setPartnerAvatarPath(String v) {
        this.partnerAvatarPath = v;
    }

    public String getPartnerRole() {
        return partnerRole;
    }

    public void setPartnerRole(String v) {
        this.partnerRole = v;
    }

    /** Danh sách bạn bè + lời mời đang chờ gửi về client. */
    public static final class Bundle implements Serializable {
        private static final long serialVersionUID = 1L;
        public final List<Friendship> friends;   // status = ACCEPTED
        public final List<Friendship> pending;   // status = PENDING, addressee = me
        public Bundle(List<Friendship> friends, List<Friendship> pending) {
            this.friends = friends;
            this.pending = pending;
        }
    }

    /** Kết quả tìm kiếm user — kèm trạng thái quan hệ với người tìm. */
    public static final class SearchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String userId;
        public final String username;
        public final String avatarPath;
        public final String role;
        public final String friendStatus; // "NONE","PENDING_SENT","PENDING_RECEIVED","ACCEPTED","SELF"
        public SearchResult(String userId, String username, String avatarPath,
                            String role, String friendStatus) {
            this.userId = userId;
            this.username = username;
            this.avatarPath = avatarPath;
            this.role = role;
            this.friendStatus = friendStatus;
        }
    }

    /** Bundle kết quả tìm kiếm. */
    public static final class SearchBundle implements Serializable {
        private static final long serialVersionUID = 1L;
        public final List<SearchResult> items;
        public SearchBundle(List<SearchResult> items) {
            this.items = items;
        }
    }
}
