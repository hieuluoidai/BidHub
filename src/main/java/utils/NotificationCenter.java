package utils;

import controller.NotificationPopupController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import model.manager.AppState;
import model.notification.Notification;
import network.AuctionClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side helper: gắn icon chuông vào một StackPane, quản lý badge
 * unread count + popup hiển thị danh sách thông báo.
 *
 * Cách dùng (trong Controller dashboard):
 *   NotificationCenter.attach(bellStackPane, badgeLabel);
 */
public class NotificationCenter {

    private static final List<Notification> CACHE = new ArrayList<>();
    private static int unreadCount = 0;
    private static Label badgeRef;
    private static Popup currentPopup;
    private static NotificationPopupController popupController;
    private static boolean wiredOnClient = false;

    /** Gắn vào icon bell + label badge. Có thể gọi nhiều lần (mỗi khi vào dashboard mới). */
    public static void attach(Node bellNode, Label badge) {
        badgeRef = badge;
        wireClientOnce();

        bellNode.setOnMouseClicked(e -> togglePopup(bellNode));
        bellNode.setStyle((bellNode.getStyle() == null ? "" : bellNode.getStyle()) + "; -fx-cursor: hand;");

        // Lần đầu attach: fetch ngay
        fetchNow();
        updateBadge();
    }

    private static void wireClientOnce() {
        if (wiredOnClient) return;
        AuctionClient client = AppState.getInstance().getClient();
        if (client == null) return;

        client.addNotificationBundleListener(bundle -> {
            CACHE.clear();
            for (Notification n : bundle.items) {
                if (n != null) {
                    CACHE.add(n);
                }
            }
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
        var user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        try {
            AppState.getInstance().getClient().send("FETCH_NOTIFICATIONS:" + user.getUserId());
        } catch (Exception ignore) {}
    }

    private static void updateBadge() {
        if (badgeRef == null) return;
        if (unreadCount <= 0) {
            badgeRef.setVisible(false);
            badgeRef.setManaged(false);
        } else {
            badgeRef.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            badgeRef.setVisible(true);
            badgeRef.setManaged(true);
        }
    }

    private static void togglePopup(Node anchor) {
        if (currentPopup != null && currentPopup.isShowing()) {
            currentPopup.hide();
            currentPopup = null;
            popupController = null;
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(NotificationCenter.class.getResource("/view/notification_popup.fxml"));
            javafx.scene.Parent root = loader.load();
            popupController = loader.getController();
            popupController.setData(new ArrayList<>(CACHE));
            popupController.setOnMarkAll(NotificationCenter::markAllRead);
            popupController.setOnMarkOne(NotificationCenter::markOneRead);

            Popup popup = new Popup();
            popup.getContent().add(root);
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);

            // Đặt popup phía trên-bên phải của icon (vì bell thường nằm dưới cùng sidebar)
            javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
            double x = b.getMinX() + anchor.getBoundsInLocal().getWidth() + 8;
            double y = b.getMinY() - 650; // hiện lên trên do icon nằm dưới
            if (y < 20) y = 20;
            popup.show(anchor, x, y);
            currentPopup = popup;
        } catch (Exception ex) {
            System.err.println("Lỗi mở notification popup: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void markOneRead(Notification n) {
        n.setRead(true);
        unreadCount = Math.max(0, unreadCount - 1);
        updateBadge();
        if (popupController != null) popupController.setData(new ArrayList<>(CACHE));
        try {
            AppState.getInstance().getClient().send("MARK_NOTIFICATION_READ:" + n.getNotificationId());
        } catch (Exception ignore) {}
    }

    private static void markAllRead() {
        for (Notification n : CACHE) {
            n.setRead(true);
        }
        unreadCount = 0;
        updateBadge();
        if (popupController != null) popupController.setData(new ArrayList<>(CACHE));
        try {
            var user = AppState.getInstance().getCurrentUser();
            if (user != null) AppState.getInstance().getClient().send("MARK_ALL_NOTIFICATIONS_READ:" + user.getUserId());
        } catch (Exception ignore) {}
    }

    /** Gọi khi logout để reset state — tránh leak data sang user khác trên cùng app instance. */
    public static void reset() {
        CACHE.clear();
        unreadCount = 0;
        badgeRef = null;
        if (currentPopup != null) {
            currentPopup.hide();
            currentPopup = null;
        }
        popupController = null;
    }
}
