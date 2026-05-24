package network.handler;

import database.ChatMessageDAO;
import database.FriendshipDAO;
import database.NotificationDAO;
import database.UserDAO;
import exception.ErrorCode;
import exception.ErrorResponse;
import model.chat.ChatMessage;
import model.friendship.Friendship;
import model.notification.Notification;
import model.user.User;
import network.ClientHandler;
import utils.NotificationService;

import java.util.List;

/**
 * Handles chat and friendship-related commands.
 */
public class SocialHandler implements RequestHandler {

    @Override
    public void handle(ClientHandler context, String msg) {
        if (msg.startsWith("CHAT_SEND:")) {
            handleChatSend(context, msg);
        } else if (msg.startsWith("CHAT_FETCH:")) {
            handleChatFetch(context, msg);
        } else if (msg.startsWith("CHAT_FETCH_LIST:")) {
            handleChatFetchList(context, msg);
        } else if (msg.startsWith("CHAT_MARK_READ:")) {
            handleChatMarkRead(context, msg);
        } else if (msg.startsWith("CHAT_LIKE:")) {
            handleChatLike(context, msg);
        } else if (msg.startsWith("CHAT_RECALL:")) {
            handleChatRecall(context, msg);
        } else if (msg.startsWith("FRIEND_REQUEST:")) {
            handleFriendRequest(context, msg);
        } else if (msg.startsWith("FRIEND_ACCEPT:")) {
            handleFriendAccept(context, msg);
        } else if (msg.startsWith("FRIEND_DECLINE:")) {
            handleFriendDecline(context, msg);
        } else if (msg.startsWith("FRIEND_LIST:")) {
            handleFriendList(context, msg);
        } else if (msg.startsWith("FRIEND_STATUS:")) {
            handleFriendStatus(context, msg);
        } else if (msg.startsWith("USER_SEARCH:")) {
            handleUserSearch(context, msg);
        }
    }

    private void handleChatSend(ClientHandler context, String msg) {
        String[] parts = msg.split(":", 4);
        if (parts.length < 4) {
            context.send("CHAT_SEND_FAILED:Sai định dạng");
            return;
        }
        String senderId = parts[1];
        String receiverId = parts[2];
        String content = parts[3];
        if (content == null || content.trim().isEmpty()) {
            context.send("CHAT_SEND_FAILED:Nội dung rỗng");
            return;
        }
        if (content.length() > 2000) content = content.substring(0, 2000);

        ChatMessageDAO dao = new ChatMessageDAO();
        ChatMessage saved = dao.insert(senderId, receiverId, content);
        if (saved == null) {
            context.send("CHAT_SEND_FAILED:Lỗi DB");
            return;
        }

        context.send(saved);
        context.getServer().sendToUser(receiverId, saved);

        UserDAO userDao = new UserDAO();
        User sender = userDao.findById(senderId);
        String senderName = sender != null ? sender.getUsername() : senderId;
        String preview = content.length() > 80 ? content.substring(0, 77) + "..." : content;
        NotificationService.notifyChat(context.getServer(), receiverId, senderId, senderName, preview);
    }

    private void handleChatFetch(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String userId = parts[1];
        String partnerId = parts[2];
        List<ChatMessage> list = new ChatMessageDAO().findConversation(userId, partnerId, 200);
        context.send(new ChatMessage.Bundle(partnerId, list));
    }

    private void handleChatFetchList(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 2) return;
        String userId = parts[1];
        ChatMessageDAO dao = new ChatMessageDAO();
        List<ChatMessage.Summary> summaries = dao.findSummaries(userId);
        int total = dao.getTotalUnread(userId);
        context.send(new ChatMessage.SummaryBundle(summaries, total));
    }

    private void handleChatMarkRead(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String userId = parts[1];
        String partnerId = parts[2];
        List<String> marked = new ChatMessageDAO().markConversationRead(userId, partnerId);
        if (marked.isEmpty()) return;

        new NotificationDAO().markChatAsRead(userId, partnerId);

        context.getServer().sendToUser(partnerId, "CHAT_READ:" + userId + ":" + String.join(",", marked));
        context.send("CHAT_UNREAD_UPDATED:" + userId);
    }

    private void handleChatLike(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        String messageId = parts[1];
        boolean liked = "1".equals(parts[2]);
        ChatMessageDAO dao = new ChatMessageDAO();
        if (!dao.setLiked(messageId, liked)) return;
        ChatMessage updated = dao.findById(messageId);
        if (updated == null) return;

        context.getServer().sendToUser(updated.getSenderId(), updated);
        context.getServer().sendToUser(updated.getReceiverId(), updated);

        if (liked && context.getUserId() != null && !context.getUserId().equals(updated.getSenderId())) {
            UserDAO userDao = new UserDAO();
            User liker = userDao.findById(context.getUserId());
            String likerName = liker != null ? liker.getUsername() : context.getUserId();
            String preview = updated.getContent();
            if (preview != null && preview.length() > 60) preview = preview.substring(0, 57) + "...";
            NotificationService.notifyUser(context.getServer(), updated.getSenderId(),
                    Notification.Type.CHAT_LIKED,
                    likerName + " đã thích tin nhắn của bạn",
                    preview);
        }
    }

    private void handleChatRecall(ClientHandler context, String msg) {
        String[] parts = msg.split(":", 3);
        if (parts.length < 3) {
            context.send("CHAT_RECALL_FAILED:Sai định dạng");
            return;
        }
        String messageId = parts[1];
        String mode = parts[2];
        String requesterId = context.getUserId();
        if (requesterId == null) {
            context.send("CHAT_RECALL_FAILED:Chưa xác thực");
            return;
        }

        ChatMessageDAO dao = new ChatMessageDAO();
        if ("ALL".equals(mode)) {
            ChatMessage updated = dao.recallForAll(messageId, requesterId);
            if (updated == null) {
                context.send("CHAT_RECALL_FAILED:Không thể thu hồi");
                return;
            }
            context.getServer().sendToUser(updated.getSenderId(), updated);
            context.getServer().sendToUser(updated.getReceiverId(), updated);
        } else if ("SELF".equals(mode)) {
            if (!dao.hideSelf(messageId, requesterId)) {
                context.send("CHAT_RECALL_FAILED:Không thể ẩn");
                return;
            }
            context.send("CHAT_RECALLED_SELF:" + messageId);
        } else {
            context.send("CHAT_RECALL_FAILED:Mode không hợp lệ");
        }
    }

    private void handleFriendRequest(ClientHandler context, String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts(context, "FRIEND_REQUEST", p, 3)) return;
        
        String fromId = p[1], toId = p[2];
        if (fromId.equals(toId)) {
            context.send("FRIEND_REQUEST_FAILED:Không thể kết bạn với chính mình");
            return;
        }
        FriendshipDAO dao = new FriendshipDAO();
        if (!dao.sendRequest(fromId, toId)) {
            context.send("FRIEND_REQUEST_FAILED:Đã tồn tại quan hệ");
            return;
        }
        context.send("FRIEND_REQUEST_OK:" + toId);
        pushFriendBundle(context, fromId);
        pushFriendBundle(context, toId);
        
        UserDAO userDao = new UserDAO();
        User from = userDao.findById(fromId);
        String fromName = from != null ? from.getUsername() : fromId;
        NotificationService.notifyUser(context.getServer(), toId,
                Notification.Type.FRIEND_REQUEST,
                "Lời mời kết bạn",
                fromName + " muốn kết bạn với bạn");
    }

    private void handleFriendAccept(ClientHandler context, String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts(context, "FRIEND_ACCEPT", p, 3)) return;
        
        String requesterId = p[1], addresseeId = p[2];
        FriendshipDAO dao = new FriendshipDAO();
        if (!dao.accept(requesterId, addresseeId)) {
            context.send("FRIEND_ACCEPT_FAILED");
            return;
        }
        context.send("FRIEND_ACCEPT_OK:" + requesterId);
        pushFriendBundle(context, requesterId);
        pushFriendBundle(context, addresseeId);
        
        UserDAO userDao = new UserDAO();
        User addressee = userDao.findById(addresseeId);
        String addresseeName = addressee != null ? addressee.getUsername() : addresseeId;
        NotificationService.notifyUser(context.getServer(), requesterId,
                Notification.Type.FRIEND_ACCEPTED,
                "Lời mời kết bạn",
                addresseeName + " đã chấp nhận lời mời kết bạn của bạn");
    }

    private void handleFriendDecline(ClientHandler context, String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts(context, "FRIEND_DECLINE", p, 3)) return;
        
        if (!new FriendshipDAO().decline(p[1], p[2])) {
            context.send("FRIEND_DECLINE_FAILED");
            return;
        }
        pushFriendBundle(context, p[1]);
        pushFriendBundle(context, p[2]);
    }

    private void handleFriendList(ClientHandler context, String msg) {
        String[] p = msg.split(":", 2);
        if (!requireFriendCommandParts(context, "FRIEND_LIST", p, 2)) return;
        context.send(new FriendshipDAO().getFriendBundle(p[1]));
    }

    private void handleFriendStatus(ClientHandler context, String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts(context, "FRIEND_STATUS", p, 3)) return;
        String status = new FriendshipDAO().getStatus(p[1], p[2]);
        context.send("FRIEND_STATUS_RESULT:" + p[2] + ":" + status);
    }

    private void handleUserSearch(ClientHandler context, String msg) {
        String[] p = msg.split(":", 3);
        if (!requireFriendCommandParts(context, "USER_SEARCH", p, 3)) return;
        context.send(new FriendshipDAO().search(p[1], p[2]));
    }

    private boolean requireFriendCommandParts(ClientHandler context, String command, String[] parts, int minLength) {
        if (parts.length >= minLength) return true;
        context.send(ErrorResponse.of(ErrorCode.COMMAND_FORMAT_INVALID, "Lệnh " + command + " sai định dạng."));
        return false;
    }

    private void pushFriendBundle(ClientHandler context, String userId) {
        Friendship.Bundle bundle = new FriendshipDAO().getFriendBundle(userId);
        context.getServer().sendToUser(userId, bundle);
    }
}
