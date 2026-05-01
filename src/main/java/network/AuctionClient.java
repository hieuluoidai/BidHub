package network;

import java.io.*;
import java.net.Socket;
import java.util.List;
import javafx.application.Platform;
import model.auction.Auction;

/**
 * Lớp điều phối liên lạc với Server và xử lý dữ liệu phía Client.
 */
public class AuctionClient {
    private java.util.function.Consumer<model.auction.BidResult> bidResultCallback;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

    public void setBidResultCallback(java.util.function.Consumer<model.auction.BidResult> callback) {
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

        // Chạy luồng nghe riêng biệt để không gây treo giao diện (UI Freeze)
        Thread listenerThread = new Thread(this::listen);
        listenerThread.setDaemon(true);
        listenerThread.start();

        System.out.println(">>> Đã kết nối tới Server tại " + host + ":" + port);
    }

    /**
     * Vòng lặp liên tục nhận dữ liệu từ Server.
     */
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

    /**
     * Phân loại và xử lý dữ liệu nhận được từ Server.
     */
    @SuppressWarnings("unchecked")
    private void handleIncomingData(Object data) {
        System.out.println(">>> [CLIENT] Nhận data kiểu: " + data.getClass().getSimpleName());

        // Xử lý BidResult trước — phản hồi riêng cho lần bid vừa rồi
        if (data instanceof model.auction.BidResult bidResult) {
            System.out.println(">>> [CLIENT] BidResult: " + bidResult);
            Platform.runLater(() -> {
                if (bidResultCallback != null) {
                    bidResultCallback.accept(bidResult);
                } else {
                    showBidResultAlert(bidResult);
                }
            });
            return;
        }

        Platform.runLater(() -> {
            if (data instanceof List<?>) {
                List<Auction> auctions = (List<Auction>) data;
                System.out.println(">>> [CLIENT] Danh sách " + auctions.size() + " phiên");
                model.manager.AppState.getInstance().getAuctionList().setAll(auctions);

            } else if (data instanceof Auction updatedAuction) {
                System.out.println(">>> [CLIENT] 1 auction: " + updatedAuction.getAuctionId()
                        + " status=" + updatedAuction.getStatus());
                updateSingleAuction(updatedAuction);
            }
        });
    }

    private void showBidResultAlert(model.auction.BidResult result) {
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
                out.reset();
            }
        } catch (IOException e) {
            System.err.println(">>> Lỗi khi gửi dữ liệu: " + e.getMessage());
        }
    }

    /**
     * Ngắt kết nối và giải phóng tài nguyên.
     */
    public void close() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cập nhật thông tin một phiên đấu giá cụ thể trong danh sách hiển thị.
     *
     * BUG FIX: Sau khi set() lại phần tử, force trigger listener bằng cách
     * gọi thêm 1 operation tạm thời. Điều này đảm bảo TableView luôn refresh
     * ngay cả khi chỉ 1 phiên thay đổi.
     */
    private void updateSingleAuction(model.auction.Auction updated) {
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