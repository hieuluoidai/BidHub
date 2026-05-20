package utils;

import javafx.application.Platform;
import model.chat.ChatMessage;
import model.manager.AppState;
import model.user.User;
import network.AuctionClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Helper client-side: quản lý real-time chat — wire 1 lần lúc đăng nhập,
 * cập nhật badge "Tin nhắn" trên sidebar, và dispatch tin nhắn tới controllers đang mở.
 */
public final class ChatCenter {

    private static boolean wired = false;
    private static final List<Consumer<ChatMessage>> MESSAGE_LISTENERS = new ArrayList<>();
    private static final List<Consumer<ChatMessage.Bundle>> BUNDLE_LISTENERS = new ArrayList<>();
    private static final List<Consumer<ChatMessage.SummaryBundle>> SUMMARY_LISTENERS = new ArrayList<>();
    private static final List<Consumer<String>> READ_RECEIPT_LISTENERS = new ArrayList<>();

    private ChatCenter() {}

    public static synchronized void init() {
        if (wired) return;
        AuctionClient client = AppState.getInstance().getClient();
        if (client == null) return;

        client.addChatMessageListener(m -> Platform.runLater(() -> {
            for (var l : MESSAGE_LISTENERS) {
                l.accept(m);
            }
            User cu = AppState.getInstance().getCurrentUser();
            // Nếu là tin tới mình và chưa đọc → refresh badge
            if (cu != null && cu.getUserId().equals(m.getReceiverId()) && !m.isRead()) {
                fetchSummariesForBadge();
            }
        }));
        client.addChatBundleListener(b -> Platform.runLater(() -> {
            for (var l : BUNDLE_LISTENERS) {
                l.accept(b);
            }
        }));
        client.addChatSummaryListener(sb -> Platform.runLater(() -> {
            AppState.getInstance().setTotalUnreadChat(sb.totalUnread);
            for (var l : SUMMARY_LISTENERS) {
                l.accept(sb);
            }
        }));
        client.addStringMessageListener(msg -> {
            if (msg.startsWith("CHAT_READ:")) {
                Platform.runLater(() -> {
                    for (var l : READ_RECEIPT_LISTENERS) {
                        l.accept(msg);
                    }
                });
            } else if (msg.startsWith("CHAT_UNREAD_UPDATED:")) {
                fetchSummariesForBadge();
            }
        });

        wired = true;
        fetchSummariesForBadge();
    }

    public static void fetchSummariesForBadge() {
        User u = AppState.getInstance().getCurrentUser();
        if (u == null) return;
        try {
            AppState.getInstance().getClient().send("CHAT_FETCH_LIST:" + u.getUserId());
        } catch (Exception ignore) {}
    }

    public static void addMessageListener(Consumer<ChatMessage> l) {
        MESSAGE_LISTENERS.add(l);
    }

    public static void removeMessageListener(Consumer<ChatMessage> l) {
        MESSAGE_LISTENERS.remove(l);
    }

    public static void addBundleListener(Consumer<ChatMessage.Bundle> l) {
        BUNDLE_LISTENERS.add(l);
    }

    public static void removeBundleListener(Consumer<ChatMessage.Bundle> l) {
        BUNDLE_LISTENERS.remove(l);
    }

    public static void addSummaryListener(Consumer<ChatMessage.SummaryBundle> l) {
        SUMMARY_LISTENERS.add(l);
    }

    public static void removeSummaryListener(Consumer<ChatMessage.SummaryBundle> l) {
        SUMMARY_LISTENERS.remove(l);
    }

    public static void addReadReceiptListener(Consumer<String> l) {
        READ_RECEIPT_LISTENERS.add(l);
    }

    public static void removeReadReceiptListener(Consumer<String> l) {
        READ_RECEIPT_LISTENERS.remove(l);
    }

    public static void reset() {
        MESSAGE_LISTENERS.clear();
        BUNDLE_LISTENERS.clear();
        SUMMARY_LISTENERS.clear();
        READ_RECEIPT_LISTENERS.clear();
        AppState.getInstance().setTotalUnreadChat(0);
        wired = false;
    }
}
