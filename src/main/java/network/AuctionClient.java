package network;

import exception.AuthenticationException;
import exception.ErrorResponse;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javafx.application.Platform;
import model.auth.AuthRequest;
import model.auth.AuthResponse;
import model.auction.Auction;
import model.auction.BidResult;
import model.user.User;

/**
 * Coordinates client-server communication and dispatches updates to the UI.
 */
public class AuctionClient {
    private final List<Consumer<BidResult>> bidResultListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> stringMessageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.notification.Notification.Bundle>> notificationBundleListeners =
            new CopyOnWriteArrayList<>();
    private final List<Runnable> notificationRefreshListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.chat.ChatMessage>> chatMessageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.chat.ChatMessage.Bundle>> chatBundleListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<model.chat.ChatMessage.SummaryBundle>> chatSummaryListeners =
            new CopyOnWriteArrayList<>();
    private final List<Consumer<model.friendship.Friendship.Bundle>> friendBundleListeners =
            new CopyOnWriteArrayList<>();
    private final List<Consumer<model.friendship.Friendship.SearchBundle>> friendSearchListeners =
            new CopyOnWriteArrayList<>();

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

    public void addBidResultListener(Consumer<BidResult> listener) {
        if (listener != null) {
            bidResultListeners.add(listener);
        }
    }

    public void removeBidResultListener(Consumer<BidResult> listener) {
        bidResultListeners.remove(listener);
    }

    public void addStringMessageListener(Consumer<String> listener) {
        if (listener != null) {
            stringMessageListeners.add(listener);
        }
    }

    public void removeStringMessageListener(Consumer<String> listener) {
        stringMessageListeners.remove(listener);
    }

    public void addNotificationBundleListener(Consumer<model.notification.Notification.Bundle> listener) {
        if (listener != null) {
            notificationBundleListeners.add(listener);
        }
    }

    public void addNotificationRefreshListener(Runnable listener) {
        if (listener != null) {
            notificationRefreshListeners.add(listener);
        }
    }

    public void addChatMessageListener(Consumer<model.chat.ChatMessage> listener) {
        if (listener != null) {
            chatMessageListeners.add(listener);
        }
    }

    public void removeChatMessageListener(Consumer<model.chat.ChatMessage> listener) {
        chatMessageListeners.remove(listener);
    }

    public void addChatBundleListener(Consumer<model.chat.ChatMessage.Bundle> listener) {
        if (listener != null) {
            chatBundleListeners.add(listener);
        }
    }

    public void removeChatBundleListener(Consumer<model.chat.ChatMessage.Bundle> listener) {
        chatBundleListeners.remove(listener);
    }

    public void addChatSummaryListener(Consumer<model.chat.ChatMessage.SummaryBundle> listener) {
        if (listener != null) {
            chatSummaryListeners.add(listener);
        }
    }

    public void removeChatSummaryListener(Consumer<model.chat.ChatMessage.SummaryBundle> listener) {
        chatSummaryListeners.remove(listener);
    }

    public void addFriendBundleListener(Consumer<model.friendship.Friendship.Bundle> listener) {
        if (listener != null) {
            friendBundleListeners.add(listener);
        }
    }

    public void removeFriendBundleListener(Consumer<model.friendship.Friendship.Bundle> listener) {
        friendBundleListeners.remove(listener);
    }

    public void addFriendSearchListener(Consumer<model.friendship.Friendship.SearchBundle> listener) {
        if (listener != null) {
            friendSearchListeners.add(listener);
        }
    }

    public void removeFriendSearchListener(Consumer<model.friendship.Friendship.SearchBundle> listener) {
        friendSearchListeners.remove(listener);
    }

    private Consumer<BidResult> legacyBidResultCallback;

    public void setBidResultCallback(Consumer<BidResult> callback) {
        if (legacyBidResultCallback != null) {
            removeBidResultListener(legacyBidResultCallback);
        }
        legacyBidResultCallback = callback;
        if (callback != null) {
            addBidResultListener(callback);
        }
    }

    private Consumer<String> legacyStringMessageCallback;

    public void setStringMessageCallback(Consumer<String> callback) {
        if (legacyStringMessageCallback != null) {
            removeStringMessageListener(legacyStringMessageCallback);
        }
        legacyStringMessageCallback = callback;
        if (callback != null) {
            addStringMessageListener(callback);
        }
    }

    public void connect(String host, int port) throws IOException {
        if (socket != null && !socket.isClosed()) {
            return;
        }

        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        isRunning = true;
        startListenerThread();
        System.out.println(">>> Da ket noi toi Server tai " + host + ":" + port);
    }

    public User authenticate(String host, int port, String username, String password)
            throws IOException, AuthenticationException {
        close();

        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());

        send(AuthRequest.login(username, password));
        Object response = readObjectOnce("dang nhap");
        if (!(response instanceof AuthResponse authResponse)) {
            close();
            throw new IOException("Phan hoi dang nhap tu server khong hop le.");
        }
        if (!authResponse.isSuccess() || authResponse.getUser() == null) {
            close();
            throw new AuthenticationException(authResponse.getMessage());
        }

        isRunning = true;
        startListenerThread();
        return authResponse.getUser();
    }

    public User register(String host, int port, String fullName, LocalDate dateOfBirth,
                         String phone, String email, String username, String password)
            throws IOException, AuthenticationException {
        try (Socket tempSocket = new Socket(host, port);
             ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream());
             ObjectInputStream tempIn = new ObjectInputStream(tempSocket.getInputStream())) {
            tempOut.writeObject(AuthRequest.register(fullName, dateOfBirth, phone, email, username, password));
            tempOut.flush();
            tempOut.reset();

            Object response;
            try {
                response = tempIn.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Khong the doc phan hoi dang ky tu server.", e);
            }

            if (!(response instanceof AuthResponse authResponse)) {
                throw new IOException("Phan hoi dang ky tu server khong hop le.");
            }
            if (!authResponse.isSuccess() || authResponse.getUser() == null) {
                throw new AuthenticationException(authResponse.getMessage());
            }
            return authResponse.getUser();
        }
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
            if (isRunning) {
                System.err.println(">>> Mat ket noi toi Server: " + e.getMessage());
            }
            close();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleIncomingData(Object data) {
        Platform.runLater(() -> {
            System.out.println(">>> [CLIENT] Nhan data kieu: " + data.getClass().getSimpleName());

            if (data instanceof BidResult result) {
                System.out.println(">>> [CLIENT] BidResult nhan duoc: " + result);
                if (bidResultListeners.isEmpty()) {
                    showBidResultAlert(result);
                } else {
                    for (Consumer<BidResult> listener : bidResultListeners) {
                        listener.accept(result);
                    }
                }
            } else if (data instanceof List<?> list) {
                List<Auction> auctions = (List<Auction>) list;
                System.out.println(">>> [CLIENT] Cap nhat danh sach " + auctions.size() + " phien");
                model.manager.AppState.getInstance().getAuctionList().setAll(auctions);
            } else if (data instanceof Auction updatedAuction) {
                System.out.println(">>> [CLIENT] Cap nhat phien: " + updatedAuction.getAuctionId());
                updateSingleAuction(updatedAuction);
            } else if (data instanceof model.notification.Notification.Bundle bundle) {
                for (var listener : notificationBundleListeners) {
                    listener.accept(bundle);
                }
            } else if (data instanceof model.notification.Notification.RefreshSignal) {
                for (Runnable listener : notificationRefreshListeners) {
                    listener.run();
                }
            } else if (data instanceof model.chat.ChatMessage chatMsg) {
                for (var listener : chatMessageListeners) {
                    listener.accept(chatMsg);
                }
            } else if (data instanceof model.chat.ChatMessage.Bundle chatBundle) {
                for (var listener : chatBundleListeners) {
                    listener.accept(chatBundle);
                }
            } else if (data instanceof model.chat.ChatMessage.SummaryBundle summaryBundle) {
                for (var listener : chatSummaryListeners) {
                    listener.accept(summaryBundle);
                }
            } else if (data instanceof model.friendship.Friendship.Bundle friendBundle) {
                for (var listener : friendBundleListeners) {
                    listener.accept(friendBundle);
                }
            } else if (data instanceof model.friendship.Friendship.SearchBundle searchBundle) {
                for (var listener : friendSearchListeners) {
                    listener.accept(searchBundle);
                }
            } else if (data instanceof ErrorResponse error) {
                String msg = "ERROR:" + error.getCode() + ":" + error.getMessage();
                System.out.println(">>> [CLIENT] Server error: " + msg);
                if (stringMessageListeners.isEmpty()) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Loi");
                    alert.setHeaderText(null);
                    alert.setContentText(error.getMessage());
                    alert.showAndWait();
                } else {
                    for (Consumer<String> listener : stringMessageListeners) {
                        listener.accept(msg);
                    }
                }
            } else if (data instanceof String msg) {
                System.out.println(">>> [CLIENT] Server message: " + msg);

                if (msg.startsWith("AUCTION_REMOVED:")) {
                    String id = msg.substring("AUCTION_REMOVED:".length());
                    model.manager.AppState.getInstance().getAuctionList()
                            .removeIf(auction -> auction.getAuctionId().equals(id));
                }

                for (Consumer<String> listener : stringMessageListeners) {
                    listener.accept(msg);
                }
            }
        });
    }

    private void showBidResultAlert(BidResult result) {
        javafx.scene.control.Alert.AlertType type = switch (result.getStatus()) {
            case SUCCESS -> javafx.scene.control.Alert.AlertType.INFORMATION;
            case OUTBID -> javafx.scene.control.Alert.AlertType.WARNING;
            case FAILURE -> javafx.scene.control.Alert.AlertType.ERROR;
        };

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle("Ket qua dat gia");
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
            System.err.println(">>> Loi khi gui du lieu: " + e.getMessage());
        }
    }

    public void close() {
        isRunning = false;
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
            out = null;
            in = null;
        }
    }

    private Object readObjectOnce(String action) throws IOException {
        try {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Khong the doc phan hoi " + action + " tu server.", e);
        }
    }

    private void startListenerThread() {
        Thread listenerThread = new Thread(this::listen);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void updateSingleAuction(Auction updated) {
        var list = model.manager.AppState.getInstance().getAuctionList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getAuctionId().equals(updated.getAuctionId())) {
                Auction existing = list.get(i);

                if (updated.getBidHistory().isEmpty() && !existing.getBidHistory().isEmpty()) {
                    updated.restoreBidHistory(existing.getBidHistory());
                }

                boolean priceChanged = existing.getCurrentPrice() != updated.getCurrentPrice();
                boolean statusChanged = !existing.getStatus().equals(updated.getStatus());
                boolean bidHistoryChanged = existing.getBidHistory().size() != updated.getBidHistory().size();
                boolean highestBidAppeared = existing.getHighestBid() == null && updated.getHighestBid() != null;

                if ("RUNNING".equals(updated.getStatus())
                        && updated.getEndTime() != null
                        && existing.getEndTime() != null
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
