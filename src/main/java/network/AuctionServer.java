package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import model.auction.Auction;
import model.manager.AuctionManager;

/**
 * Server chính điều phối kết nối Socket và quản lý luồng dữ liệu đấu giá.
 */
public class AuctionServer {
    private final int port;

    // Danh sách quản lý các kết nối Client đang hoạt động
    private final List<ClientHandler> clients = new ArrayList<>();
    private final List<ClientHandler> observers = new ArrayList<>();

    public AuctionServer(int port) {
        this.port = port;
    }

    /**
     * Khởi động Server và chấp nhận các kết nối mới từ Client.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println(">>> Server đấu giá đang chạy trên port " + port + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                synchronized (clients) { clients.add(handler); }
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
        }
    }

    /**
     * Gửi dữ liệu tới một người dùng cụ thể dựa trên ID.
     */
    public void sendToUser(String userId, Object data) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (userId.equals(client.getUserId()) && client.isAlive()) {
                    client.send(data);
                }
            }
        }
    }

    /**
     * Phát sóng dữ liệu tới tất cả Client đang kết nối (Real-time Update).
     */
    public void broadcast(Object data) {
        synchronized (clients) {
            // Remove các client đã mất kết nối trước khi gửi
            clients.removeIf(client -> !client.isAlive());
            for (ClientHandler client : clients) {
                client.send(data);
            }
        }
    }

    // Quản lý Observers
    public synchronized void addObserver(ClientHandler observer) { observers.add(observer); }
    public synchronized void removeObserver(ClientHandler observer) { observers.remove(observer); }
    public synchronized void notifyObservers(Object data) {
        for (ClientHandler observer : observers) {
            observer.send(data);
        }
    }

    public static void main(String[] args) {
        System.out.println(">>> Đang khởi động hệ thống...");

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(">>> Đang tắt Server...");
            database.DatabaseConnection.closePool();
        }));

        List<Auction> savedAuctions = new database.AuctionDAO().findAll();
        for (Auction a : savedAuctions) {
            AuctionManager.getInstance().addAuction(a);
        }
        System.out.println(">>> Đã nạp " + savedAuctions.size() + " phiên đấu giá.");

        AuctionServer server = new AuctionServer(1234);

        // Chỉ một lifecycle monitor duy nhất, dùng cái chuẩn trong AuctionManager
        AuctionManager.getInstance().startAutoClosureService(server);

        server.start();
    }
}
