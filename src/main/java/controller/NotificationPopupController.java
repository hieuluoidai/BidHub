package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.notification.Notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class NotificationPopupController {

    @FXML private ListView<Notification> listNotifications;
    @FXML private Label lblUnreadInfo;
    @FXML private Button btnMarkAll;

    private final ObservableList<Notification> items = FXCollections.observableArrayList();
    private Runnable onMarkAll;
    private java.util.function.Consumer<Notification> onMarkOne;
    private java.util.function.Consumer<Notification> onAction;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @FXML
    public void initialize() {
        listNotifications.setItems(items);
        listNotifications.setPlaceholder(new Label("Bạn chưa có thông báo nào."));
        listNotifications.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Notification n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setGraphic(buildRow(n));
                    setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                }
            }
        });
    }

    public void setData(List<Notification> data) {
        items.setAll(data);
        long unread = data.stream().filter(n -> !n.isRead()).count();
        lblUnreadInfo.setText(unread > 0 ? unread + " chưa đọc" : "Tất cả đã đọc");
        btnMarkAll.setDisable(unread == 0);
    }

    public void setOnMarkAll(Runnable r) {
        this.onMarkAll = r;
    }
    public void setOnMarkOne(java.util.function.Consumer<Notification> c) {
        this.onMarkOne = c;
    }
    public void setOnAction(java.util.function.Consumer<Notification> c) {
        this.onAction = c;
    }

    @FXML
    void handleMarkAllRead() {
        if (onMarkAll != null) onMarkAll.run();
    }

    private VBox buildRow(Notification n) {
        VBox row = new VBox(4);
        row.getStyleClass().add("notification-row");
        if (!n.isRead()) row.getStyleClass().add("notification-row-unread");

        HBox top = new HBox(8);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label icon = new Label(iconFor(n.getType()));
        icon.getStyleClass().add("notification-icon");
        icon.setStyle("-fx-background-color: " + colorFor(n.getType()) + ";");

        Label title = new Label(n.getTitle() == null ? "" : n.getTitle());
        title.getStyleClass().add("notification-row-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label time = new Label(formatRelative(n.getCreatedAt()));
        time.getStyleClass().add("notification-row-time");

        top.getChildren().addAll(icon, title, spacer, time);

        Label msg = new Label(n.getMessage() == null ? "" : n.getMessage());
        msg.setWrapText(true);
        msg.getStyleClass().add("notification-row-msg");

        row.getChildren().addAll(top, msg);

        // Click → mark read + trigger action
        row.setOnMouseClicked(e -> {
            if (onAction != null) onAction.accept(n);
            if (!n.isRead() && onMarkOne != null) {
                onMarkOne.accept(n);
            }
        });
        row.setStyle(row.getStyle() + "; -fx-cursor: hand;");
        return row;
    }

    private String formatRelative(LocalDateTime when) {
        if (when == null) return "";
        LocalDateTime now = LocalDateTime.now();
        long sec = ChronoUnit.SECONDS.between(when, now);
        if (sec < 60)        return "vừa xong";
        long min = sec / 60;
        if (min < 60)        return min + " phút trước";
        long hour = min / 60;
        if (hour < 24)       return hour + " giờ trước";
        long day = hour / 24;
        if (day < 7)         return day + " ngày trước";
        return when.format(TIME_FMT);
    }

    private String iconFor(Notification.Type t) {
        if (t == null) return "🔔";
        return switch (t) {
            case WALLET_TOPUP     -> "💰";
            case WALLET_PAYMENT   -> "🛒";
            case WALLET_EARNING   -> "📈";
            case WALLET_REFUND    -> "🔄";
            case ITEM_POSTED      -> "📦";
            case AUCTION_NEW_BID  -> "🏷️";
            case AUCTION_OUTBID   -> "⚠️";
            case AUCTION_WON      -> "🏆";
            case AUCTION_ENDED_SOLD    -> "✅";
            case AUCTION_ENDED_NO_BID  -> "🚫";
            case AUCTION_EXTENDED      -> "⏱️";
            case AUCTION_CANCELED      -> "❌";
            case SELLER_APPROVED  -> "🎉";
            case SELLER_REVOKED   -> "⛔";
            case ADMIN_NEW_USER           -> "👤";
            case ADMIN_NEW_SELLER_REQUEST -> "📨";
            case ADMIN_NEW_AUCTION        -> "🆕";
            case ADMIN_DEPOSIT_REQUEST    -> "💳";
            case DEPOSIT_REJECTED         -> "❌";
        };
    }

    private String colorFor(Notification.Type t) {
        if (t == null) return "#94A3B8";
        return switch (t) {
            case WALLET_TOPUP, WALLET_EARNING, SELLER_APPROVED, AUCTION_WON, AUCTION_ENDED_SOLD -> "#10B981";
            case WALLET_PAYMENT, AUCTION_OUTBID, SELLER_REVOKED, AUCTION_CANCELED -> "#EF4444";
            case WALLET_REFUND, AUCTION_EXTENDED -> "#F59E0B";
            case ITEM_POSTED, AUCTION_NEW_BID -> "#3B82F6";
            case AUCTION_ENDED_NO_BID -> "#64748B";
            case DEPOSIT_REJECTED -> "#EF4444";
            case ADMIN_NEW_USER, ADMIN_NEW_SELLER_REQUEST, ADMIN_NEW_AUCTION,
                 ADMIN_DEPOSIT_REQUEST -> "#8B5CF6";
        };
    }
}
