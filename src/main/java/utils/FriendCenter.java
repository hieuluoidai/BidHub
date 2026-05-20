package utils;

import javafx.application.Platform;
import model.friendship.Friendship;
import model.manager.AppState;
import model.user.User;
import network.AuctionClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side singleton: quản lý friend bundle real-time.
 * Wire 1 lần khi đăng nhập, dispatch bundle tới các controller đang lắng nghe.
 */
public final class FriendCenter {

    private static boolean wired = false;
    private static final List<Consumer<Friendship.Bundle>> BUNDLE_LISTENERS = new ArrayList<>();
    private static final List<Consumer<Friendship.SearchBundle>> SEARCH_LISTENERS = new ArrayList<>();

    private static Consumer<Friendship.Bundle> clientBundleListener;
    private static Consumer<Friendship.SearchBundle> clientSearchListener;
    private static Consumer<String> clientStringListener;

    private FriendCenter() {}

    public static synchronized void init() {
        if (wired) return;
        AuctionClient client = AppState.getInstance().getClient();
        if (client == null) return;

        clientBundleListener = bundle -> Platform.runLater(() -> {
            int pending = bundle.pending != null ? bundle.pending.size() : 0;
            AppState.getInstance().setPendingFriendCount(pending);
            for (var l : BUNDLE_LISTENERS) l.accept(bundle);
        });
        client.addFriendBundleListener(clientBundleListener);

        clientSearchListener = sb -> Platform.runLater(() -> {
            for (var l : SEARCH_LISTENERS) l.accept(sb);
        });
        client.addFriendSearchListener(clientSearchListener);

        clientStringListener = msg -> {
            if (msg.startsWith("FRIEND_REQUEST_OK:") || msg.startsWith("FRIEND_ACCEPT_OK:")) {
                fetchBundle();
            }
        };
        client.addStringMessageListener(clientStringListener);

        wired = true;
        fetchBundle();
    }

    public static void fetchBundle() {
        User u = AppState.getInstance().getCurrentUser();
        if (u == null) return;
        try {
            AppState.getInstance().getClient().send("FRIEND_LIST:" + u.getUserId());
        } catch (Exception ignore) {}
    }

    public static void search(String query) {
        User u = AppState.getInstance().getCurrentUser();
        if (u == null) return;
        try {
            AppState.getInstance().getClient().send("USER_SEARCH:" + u.getUserId() + ":" + query);
        } catch (Exception ignore) {}
    }

    public static void sendRequest(String toId) {
        User u = AppState.getInstance().getCurrentUser();
        if (u == null) return;
        try {
            AppState.getInstance().getClient().send("FRIEND_REQUEST:" + u.getUserId() + ":" + toId);
        } catch (Exception ignore) {}
    }

    public static void accept(String requesterId) {
        User u = AppState.getInstance().getCurrentUser();
        if (u == null) return;
        try {
            AppState.getInstance().getClient()
                    .send("FRIEND_ACCEPT:" + requesterId + ":" + u.getUserId());
        } catch (Exception ignore) {}
    }

    public static void decline(String requesterId) {
        User u = AppState.getInstance().getCurrentUser();
        if (u == null) return;
        try {
            AppState.getInstance().getClient()
                    .send("FRIEND_DECLINE:" + requesterId + ":" + u.getUserId());
        } catch (Exception ignore) {}
    }

    public static void addBundleListener(Consumer<Friendship.Bundle> l) {
        BUNDLE_LISTENERS.add(l);
    }
    public static void removeBundleListener(Consumer<Friendship.Bundle> l) {
        BUNDLE_LISTENERS.remove(l);
    }
    public static void addSearchListener(Consumer<Friendship.SearchBundle> l) {
        SEARCH_LISTENERS.add(l);
    }
    public static void removeSearchListener(Consumer<Friendship.SearchBundle> l) {
        SEARCH_LISTENERS.remove(l);
    }

    public static void reset() {
        BUNDLE_LISTENERS.clear();
        SEARCH_LISTENERS.clear();
        AppState.getInstance().setPendingFriendCount(0);
        if (wired) {
            AuctionClient client = AppState.getInstance().getClient();
            if (client != null) {
                if (clientBundleListener != null) client.removeFriendBundleListener(clientBundleListener);
                if (clientSearchListener != null) client.removeFriendSearchListener(clientSearchListener);
                if (clientStringListener != null) client.removeStringMessageListener(clientStringListener);
            }
        }
        clientBundleListener = null;
        clientSearchListener = null;
        clientStringListener = null;
        wired = false;
    }
}
