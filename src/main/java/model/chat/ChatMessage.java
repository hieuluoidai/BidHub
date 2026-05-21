package model.chat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tin nhắn 1-1 giữa hai người dùng.
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String senderId;
    private String receiverId;
    private String content;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private boolean liked;
    /** true khi sender đã thu hồi tin nhắn với tất cả mọi người. */
    private boolean recalled;

    public ChatMessage() {}

    public ChatMessage(String messageId, String senderId, String receiverId,
                       String content, LocalDateTime sentAt,
                       LocalDateTime readAt, boolean liked) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.sentAt = sentAt;
        this.readAt = readAt;
        this.liked = liked;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isLiked() {
        return liked;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void setMessageId(String v) {
        this.messageId = v;
    }

    public void setSenderId(String v) {
        this.senderId = v;
    }

    public void setReceiverId(String v) {
        this.receiverId = v;
    }

    public void setContent(String v) {
        this.content = v;
    }

    public void setSentAt(LocalDateTime v) {
        this.sentAt = v;
    }

    public void setReadAt(LocalDateTime v) {
        this.readAt = v;
    }

    public void setLiked(boolean v) {
        this.liked = v;
    }

    public boolean isRecalled() {
        return recalled;
    }

    public void setRecalled(boolean v) {
        this.recalled = v;
    }

    /** Bundle phản hồi: danh sách tin nhắn của 1 conversation. */
    public static final class Bundle implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String partnerId;
        public final List<ChatMessage> items;
        public Bundle(String partnerId, List<ChatMessage> items) {
            this.partnerId = partnerId;
            this.items = items;
        }
    }

    /** Tóm tắt 1 cuộc trò chuyện (cho danh sách bên trái). */
    public static final class Summary implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String partnerId;
        public final String partnerUsername;
        public final String partnerAvatarPath;
        public final String lastMessage;
        public final LocalDateTime lastAt;
        public final int unreadCount;
        public final boolean lastFromMe;

        public Summary(String partnerId, String partnerUsername, String partnerAvatarPath,
                       String lastMessage, LocalDateTime lastAt,
                       int unreadCount, boolean lastFromMe) {
            this.partnerId = partnerId;
            this.partnerUsername = partnerUsername;
            this.partnerAvatarPath = partnerAvatarPath;
            this.lastMessage = lastMessage;
            this.lastAt = lastAt;
            this.unreadCount = unreadCount;
            this.lastFromMe = lastFromMe;
        }
    }

    /** Bundle danh sách conversation. */
    public static final class SummaryBundle implements Serializable {
        private static final long serialVersionUID = 1L;
        public final List<Summary> items;
        public final int totalUnread;
        public SummaryBundle(List<Summary> items, int totalUnread) {
            this.items = items;
            this.totalUnread = totalUnread;
        }
    }
}
