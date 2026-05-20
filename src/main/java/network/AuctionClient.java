package network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javafx.application.Platform;
import model.auction.Auction;
import model.auction.BidResult;

/**
 * Lớp điều phối liên lạc với Server và xử lý dữ liệu phía Client.
 */
public class AuctionClient {
    private final List<Consumer<BidResult>> bidResultListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> stringMessageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.notification.Notification.Bundle>> notificationBundleListeners =
            new CopyOnWriteArrayList<>();
    private final List<Runnable> notificationRefreshListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.chat.ChatMessage>> chatMessageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.chat.ChatMessage.Bundle>> chatBundleListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.chat.ChatMessage.SummaryBundle>> chatSummaryListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.friendship.Friendship.Bundle>> friendBundleListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.friendship.Friendship.SearchBundle>> friendSearchListeners = new CopyOnWriteArrayList<>();

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

    /**
     * Thêm listener nhận BidResult.
     */
    public void addBidResultListener(Consumer<BidResult> listener) {
        if (listener != null) bidResultListeners.add(listener);
    }

    public void removeBidResultListener(Consumer<BidResult> listener) {
        bidResultListeners.remove(listener);
    }

    /**
     * Thêm listener nhận String message từ server.
     */
    public void addStringMessageListener(Consumer<String> listener) {
        if (listener != null) stringMessageListeners.add(listener);
    }

    public void removeStringMessageListener(Consumer<String> listener) {
        stringMessageListeners.remove(listener);
    }

    public void addNotificationBundleListener(Consumer<model.notification.Notification.Bundle> l) {
        if (l != null) notificationBundleListeners.add(l);
    }

    public void addNotificationRefreshListener(Runnable l) {
        if (l != null) notificationRefreshListeners.add(l);
    }

    public void addChatMessageListener(Consumer<model.chat.ChatMessage> l) {
        if (l != null) chatMessageListeners.add(l);
    }

    public void removeChatMessageListener(Consumer<model.chat.ChatMessage> l) {
        chatMessageListeners.remove(l);
    }

    public void addChatBundleListener(Consumer<model.chat.ChatMessage.Bundle> l) {
        if (l != null) chatBundleListeners.add(l);
    }

    public void removeChatBundleListener(Consumer<model.chat.ChatMessage.Bundle> l) {
        chatBundleListeners.remove(l);
    }

    public void addChatSummaryListener(Consumer<model.chat.ChatMessage.SummaryBundle> l) {
        if (l != null) chatSummaryListeners.add(l);
    }

    public void removeChatSummaryListener(Consumer<model.chat.ChatMessage.SummaryBundle> l) {
        chatSummaryListeners.remove(l);
    }

    public void addFriendBundleListener(Consumer<model.friendship.Friendship.Bundle> l) {
        if (l != null) friendBundleListeners.add(l);
    }
    public void removeFriendBundleListener(Consumer<model.friendship.Friendship.Bundle> l) {
        friendBundleListeners.remove(l);
    }
    public void addFriendSearchListener(Consumer<model.friendship.Friendship.SearchBundle> l) {
        if (l != null) friendSearchListeners.add(l);
    }
    public void removeFriendSearchListener(Consumer<model.friendship.Friendship.SearchBundle> l) {
        friendSearchListeners.remove(l);
    }

    /** Legacy support - overwrites/sets a primary listener if needed, 
     * but we'll adapt existing code to use the new add/remove pattern. */
    private Consumer<BidResult> legacyBidResultCallback;
    public void setBidResultCallback(Consumer<BidResult> callback) {
        if (this.legacyBidResultCallback != null) removeBidResultListener(this.legacyBidResultCallback);
        this.legacyBidResultCallback = callback;
        if (callback != null) addBidResultListener(callback);
    }

    private Consumer<String> legacyStringMessageCallback;
    public void setStringMessageCallback(Consumer<String> callback) {
        if (this.legacyStringMessageCallback != null) removeStringMessageListener(this.legacyStringMessageCallback);
        this.legacyStringMessageCallback = callback;
        if (callback != null) addStringMessageListener(callback);
    }

    public void connect(String host, int port) throws IOException {
        if (socket != null && !socket.isClosed()) return;

        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        isRunning = true;

        Thread listenerThread = new Thread(this::listen);
        listenerThread.setDaemon(true);
        listenerThread.start();

        System.out.println(">>> Đã kết nối tới Server tại " + host + ":" + port);
    }

    private void listen() {
        try {
            while (isRunning) {
                Object data = in.readObject();
                if (data != null) {
                    handleIncomingData(data);
                }
            }
        } catch (Exception e) {
            System.err.println(">>> Mất kết nối tới Server: " + e.getMessage());
            close();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleIncomingData(Object data) {
        Platform.runLater(() -> {
            System.out.println(">>> [CLIENT] Nhận data kiểu: " + data.getClass().getSimpleName());

            // 1. BidResult — kết quả đặt giá
            if (data instanceof BidResult result) {
                System.out.println(">>> [CLIENT] BidResult nhận được: " + result);
                if (bidResultListeners.isEmpty()) {
                    showBidResultAlert(result);
                } else {
                    for (Consumer<BidResult> listener : bidResultListeners) {
                        listener.accept(result);
                    }
                }

            // 2. List<Auction> — toàn bộ danh sách phiên
            } else if (data instanceof List<?>) {
                List<Auction> auctions = (List<Auction>) data;
                System.out.println(">>> [CLIENT] Cập nhật danh sách " + auctions.size() + " phiên");
                model.manager.AppState.getInstance().getAuctionList().setAll(auctions);

            // 3. Auction — cập nhật phiên đơn lẻ
            } else if (data instanceof Auction updatedAuction) {
                System.out.println(">>> [CLIENT] Cập nhật phiên: " + updatedAuction.getAuctionId());
                updateSingleAuction(updatedAuction);

            // 4. Notification.Bundle — danh sách thông báo trả về cho FETCH_NOTIFICATIONS
            } else if (data instanceof model.notification.Notification.Bundle bundle) {
                for (var l : notificationBundleListeners) {
                    l.accept(bundle);
                }

            // 5. Notification.RefreshSignal — server báo có thông báo mới, client fetch lại
            } else if (data instanceof model.notification.Notification.RefreshSignal) {
                for (Runnable l : notificationRefreshListeners) {
                    l.run();
                }

            // 5b. ChatMessage — tin nhắn mới hoặc cập nhật (read/like)
            } else if (data instanceof model.chat.ChatMessage chatMsg) {
                for (var l : chatMessageListeners) {
                    l.accept(chatMsg);
                }

            // 5c. ChatMessage.Bundle — lịch sử hội thoại
            } else if (data instanceof model.chat.ChatMessage.Bundle chatBundle) {
                for (var l : chatBundleListeners) {
                    l.accept(chatBundle);
                }

            // 5d. ChatMessage.SummaryBundle — danh sách hội thoại
            } else if (data instanceof model.chat.ChatMessage.SummaryBundle sb) {
                for (var l : chatSummaryListeners) {
                    l.accept(sb);
                }

            // 5e. Friendship.Bundle — danh sách bạn bè + lời mời
            } else if (data instanceof model.friendship.Friendship.Bundle fb) {
                for (var l : friendBundleListeners) {
                    l.accept(fb);
                }

            // 5f. Friendship.SearchBundle — kết quả tìm kiếm user
            } else if (data instanceof model.friendship.Friendship.SearchBundle sb2) {
                for (var l : friendSearchListeners) {
                    l.accept(sb2);
                }

            // 6. String — message từ server (TOPUP_OK, PAY_OK, *_FAILED, ...)
            } else if (data instanceof String msg) {
                System.out.println(">>> [CLIENT] Server message: " + msg);
                for (Consumer<String> listener : stringMessageListeners) {
                    listener.accept(msg);
                }
            }
        });
    }

    private void showBidResultAlert(BidResult result) {
        javafx.scene.control.Alert.AlertType type = switch (result.getStatus()) {
            case SUCCESS -> javafx.scene.control.Alert.AlertType.INFORMATION;
            case OUTBID  -> javafx.scene.control.Alert.AlertType.WARNING;
            case FAILURE -> javafx.scene.control.Alert.AlertType.ERROR;
        };
        
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle("Kết quả đặt giá");
        alert.setHeaderText(null);
        alert.setContentText(result.getMessage());
        alert.showAndWait();
    }

    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            System.err.println(">>> Lỗi khi gửi dữ liệu: " + e.getMessage());
        }
    }

    public void close() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateSingleAuction(Auction updated) {
        var list = model.manager.AppState.getInstance().getAuctionList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getAuctionId().equals(updated.getAuctionId())) {
                Auction existing = list.get(i);
                
                // MẸO QUAN TRỌNG: Nếu bản cập nhật từ server bị thiếu history (do serialization hoặc 
                // logic server tối giản), ta phải giữ lại history cũ để chart và table không bị trắng.
                if (updated.getBidHistory().isEmpty() && !existing.getBidHistory().isEmpty()) {
                    updated.restoreBidHistory(existing.getBidHistory());
                }

                boolean priceChanged = existing.getCurrentPrice() != updated.getCurrentPrice();
                boolean statusChanged = !existing.getStatus().equals(updated.getStatus());
                boolean bidHistoryChanged = existing.getBidHistory().size() != updated.getBidHistory().size();
                boolean highestBidAppeared = existing.getHighestBid() == null && updated.getHighestBid() != null;

                // Phát hiện gia hạn thời gian do Anti-Sniping
                if ("RUNNING".equals(updated.getStatus())
                        && updated.getEndTime() != null && existing.getEndTime() != null
                        && updated.getEndTime().isAfter(existing.getEndTime())) {
                    for (Consumer<String> listener : stringMessageListeners) {
                        listener.accept("ANTI_SNIPE_EXTENDED:" + updated.getAuctionId());
                    }
                }

                if (priceChanged || statusChanged || bidHistoryChanged || highestBidAppeared) {
                    list.set(i, updated);
                }
                return;
            }
        }
        list.add(updated);
    }
}
