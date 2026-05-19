package utils;

import controller.NotificationPopupController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import model.manager.AppState;
import model.notification.Notification;
import model.user.User;
import network.AuctionClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side helper: quản lý badge unread count + popup hiển thị danh sách thông báo.
 */
public class NotificationCenter {

    private static final List<Notification> CACHE = new ArrayList<>();
    private static int unreadCount = 0;
    private static Label badgeRef;
    private static Popup currentPopup;
    private static NotificationPopupController popupController;
    private static boolean wiredOnClient = false;
    private static Consumer<Notification> onAction;

    public static void attach(Node bellNode, Label badge) {
        attach(bellNode, badge, null);
    }

    public static void attach(Node bellNode, Label badge, Consumer<Notification> onClick) {
        badgeRef = badge;
        onAction = onClick;
        wireClientOnce();

        bellNode.setOnMouseClicked(e -> togglePopup(bellNode));
        bellNode.setStyle((bellNode.getStyle() == null ? "" : bellNode.getStyle()) + "; -fx-cursor: hand;");

        fetchNow();
        updateBadge();
    }

    private static void wireClientOnce() {
        if (wiredOnClient) return;
        AuctionClient client = AppState.getInstance().getClient();
        if (client == null) return;

        client.addNotificationBundleListener(bundle -> {
            CACHE.clear();
            for (Notification n : bundle.items) { if (n != null) CACHE.add(n); }
            unreadCount = (int) CACHE.stream().filter(n -> !n.isRead()).count();
            Platform.runLater(() -> {
                updateBadge();
                if (popupController != null) popupController.setData(new ArrayList<>(CACHE));
            });
        });

        client.addNotificationRefreshListener(() -> Platform.runLater(NotificationCenter::fetchNow));
        wiredOnClient = true;
    }

    public static void fetchNow() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        try {
            AppState.getInstance().getClient().send("FETCH_NOTIFICATIONS:" + user.getUserId());
        } catch (Exception ignore) {}
    }

    private static void updateBadge() {
        if (badgeRef == null) return;
        if (unreadCount <= 0) {
            badgeRef.setVisible(false); badgeRef.setManaged(false);
        } else {
            badgeRef.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            badgeRef.setVisible(true); badgeRef.setManaged(true);
        }
    }

    private static void togglePopup(Node anchor) {
        if (currentPopup != null && currentPopup.isShowing()) {
            currentPopup.hide(); currentPopup = null; popupController = null;
            return;
        }
        fetchNow();
        try {
            FXMLLoader loader = new FXMLLoader(NotificationCenter.class.getResource("/view/notification_popup.fxml"));
            javafx.scene.Parent root = loader.load();
            popupController = loader.getController();
            popupController.setData(new ArrayList<>(CACHE));
            popupController.setOnMarkAll(NotificationCenter::markAllRead);
            popupController.setOnMarkOne(NotificationCenter::markOneRead);
            popupController.setOnAction(n -> {
                if (currentPopup != null) currentPopup.hide();
                if (onAction != null) onAction.accept(n);
            });

            Popup popup = new Popup();
            popup.getContent().add(root);
            popup.setAutoHide(true);
            popup.show(anchor, anchor.localToScreen(anchor.getBoundsInLocal()).getMinX(), anchor.localToScreen(anchor.getBoundsInLocal()).getMinY() - 400);
            currentPopup = popup;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void markOneRead(Notification n) {
        n.setRead(true);
        unreadCount = Math.max(0, unreadCount - 1);
        updateBadge();
        if (popupController != null) popupController.setData(new ArrayList<>(CACHE));
        try { AppState.getInstance().getClient().send("MARK_NOTIFICATION_READ:" + n.getNotificationId()); } catch (Exception ignore) {}
    }

    private static void markAllRead() {
        for (Notification n : CACHE) n.setRead(true);
        unreadCount = 0;
        updateBadge();
        if (popupController != null) popupController.setData(new ArrayList<>(CACHE));
        try {
            User user = AppState.getInstance().getCurrentUser();
            if (user != null) AppState.getInstance().getClient().send("MARK_ALL_NOTIFICATIONS_READ:" + user.getUserId());
        } catch (Exception ignore) {}
    }

    public static void reset() {
        CACHE.clear(); unreadCount = 0; badgeRef = null;
        if (currentPopup != null) currentPopup.hide();
        currentPopup = null; popupController = null;
    }
}
