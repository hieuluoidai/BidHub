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
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
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

        Node iconNode = createIcon(n.getType());

        Label title = new Label(n.getTitle() == null ? "" : n.getTitle());
        title.getStyleClass().add("notification-row-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label time = new Label(formatRelative(n.getCreatedAt()));
        time.getStyleClass().add("notification-row-time");

        top.getChildren().addAll(iconNode, title, spacer, time);

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

    private Node createIcon(Notification.Type t) {
        String pathData = switch (t) {
            case WALLET_TOPUP, WALLET_EARNING ->
                "M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21"
                + "c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13"
                + " 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2"
                + "c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z";
            case WALLET_PAYMENT -> "M7 10l5 5 5-5z";
            case WALLET_REFUND ->
                "M12 6v3l4-4-4-4v3c-4.42 0-8 3.58-8 8 0 1.57.46 3.03 1.24 4.26l1.46-1.46C6.27 13.11 6 12.58 6 12"
                + "c0-3.31 2.69-6 6-6z";
            case ITEM_POSTED -> "M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1z";
            case AUCTION_NEW_BID ->
                "M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9"
                + "c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42z";
            case AUCTION_OUTBID -> "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z";
            case AUCTION_WON ->
                "M18 2H6v2H1v7c0 3.31 2.69 6 6 6 1.49 0 2.86-.54 3.93-1.44C11.66 16.89 13.68 18 16 18h2v2h-4v2h8v-2h-4"
                + "v-2h2c3.31 0 6-2.69 6-6V4c0-1.1-.9-2-2-2z";
            case AUCTION_ENDED_SOLD -> "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
            case AUCTION_ENDED_NO_BID, AUCTION_CANCELED, DEPOSIT_REJECTED ->
                "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
            case AUCTION_EXTENDED ->
                "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2z";
            case SELLER_APPROVED ->
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41"
                + "L10 14.17l7.59-7.59L19 8l-9 9z";
            case SELLER_REVOKED -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z";
            case ADMIN_NEW_USER ->
                "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2"
                + "c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
            case CHAT_NEW_MESSAGE -> "M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z";
            case FRIEND_REQUEST ->
                "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0"
                + "c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm0 2"
                + "c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5z";
            case FRIEND_ACCEPTED ->
                "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2"
                + "c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4zm-8.2-1.5l-3.3-3.3 1.4-1.4 1.9 1.9 4.3-4.3 1.4 1.4-5.7 5.7z";
            default ->
                "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4"
                + "c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z";
        };

        SVGPath svg = new SVGPath();
        svg.setContent(pathData);
        svg.setFill(javafx.scene.paint.Color.WHITE);
        svg.setScaleX(0.7);
        svg.setScaleY(0.7);

        StackPane pane = new StackPane(svg);
        pane.getStyleClass().add("notification-icon");
        pane.setStyle("-fx-background-color: " + colorFor(t) + ";");
        return pane;
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
            case CHAT_NEW_MESSAGE -> "#0EA5E9";
            case CHAT_LIKED       -> "#EC4899";
            case FRIEND_REQUEST, FRIEND_ACCEPTED -> "#6366F1";
        };
    }
}
