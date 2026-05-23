package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import model.chat.ChatMessage;
import model.manager.AppState;
import model.user.User;
import utils.ChatCenter;
import utils.ImageStorageService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MessagesController {

    @FXML private Button btnTabMessages;
    @FXML private Button btnTabFriends;
    @FXML private ListView<ChatMessage.Summary> listConversations;
    @FXML private VBox friendsPane;
    @FXML private FriendsController friendsPaneController;

    @FXML private VBox paneEmptyChat;
    @FXML private VBox paneConversation;
    @FXML private Label lblPartnerAvatar;
    @FXML private ImageView imgPartnerAvatar;
    @FXML private Label lblPartnerName;
    @FXML private Label lblPartnerStatus;
    @FXML private ScrollPane scrollMessages;
    @FXML private VBox messagesContainer;
    @FXML private Button btnEmoji;
    @FXML private TextField txtInput;
    @FXML private Button btnSend;

    private static final String[] EMOJIS = {
        "😀","😁","😂","😆","😍","😘","🤗","😎","😲","😜",
        "🤔","😴","😭","😢","😡","😞","😵","😱","😇","🙄",
        "👍","👎","👏","🙏","💪","🔥","❤","💯","🎉","✨"
    };

    private final ObservableList<ChatMessage.Summary> summaries = FXCollections.observableArrayList();
    private final Map<String, ChatMessage> messageMap = new HashMap<>();
    private String currentPartnerId;
    private String currentPartnerName;
    private String currentPartnerAvatarPath;
    private Popup emojiPopup;

    private final Consumer<ChatMessage> messageListener = this::onIncomingMessage;
    private final Consumer<ChatMessage.Bundle> bundleListener = this::onBundle;
    private final Consumer<ChatMessage.SummaryBundle> summaryListener = this::onSummaries;
    private final Consumer<String> readReceiptListener = this::onReadReceipt;
    private final Consumer<String> recalledSelfListener = this::onRecalledSelf;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    @FXML
    public void initialize() {
        // Default active tab: Tin nhắn
        btnTabMessages.getStyleClass().add("chat-tab-active");

        listConversations.setItems(summaries);
        listConversations.setPlaceholder(new Label("Chưa có cuộc trò chuyện nào."));
        listConversations.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatMessage.Summary s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null); setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setGraphic(buildConversationRow(s));
                    setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                }
            }
        });

        listConversations.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) openConversation(newV.partnerId, newV.partnerUsername, newV.partnerAvatarPath);
        });

        ChatCenter.init();
        ChatCenter.addMessageListener(messageListener);
        ChatCenter.addBundleListener(bundleListener);
        ChatCenter.addSummaryListener(summaryListener);
        ChatCenter.addReadReceiptListener(readReceiptListener);
        ChatCenter.addRecalledSelfListener(recalledSelfListener);

        refreshSummaries();

        // Friend badge — update button text when pending count changes
        AppState.getInstance().pendingFriendCountProperty().addListener((obs, oldV, newV) ->
                Platform.runLater(() -> updateFriendBadge(newV.intValue())));
        updateFriendBadge(AppState.getInstance().getPendingFriendCount());

        // Wire "Nhắn tin" callback from friend rows → open chat here
        if (friendsPaneController != null) {
            friendsPaneController.setOpenChatCallback(args -> Platform.runLater(() -> {
                switchToMessagesTab();
                openConversation(args[0], args[1], args[2]);
            }));
        }
    }

    public void detach() {
        ChatCenter.removeMessageListener(messageListener);
        ChatCenter.removeBundleListener(bundleListener);
        ChatCenter.removeSummaryListener(summaryListener);
        ChatCenter.removeReadReceiptListener(readReceiptListener);
        ChatCenter.removeRecalledSelfListener(recalledSelfListener);
        if (friendsPaneController != null) friendsPaneController.detach();
    }

    public void refreshSummaries() {
        ChatCenter.fetchSummariesForBadge();
    }

    /** Mở cuộc trò chuyện với 1 user — gọi từ ngoài (vd: từ item_details, từ FriendsController). */
    public void openChatWith(String partnerId, String partnerUsername, String partnerAvatarPath) {
        switchToMessagesTab();
        openConversation(partnerId, partnerUsername, partnerAvatarPath);
    }

    private void openConversation(String partnerId, String partnerName, String avatarPath) {
        this.currentPartnerId = partnerId;
        this.currentPartnerName = partnerName;
        this.currentPartnerAvatarPath = avatarPath;
        messageMap.clear();
        messagesContainer.getChildren().clear();
        paneEmptyChat.setVisible(false); paneEmptyChat.setManaged(false);
        paneConversation.setVisible(true); paneConversation.setManaged(true);

        lblPartnerName.setText(partnerName);
        String firstChar = (partnerName == null || partnerName.isEmpty()) ? "?"
                : partnerName.substring(0, 1).toUpperCase();
        lblPartnerAvatar.setText(firstChar);
        if (avatarPath != null && !avatarPath.isEmpty()) {
            String uri = ImageStorageService.toImageUrl(avatarPath);
            if (uri != null) {
                imgPartnerAvatar.setImage(new Image(uri));
                imgPartnerAvatar.setVisible(true);
                lblPartnerAvatar.setVisible(false);
                imgPartnerAvatar.setClip(new javafx.scene.shape.Circle(21, 21, 21));
            } else {
                imgPartnerAvatar.setVisible(false); lblPartnerAvatar.setVisible(true);
            }
        } else {
            imgPartnerAvatar.setVisible(false); lblPartnerAvatar.setVisible(true);
        }

        User cu = AppState.getInstance().getCurrentUser();
        if (cu == null) return;
        AppState.getInstance().getClient().send("CHAT_FETCH:" + cu.getUserId() + ":" + partnerId);
        AppState.getInstance().getClient().send("CHAT_MARK_READ:" + cu.getUserId() + ":" + partnerId);
        txtInput.requestFocus();
    }

    private void onBundle(ChatMessage.Bundle b) {
        if (currentPartnerId == null || !currentPartnerId.equals(b.partnerId)) return;
        messageMap.clear();
        for (ChatMessage m : b.items) {
            messageMap.put(m.getMessageId(), m);
        }
        rebuildAllBubbles();
        scrollToBottom();
    }

    private void onIncomingMessage(ChatMessage m) {
        User cu = AppState.getInstance().getCurrentUser();
        if (cu == null) return;
        String myId = cu.getUserId();
        boolean inOpen = currentPartnerId != null
                && ((m.getSenderId().equals(currentPartnerId) && m.getReceiverId().equals(myId))
                 || (m.getReceiverId().equals(currentPartnerId) && m.getSenderId().equals(myId)));

        if (inOpen) {
            boolean isNew = !messageMap.containsKey(m.getMessageId());
            messageMap.put(m.getMessageId(), m);
            rebuildAllBubbles();
            if (isNew) {
                scrollToBottom();
                if (m.getSenderId().equals(currentPartnerId)) {
                    AppState.getInstance().getClient()
                            .send("CHAT_MARK_READ:" + myId + ":" + currentPartnerId);
                }
            }
        }
        refreshSummaries();
    }

    private void onSummaries(ChatMessage.SummaryBundle sb) {
        summaries.setAll(sb.items);
        updateUnreadPill(sb.totalUnread);
    }

    private void onReadReceipt(String msg) {
        // "CHAT_READ:<readerId>:<id1,id2,...>"
        String[] parts = msg.split(":", 3);
        if (parts.length < 3) return;
        String readerId = parts[1];
        if (currentPartnerId == null || !currentPartnerId.equals(readerId)) return;
        for (String id : parts[2].split(",")) {
            ChatMessage existing = messageMap.get(id);
            if (existing != null && existing.getReadAt() == null) {
                existing.setReadAt(LocalDateTime.now());
            }
        }
        rebuildAllBubbles();
    }

    private void onRecalledSelf(String msg) {
        // "CHAT_RECALLED_SELF:<messageId>"
        String[] parts = msg.split(":", 2);
        if (parts.length < 2) return;
        String messageId = parts[1];
        messageMap.remove(messageId);
        rebuildAllBubbles();
        refreshSummaries();
    }

    private void rebuildAllBubbles() {
        User cu = AppState.getInstance().getCurrentUser();
        String myId = cu != null ? cu.getUserId() : null;

        java.util.List<ChatMessage> sorted = messageMap.values().stream()
                .sorted(java.util.Comparator.comparing(
                        ChatMessage::getSentAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();

        // Only the last message I sent shows "Đã gửi" / "Đã xem"
        String lastMineId = null;
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (myId != null && myId.equals(sorted.get(i).getSenderId())) {
                lastMineId = sorted.get(i).getMessageId();
                break;
            }
        }

        messagesContainer.getChildren().clear();
        for (ChatMessage m : sorted) {
            messagesContainer.getChildren().add(buildBubble(m, m.getMessageId().equals(lastMineId)));
        }
    }

    private void updateUnreadPill(int total) {
        btnTabMessages.setText(total > 0
                ? "Messages (" + (total > 99 ? "99+" : String.valueOf(total)) + ")"
                : "Messages");
    }

    private void updateFriendBadge(int n) {
        btnTabFriends.setText(n > 0
                ? "Bạn bè (" + (n > 99 ? "99+" : String.valueOf(n)) + ")"
                : "Bạn bè");
    }

    @FXML
    void handleTabMessages() {
        switchToMessagesTab();
    }

    @FXML
    void handleTabFriends() {
        switchToFriendsTab();
    }

    private void switchToMessagesTab() {
        listConversations.setVisible(true);
        listConversations.setManaged(true);
        friendsPane.setVisible(false);
        friendsPane.setManaged(false);
        btnTabMessages.getStyleClass().add("chat-tab-active");
        btnTabFriends.getStyleClass().remove("chat-tab-active");
    }

    private void switchToFriendsTab() {
        listConversations.setVisible(false);
        listConversations.setManaged(false);
        friendsPane.setVisible(true);
        friendsPane.setManaged(true);
        btnTabMessages.getStyleClass().remove("chat-tab-active");
        btnTabFriends.getStyleClass().add("chat-tab-active");
    }

    private Node buildConversationRow(ChatMessage.Summary s) {
        HBox row = new HBox(12);
        row.getStyleClass().add("chat-conv-row");
        row.setPadding(new javafx.geometry.Insets(10, 16, 10, 16));

        StackPane avatarPane = new StackPane();
        avatarPane.setMinSize(40, 40); avatarPane.setMaxSize(40, 40);
        String color = "#3B82F6";
        Label initial = new Label(s.partnerUsername == null || s.partnerUsername.isEmpty()
                ? "?" : s.partnerUsername.substring(0, 1).toUpperCase());
        initial.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;");
        avatarPane.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 50%;");
        avatarPane.getChildren().add(initial);
        if (s.partnerAvatarPath != null && !s.partnerAvatarPath.isEmpty()) {
            String uri = ImageStorageService.toImageUrl(s.partnerAvatarPath);
            if (uri != null) {
                ImageView iv = new ImageView(new Image(uri, 40, 40, true, true));
                iv.setFitWidth(40); iv.setFitHeight(40);
                iv.setClip(new javafx.scene.shape.Circle(20, 20, 20));
                avatarPane.getChildren().add(iv);
            }
        }

        VBox info = new VBox(2);
        info.setMaxWidth(180);
        Label name = new Label(s.partnerUsername == null ? s.partnerId : s.partnerUsername);
        name.getStyleClass().add("chat-conv-name");
        String previewText = (s.lastFromMe ? "Bạn: " : "") + (s.lastMessage == null ? "" : s.lastMessage);
        Label preview = new Label(previewText);
        preview.getStyleClass().add("chat-conv-preview");
        preview.setMaxWidth(180);
        preview.setEllipsisString("...");
        info.getChildren().addAll(name, preview);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        VBox right = new VBox(4);
        right.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        Label time = new Label(formatRelative(s.lastAt));
        time.getStyleClass().add("chat-conv-time");
        right.getChildren().add(time);
        if (s.unreadCount > 0) {
            Label badge = new Label(s.unreadCount > 99 ? "99+" : String.valueOf(s.unreadCount));
            badge.getStyleClass().add("chat-conv-unread");
            right.getChildren().add(badge);
        }

        row.getChildren().addAll(avatarPane, info, spacer, right);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private Node buildBubble(ChatMessage m, boolean showStatus) {
        User cu = AppState.getInstance().getCurrentUser();
        boolean isMine = cu != null && cu.getUserId().equals(m.getSenderId());

        // ── Trường hợp đã bị thu hồi với tất cả ──────────────────────────────
        if (m.isRecalled()) {
            Label recalled = new Label("Tin nhắn đã bị thu hồi");
            recalled.getStyleClass().add("chat-bubble-recalled");
            HBox row = new HBox();
            javafx.scene.layout.Region sp = new javafx.scene.layout.Region();
            HBox.setHgrow(sp, javafx.scene.layout.Priority.ALWAYS);
            return isMine ? new HBox(sp, recalled) : new HBox(recalled, sp);
        }

        // Bubble label — wraps at maxWidth, sized to content for short messages
        Label bubble = new Label(m.getContent());
        bubble.setWrapText(true);
        bubble.setMaxWidth(360);
        bubble.getStyleClass().add(isMine ? "chat-bubble-mine" : "chat-bubble-theirs");

        // StackPane allows heart overlay without stretching the bubble
        StackPane bubblePane = new StackPane(bubble);
        bubblePane.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        if (m.isLiked()) {
            Label heart = new Label("❤");
            heart.getStyleClass().add("chat-bubble-heart");
            StackPane.setAlignment(heart,
                    isMine ? javafx.geometry.Pos.BOTTOM_LEFT : javafx.geometry.Pos.BOTTOM_RIGHT);
            heart.setTranslateY(8);
            heart.setTranslateX(isMine ? -8 : 8);
            bubblePane.getChildren().add(heart);
        }
        bubblePane.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !isMine) {
                toggleLike(m);
            }
        });
        bubblePane.setStyle("-fx-cursor: hand;");

        // ── Context menu (chuột phải) — custom popup ─────────────────────────
        bubblePane.setOnContextMenuRequested(e -> {
            showBubbleMenu(m, isMine, bubblePane, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        HBox metaRow = new HBox(5);
        metaRow.setAlignment(isMine ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
        Label time = new Label(m.getSentAt() == null ? "" : formatTime(m.getSentAt()));
        time.getStyleClass().add("chat-bubble-time");
        metaRow.getChildren().add(time);
        if (isMine && showStatus) {
            Label status = new Label(m.getReadAt() != null ? "Đã xem" : "Đã gửi");
            status.getStyleClass().add(m.getReadAt() != null ? "chat-status-read" : "chat-status-sent");
            metaRow.getChildren().add(status);
        }

        // col stays as narrow as its widest child (setFillWidth=false)
        VBox col = new VBox(3);
        col.setFillWidth(false);
        col.setAlignment(isMine ? javafx.geometry.Pos.TOP_RIGHT : javafx.geometry.Pos.TOP_LEFT);
        col.getChildren().addAll(bubblePane, metaRow);

        // Full-width row pushes col to the correct side via spacer
        HBox row = new HBox();
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        if (isMine) {
            row.getChildren().addAll(spacer, col);
        } else {
            row.getChildren().addAll(col, spacer);
        }
        return row;
    }

    private void toggleLike(ChatMessage m) {
        boolean newLiked = !m.isLiked();
        AppState.getInstance().getClient()
                .send("CHAT_LIKE:" + m.getMessageId() + ":" + (newLiked ? "1" : "0"));
    }

    /** Gửi lệnh thu hồi tin nhắn lên server. mode = "ALL" hoặc "SELF". */
    private void recallMessage(String messageId, String mode) {
        AppState.getInstance().getClient()
                .send("CHAT_RECALL:" + messageId + ":" + mode);
    }

    /**
     * Hiển thị popup thu hồi tin nhắn với giao diện tùy chỉnh.
     * Dùng Popup thay vì ContextMenu để kiểm soát hoàn toàn CSS.
     */
    private void showBubbleMenu(ChatMessage m, boolean isMine,
                                javafx.scene.Node anchor, double sx, double sy) {
        if (anchor.getScene() == null) return;
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        VBox box = new VBox(0);
        box.getStyleClass().add("recall-popup-box");

        Button btnSelf = makeRecallBtn("✕  Thu hồi với tôi", false);
        btnSelf.setOnAction(e -> {
            recallMessage(m.getMessageId(), "SELF");
            popup.hide();
        });
        box.getChildren().add(btnSelf);

        if (isMine) {
            Separator sep = new Separator();
            sep.getStyleClass().add("recall-popup-sep");
            Button btnAll = makeRecallBtn("↺  Thu hồi với tất cả", true);
            btnAll.setOnAction(e -> {
                recallMessage(m.getMessageId(), "ALL");
                popup.hide();
            });
            box.getChildren().addAll(sep, btnAll);
        }

        popup.getContent().add(box);

        // Nạp stylesheet vào scene nội bộ của Popup sau khi hiển thị
        String cssUrl = getClass().getResource("/view/style.css").toExternalForm();
        popup.setOnShown(ev -> {
            Scene sc = popup.getScene();
            if (sc != null && !sc.getStylesheets().contains(cssUrl)) {
                sc.getStylesheets().add(cssUrl);
            }
        });

        popup.show(anchor.getScene().getWindow(), sx, sy);
    }

    private Button makeRecallBtn(String text, boolean danger) {
        Button btn = new Button(text);
        btn.getStyleClass().add("recall-popup-item");
        if (danger) {
            btn.getStyleClass().add("recall-popup-danger");
        }
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    @FXML
    void handleSend() {
        if (currentPartnerId == null) return;
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;
        User cu = AppState.getInstance().getCurrentUser();
        if (cu == null) return;
        AppState.getInstance().getClient().send("CHAT_SEND:" + cu.getUserId() + ":" + currentPartnerId + ":" + text);
        txtInput.clear();
        txtInput.requestFocus();
    }

    @FXML
    void handleToggleEmoji() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            emojiPopup.hide(); return;
        }
        emojiPopup = new Popup();
        emojiPopup.setAutoHide(true);
        FlowPane grid = new FlowPane();
        grid.setHgap(4); grid.setVgap(4);
        grid.setPadding(new javafx.geometry.Insets(10));
        grid.setPrefWrapLength(280);
        grid.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #E5E7EB;"
                + " -fx-border-radius: 12; -fx-border-width: 1;"
                + " -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 12, 0, 0, 4);");
        for (String e : EMOJIS) {
            Button btn = new Button(e);
            btn.setStyle("-fx-background-color: transparent; -fx-font-size: 20px;"
                    + " -fx-cursor: hand; -fx-min-width: 36px; -fx-min-height: 36px;");
            btn.setOnAction(ev -> {
                txtInput.setText(txtInput.getText() + e);
                txtInput.positionCaret(txtInput.getText().length());
                emojiPopup.hide();
                txtInput.requestFocus();
            });
            grid.getChildren().add(btn);
        }
        emojiPopup.getContent().add(grid);
        javafx.geometry.Bounds b = btnEmoji.localToScreen(btnEmoji.getBoundsInLocal());
        emojiPopup.show(btnEmoji, b.getMinX(), b.getMinY() - 320);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            scrollMessages.applyCss();
            scrollMessages.layout();
            scrollMessages.setVvalue(1.0);
        });
    }

    private String formatRelative(LocalDateTime when) {
        if (when == null) return "";
        LocalDateTime now = LocalDateTime.now();
        long sec = ChronoUnit.SECONDS.between(when, now);
        if (sec < 60) return "vừa xong";
        long min = sec / 60;
        if (min < 60) return min + "p";
        long hour = min / 60;
        if (hour < 24) return hour + "h";
        long day = hour / 24;
        if (day < 7) return day + "d";
        return when.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private String formatTime(LocalDateTime when) {
        return when.format(TIME_FMT);
    }
}
