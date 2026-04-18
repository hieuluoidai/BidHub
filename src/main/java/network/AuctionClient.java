package network;

import java.io.*;
import java.net.Socket;
import java.util.List;

import javafx.application.Platform;
import model.auction.Auction;

public class AuctionClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

    /**
     * Kết nối tới Server
     */
    public void connect(String host, int port) throws IOException {
        if (socket != null && !socket.isClosed()) return;

        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        isRunning = true;

        // Tạo một luồng riêng để nghe dữ liệu từ Server, không làm treo UI
        Thread listenerThread = new Thread(this::listen);
        listenerThread.setDaemon(true); // Tự tắt khi đóng App
        listenerThread.start();
        
        System.out.println(">>> Connected to Server at " + host + ":" + port);
    }

    private void listen() {
        try {
            while (isRunning) {
                Object data = in.readObject();
                if (data != null) {
                    // Xử lý dữ liệu nhận được ở đây
                    handleIncomingData(data);
                }
            }
        } catch (Exception e) {
            System.err.println(">>> Connection lost: " + e.getMessage());
            close();
        }
    }

    private void handleIncomingData(Object data) {
        Platform.runLater(() -> {
            if (data instanceof List<?>) {
                System.out.println(">>> CLIENT: Đã nhận danh sách từ Server. Số lượng: " + ((List<?>) data).size()); // Thêm dòng này
                List<Auction> auctions = (List<Auction>) data;
                model.manager.AppState.getInstance().getAuctionList().setAll(auctions);
            } else if (data instanceof Auction updatedAuction) {
                System.out.println(">>> CLIENT: Đã nhận 1 phiên mới: " + updatedAuction.getAuctionId()); // Thêm dòng này
                updateSingleAuction(updatedAuction); 
            }
        });
    }
    
    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset(); // Thêm dòng này để đảm bảo gửi dữ liệu mới nhất
            }
        } catch (IOException e) {
            e.printStackTrace();
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