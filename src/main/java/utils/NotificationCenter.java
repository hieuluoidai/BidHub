package utils;

import controller.NotificationWindowController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.manager.AppState;
import model.notification.Notification;
import model.user.User;
import network.AuctionClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side helper: quản lý badge unread count + cửa sổ hiển thị danh sách thông báo.
 */
public class NotificationCenter {

    private static final List<Notification> CACHE = new ArrayList<>();
    private static int unreadCount = 0;
    private static Label badgeRef;
    private static Stage currentStage;
    private static NotificationWindowController windowController;
    private static AuctionClient lastClient;
    private static Consumer<Notification> onAction;

    public static void attach(Node bellNode, Label badge) {
        attach(bellNode, badge, null);
    }

    public static void attach(Node bellNode, Label badge, Consumer<Notification> onClick) {
        badgeRef = badge;
        onAction = onClick;
        wireClientOnce();

        bellNode.setOnMouseClicked(e -> {
            e.consume(); // Ngăn sự kiện nổi lên UserProfile card
            toggleWindow(bellNode);
        });
        bellNode.setStyle((bellNode.getStyle() == null ? "" : bellNode.getStyle()) + "; -fx-cursor: hand;");

        fetchNow();
        updateBadge();
    }

    private static void wireClientOnce() {
        AuctionClient client = AppState.getInstance().getClient();
        if (client == null || client == lastClient) return;

        System.out.println(">>> [DEBUG] NotificationCenter: Wiring new client " + client);
        lastClient = client;
        client.addNotificationBundleListener(bundle -> {
            System.out.println(">>> [DEBUG] NotificationCenter: Received Bundle, items count: " 
                + (bundle.items != null ? bundle.items.size() : 0));
            synchronized (CACHE) {
                CACHE.clear();
                if (bundle.items != null) {
                    for (Notification n : bundle.items) {
                        if (n != null) {
                            System.out.println(">>> [DEBUG] - Notification: " + n.getType() + " | " + n.getTitle());
                            CACHE.add(n);
                        }
                    }
                }
                unreadCount = (int) CACHE.stream().filter(n -> !n.isRead()).count();
            }
            Platform.runLater(() -> {
                updateBadge();
                if (windowController != null) {
                    synchronized (CACHE) {
                        windowController.setData(new ArrayList<>(CACHE));
                    }
                }
            });
        });

        client.addNotificationRefreshListener(() -> {
            System.out.println(">>> [DEBUG] NotificationCenter: Received RefreshSignal, fetching...");
            Platform.runLater(NotificationCenter::fetchNow);
        });
    }

    public static void fetchNow() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;
        AuctionClient client = AppState.getInstance().getClient();
        if (client == null) return;
        try {
            client.send("FETCH_NOTIFICATIONS:" + user.getUserId());
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

    private static void toggleWindow(Node anchor) {
        if (currentStage != null && currentStage.isShowing()) {
            currentStage.toFront();
            fetchNow(); // Refresh data khi click lại
            return;
        }
        
        fetchNow();
        try {
            FXMLLoader loader = new FXMLLoader(NotificationCenter.class.getResource("/view/notification_window.fxml"));
            javafx.scene.Parent root = loader.load();
            windowController = loader.getController();
            
            synchronized (CACHE) {
                windowController.setData(new ArrayList<>(CACHE));
            }
            
            windowController.setOnMarkAll(NotificationCenter::markAllRead);
            windowController.setOnMarkOne(NotificationCenter::markOneRead);
            windowController.setOnAction(n -> {
                if (onAction != null) onAction.accept(n);
            });

            Stage stage = new Stage();
            stage.initOwner(anchor.getScene().getWindow());
            stage.initModality(Modality.NONE); // Không dùng Modal để có thể click ra ngoài
            stage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setTitle("Thông báo");
            
            // Tự động đóng khi mất focus (click ra ngoài)
            stage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    stage.close();
                }
            });
            
            // Cho phép kéo cửa sổ
            final double[] xOffset = new double[1];
            final double[] yOffset = new double[1];
            root.setOnMousePressed(event -> {
                xOffset[0] = event.getSceneX();
                yOffset[0] = event.getSceneY();
            });
            root.setOnMouseDragged(event -> {
                stage.setX(event.getScreenX() - xOffset[0]);
                stage.setY(event.getScreenY() - yOffset[0]);
            });

            stage.setOnHidden(e -> {
                windowController = null;
                currentStage = null;
            });

            stage.show();
            stage.centerOnScreen();
            currentStage = stage;

            // Hoạt ảnh xuất hiện (Fade-in & Slide-down)
            root.setOpacity(0);
            root.setTranslateY(-20);
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(300), root);
            ft.setFromValue(0); 
            ft.setToValue(1);
            
            javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(
                    javafx.util.Duration.millis(300), root);
            tt.setFromY(-20); 
            tt.setToY(0);
            
            new javafx.animation.ParallelTransition(ft, tt).play();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void markOneRead(Notification n) {
        synchronized (CACHE) {
            n.setRead(true);
            unreadCount = (int) CACHE.stream().filter(notif -> !notif.isRead()).count();
        }
        updateBadge();
        if (windowController != null) {
            synchronized (CACHE) {
                windowController.setData(new ArrayList<>(CACHE));
            }
        }
        try {
            AppState.getInstance().getClient().send("MARK_NOTIFICATION_READ:" + n.getNotificationId());
        } catch (Exception ignore) {}
    }

    private static void markAllRead() {
        synchronized (CACHE) {
            for (Notification n : CACHE) {
                n.setRead(true);
            }
            unreadCount = 0;
        }
        updateBadge();
        if (windowController != null) {
            synchronized (CACHE) {
                windowController.setData(new ArrayList<>(CACHE));
            }
        }
        try {
            User user = AppState.getInstance().getCurrentUser();
            if (user != null) AppState.getInstance().getClient().send("MARK_ALL_NOTIFICATIONS_READ:" + user.getUserId());
        } catch (Exception ignore) {}
    }

    public static void reset() {
        synchronized (CACHE) {
            CACHE.clear(); unreadCount = 0;
        }
        badgeRef = null;
        if (currentStage != null) currentStage.close();
        currentStage = null; windowController = null;
        lastClient = null;
    }
}
