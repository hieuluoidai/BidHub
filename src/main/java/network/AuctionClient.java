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
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

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
        listenerThread.setDaemon(true); // Tự động đóng luồng khi thoát ứng dụng
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
    private void handleIncomingData(Object data) {
        Platform.runLater(() -> {
            if (data instanceof List<?>) {
                @SuppressWarnings("unchecked")
				List<Auction> auctions = (List<Auction>) data;
                model.manager.AppState.getInstance().getAuctionList().setAll(auctions);
            } else if (data instanceof Auction updatedAuction) {
                updateSingleAuction(updatedAuction);
            } else if (data instanceof String msg) {
                if (msg.startsWith("BID_ERROR:")) {
                    String errorText = msg.substring("BID_ERROR:".length());
                    // Hiện alert thông báo lỗi bid cho user
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.WARNING
                    );
                    alert.setTitle("Đặt giá thất bại");
                    alert.setHeaderText(null);
                    alert.setContentText(errorText);
                    alert.showAndWait();
                }
            }
        });
    }
    
    /**
     * Gửi yêu cầu/dữ liệu lên Server.
     */
    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset(); // Xóa bộ nhớ đệm để gửi trạng thái mới nhất của Object
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
     */
    private void updateSingleAuction(model.auction.Auction updated) {
        var list = model.manager.AppState.getInstance().getAuctionList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getAuctionId().equals(updated.getAuctionId())) {
                list.set(i, updated); // Thay thế nếu đã tồn tại
                return;
            }
        }
        list.add(updated); // Thêm mới nếu chưa có trong danh sách
    }
}