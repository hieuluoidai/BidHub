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

            // 4. String — message từ server (TOPUP_OK, PAY_OK, *_FAILED, ...)
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
