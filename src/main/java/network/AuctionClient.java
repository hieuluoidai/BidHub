package network;

import java.io.*;
import java.net.Socket;
import javafx.application.Platform;

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
        // Mẹo: Dùng Platform.runLater để cập nhật UI từ luồng mạng an toàn
        Platform.runLater(() -> {
            System.out.println("Received from server: " + data);
            // Sau này Hiếu sẽ cập nhật TableView ở đây
        });
    }

    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
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
}