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
 */
public class AuctionClient {
    // Sử dụng volatile để đảm bảo an toàn đa luồng khi listener thread và UI thread cùng truy cập
    private volatile Consumer<BidResult> bidResultCallback;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

    /**
     * Đăng ký callback để xử lý BidResult (ví dụ: cập nhật UI riêng cho người bid).
     */
    public void setBidResultCallback(Consumer<BidResult> callback) {
        this.bidResultCallback = callback;
    }

    /**
     * Thiết lập kết nối tới Server và kích hoạt luồng lắng nghe dữ liệu.
     */
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
        // Mọi thay đổi lên giao diện JavaFX phải chạy trong Platform.runLater
        Platform.runLater(() -> {
            System.out.println(">>> [CLIENT] Nhận data kiểu: " + data.getClass().getSimpleName());

            // 1. Xử lý Kết quả đặt giá (BidResult)
            if (data instanceof BidResult result) {
                System.out.println(">>> [CLIENT] BidResult nhận được: " + result);
                if (bidResultCallback != null) {
                    bidResultCallback.accept(result);
                } else {
                    // Nếu controller chưa đăng ký callback, dùng thông báo mặc định
                    showBidResultAlert(result);
                }

            // 2. Xử lý Danh sách phiên đấu giá (Cập nhật Dashboard)
            } else if (data instanceof List<?>) {
                List<Auction> auctions = (List<Auction>) data;
                System.out.println(">>> [CLIENT] Cập nhật danh sách " + auctions.size() + " phiên");
                model.manager.AppState.getInstance().getAuctionList().setAll(auctions);

            // 3. Xử lý Cập nhật một phiên đơn lẻ
            } else if (data instanceof Auction updatedAuction) {
                System.out.println(">>> [CLIENT] Cập nhật phiên: " + updatedAuction.getAuctionId());
                updateSingleAuction(updatedAuction);
            }
        });
    }

    /**
     * Hiển thị thông báo kết quả đặt giá dưới dạng Popup Alert.
     */
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

    /**
     * Gửi yêu cầu/dữ liệu lên Server.
     */
    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset(); // Quan trọng để gửi object đã thay đổi trạng thái
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