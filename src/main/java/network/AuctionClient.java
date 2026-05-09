package network;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;
import model.auction.Auction;
import model.auction.BidResult;

/**
 * Lớp điều phối liên lạc với Server và xử lý dữ liệu phía Client.
 *
 * Hỗ trợ 2 callback cơ chế:
 *  - {@link #setBidResultCallback}: nhận BidResult sau khi bid (riêng cho BidController)
 *  - {@link #setStringMessageCallback}: nhận String response (TOPUP_OK, PAY_OK, ...)
 *    cho TopUpController và ItemDetailsController.
 */
public class AuctionClient {
    // volatile vì listener thread và UI thread cùng truy cập
    private volatile Consumer<BidResult> bidResultCallback;
    private volatile Consumer<String>    stringMessageCallback;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

    /**
     * Đăng ký callback nhận BidResult.
     */
    public void setBidResultCallback(Consumer<BidResult> callback) {
        this.bidResultCallback = callback;
    }

    /**
     * Đăng ký callback nhận String message từ server (TOPUP_OK, PAY_OK, *_FAILED, ...).
     * Truyền null để hủy đăng ký.
     */
    public void setStringMessageCallback(Consumer<String> callback) {
        this.stringMessageCallback = callback;
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
                if (bidResultCallback != null) {
                    bidResultCallback.accept(result);
                } else {
                    showBidResultAlert(result);
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
                Consumer<String> handler = this.stringMessageCallback;
                if (handler != null) {
                    handler.accept(msg);
                }
                // Nếu không có handler đăng ký, tin nhắn sẽ bị bỏ qua —
                // hợp lý vì các response như DELETE_OK đã được broadcast danh sách kèm theo.
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
                list.set(i, updated);
                return;
            }
        }
        list.add(updated);
    }
}