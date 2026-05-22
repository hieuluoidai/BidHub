package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.friendship.Friendship;
import utils.FriendCenter;
import utils.ImageStorageService;
import java.util.function.Consumer;

public class FriendsController {

    @FXML private TextField txtSearch;
    @FXML private Button btnSearch;
    @FXML private VBox listContainer;

    private final Consumer<Friendship.Bundle> bundleListener = this::onBundle;
    private final Consumer<Friendship.SearchBundle> searchListener = this::onSearch;

    // Callback khi nhấn "Nhắn tin" → DashboardController mở chat
    private Consumer<String[]> openChatCallback;

    @FXML
    public void initialize() {
        FriendCenter.init();
        FriendCenter.addBundleListener(bundleListener);
        FriendCenter.addSearchListener(searchListener);
        FriendCenter.fetchBundle();
    }

    public void detach() {
        FriendCenter.removeBundleListener(bundleListener);
        FriendCenter.removeSearchListener(searchListener);
    }

    public void setOpenChatCallback(Consumer<String[]> cb) {
        this.openChatCallback = cb;
    }

    @FXML
    void handleSearch() {
        String q = txtSearch.getText().trim();
        if (q.isEmpty()) {
            FriendCenter.fetchBundle();
        } else {
            FriendCenter.search(q);
        }
    }

    // ── Callbacks từ FriendCenter ──────────────────────────────────────────

    private void onBundle(Friendship.Bundle bundle) {
        listContainer.getChildren().clear();
        txtSearch.clear();

        if (!bundle.pending.isEmpty()) {
            listContainer.getChildren().add(sectionHeader("Lời mời kết bạn (" + bundle.pending.size() + ")"));
            for (Friendship f : bundle.pending) {
                listContainer.getChildren().add(buildPendingRow(f));
            }
        }

        listContainer.getChildren().add(sectionHeader("Bạn bè (" + bundle.friends.size() + ")"));
        if (bundle.friends.isEmpty()) {
            Label empty = new Label("Chưa có bạn bè nào.");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px; -fx-padding: 12 16 12 16;");
            listContainer.getChildren().add(empty);
        } else {
            for (Friendship f : bundle.friends) {
                listContainer.getChildren().add(buildFriendRow(f));
            }
        }
    }

    private void onSearch(Friendship.SearchBundle sb) {
        listContainer.getChildren().clear();
        listContainer.getChildren().add(sectionHeader("Kết quả tìm kiếm (" + sb.items.size() + ")"));
        if (sb.items.isEmpty()) {
            Label empty = new Label("Không tìm thấy người dùng nào.");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px; -fx-padding: 12 16 12 16;");
            listContainer.getChildren().add(empty);
        }
        for (Friendship.SearchResult r : sb.items) {
            listContainer.getChildren().add(buildSearchRow(r));
        }
    }

    // ── Build rows ─────────────────────────────────────────────────────────

    private HBox buildPendingRow(Friendship f) {
        HBox row = baseRow();
        row.getChildren().add(avatar(f.getPartnerAvatarPath(), f.getPartnerUsername()));

        VBox info = info(f.getPartnerUsername(), f.getPartnerRole());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button accept = new Button("Chấp nhận");
        accept.getStyleClass().add("friend-btn-accept");
        accept.setOnAction(e -> FriendCenter.accept(f.getRequesterId()));

        Button decline = new Button("Từ chối");
        decline.getStyleClass().add("friend-btn-decline");
        decline.setOnAction(e -> FriendCenter.decline(f.getRequesterId()));

        row.getChildren().addAll(info, spacer, accept, decline);
        return row;
    }

    private HBox buildFriendRow(Friendship f) {
        HBox row = baseRow();
        row.getChildren().add(avatar(f.getPartnerAvatarPath(), f.getPartnerUsername()));

        VBox info = info(f.getPartnerUsername(), f.getPartnerRole());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button chat = new Button("Nhắn tin");
        chat.getStyleClass().add("friend-btn-chat");
        chat.setOnAction(e -> {
            if (openChatCallback != null) {
                openChatCallback.accept(new String[]{
                        f.getPartnerId(), f.getPartnerUsername(), f.getPartnerAvatarPath()});
            }
        });

        row.getChildren().addAll(info, spacer, chat);
        return row;
    }

    private HBox buildSearchRow(Friendship.SearchResult r) {
        HBox row = baseRow();
        row.getChildren().add(avatar(r.avatarPath, r.username));

        VBox info = info(r.username, r.role);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        switch (r.friendStatus) {
            case "ACCEPTED" -> {
                Button chat = new Button("Nhắn tin");
                chat.getStyleClass().add("friend-btn-chat");
                chat.setOnAction(e -> {
                    if (openChatCallback != null)
                        openChatCallback.accept(new String[]{r.userId, r.username, r.avatarPath});
                });
                row.getChildren().addAll(info, spacer, chat);
            }
            case "PENDING_SENT" -> {
                Label lbl = new Label("Đã gửi lời mời");
                lbl.getStyleClass().add("friend-status-sent");
                row.getChildren().addAll(info, spacer, lbl);
            }
            case "PENDING_RECEIVED" -> {
                Button accept = new Button("Chấp nhận");
                accept.getStyleClass().add("friend-btn-accept");
                accept.setOnAction(e -> FriendCenter.accept(r.userId));
                row.getChildren().addAll(info, spacer, accept);
            }
            default -> {
                Button add = new Button("+ Kết bạn");
                add.getStyleClass().add("friend-btn-add");
                add.setOnAction(e -> {
                    FriendCenter.sendRequest(r.userId);
                    add.setText("Đã gửi");
                    add.setDisable(true);
                });
                row.getChildren().addAll(info, spacer, add);
            }
        }
        return row;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private HBox baseRow() {
        HBox row = new HBox(12);
        row.getStyleClass().add("friend-row");
        row.setPadding(new javafx.geometry.Insets(10, 16, 10, 16));
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private StackPane avatar(String avatarPath, String username) {
        StackPane pane = new StackPane();
        pane.setMinSize(40, 40); pane.setMaxSize(40, 40);
        String initial = (username == null || username.isEmpty()) ? "?" :
                username.substring(0, 1).toUpperCase();
        Label lbl = new Label(initial);
        lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;");
        pane.setStyle("-fx-background-color: #3B82F6; -fx-background-radius: 50%;");
        pane.getChildren().add(lbl);
        if (avatarPath != null && !avatarPath.isEmpty()) {
            String uri = ImageStorageService.toImageUrl(avatarPath);
            if (uri != null) {
                ImageView iv = new ImageView(new Image(uri, 40, 40, true, true));
                iv.setFitWidth(40); iv.setFitHeight(40);
                iv.setClip(new javafx.scene.shape.Circle(20, 20, 20));
                pane.getChildren().add(iv);
            }
        }
        return pane;
    }

    private VBox info(String username, String role) {
        VBox box = new VBox(2);
        Label name = new Label(username == null ? "?" : username);
        name.getStyleClass().add("friend-name");
        Label roleLbl = new Label(formatRole(role));
        roleLbl.getStyleClass().add("friend-role");
        box.getChildren().addAll(name, roleLbl);
        return box;
    }

    private Label sectionHeader(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("friend-section-header");
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private String formatRole(String role) {
        if (role == null) return "";
        return switch (role) {
            case "SELLER" -> "Người bán";
            case "ADMIN"  -> "Quản trị viên";
            default       -> "Người dùng";
        };
    }
}
