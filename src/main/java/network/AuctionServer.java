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
        // Phương thức bộ giám sát thời gian đấu giá
        startAuctionLifecycleMonitor();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println(">>> Server đấu giá đang chạy trên port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println(">>> Kết nối mới từ: " + clientSocket.getInetAddress());

                // Mỗi Client được phục vụ bởi một Thread riêng biệt
                ClientHandler handler = new ClientHandler(clientSocket, this);
                synchronized (clients) {
                    clients.add(handler);
                }
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
        }
    }

    /**
     * Phát sóng dữ liệu tới tất cả Client đang kết nối (Real-time Update).
     */
    public synchronized void broadcast(Object data) {
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

    /**
     * Luồng chạy ngầm kiểm tra và update trạng thái các phiên đấu giá mỗi giây.
     */
    private void startAuctionLifecycleMonitor() {
        new Thread(() -> {
            while (true) {
                try {
                    boolean hasChanges = false;
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    
                    for (Auction auction : AuctionManager.getInstance().getAllAuctions()) {
                        String oldStatus = auction.getStatus();
                        
                        // 1. Chuyển sang RUNNING khi đến giờ bắt đầu
                        if ("OPEN".equals(oldStatus) && !now.isBefore(auction.getStartTime())) {
                            auction.setStatus("RUNNING");
                            hasChanges = true;
                        }
                        // 2. Chuyển sang FINISHED khi hết thời gian
                        else if ("RUNNING".equals(oldStatus) && !now.isBefore(auction.getEndTime())) {
                            auction.setStatus("FINISHED");
                            hasChanges = true;
                        }

                        if (hasChanges) {
                            System.out.println(">>> Hệ thống: Phiên " + auction.getAuctionId() + " chuyển sang " + auction.getStatus());
                        }
                    }

                    if (hasChanges) {
                        broadcast(AuctionManager.getInstance().getAllAuctions());
                    }
                    Thread.sleep(1000); // Kiểm tra định kỳ 1 giây
                } catch (Exception e) {
                    System.err.println("Lỗi bộ giám sát: " + e.getMessage());
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        System.out.println(">>> Đang khởi động hệ thống...");

        // Phục hồi dữ liệu từ Database vào RAM
        List<Auction> savedAuctions = new database.AuctionDAO().findAll();
        database.BidTransactionDAO bidDao = new database.BidTransactionDAO();
        
        for (Auction a : savedAuctions) {
            // Đồng bộ mức giá cao nhất hiện tại từ lịch sử giao dịch
            String[] winnerData = bidDao.findWinner(a.getAuctionId());
            if (winnerData != null) {
                double highestPrice = Double.parseDouble(winnerData[1]);
                a.getItem().setCurrentPrice(highestPrice);
            }
            AuctionManager.getInstance().addAuction(a);
        }

        System.out.println(">>> Đã nạp " + savedAuctions.size() + " phiên đấu giá.");
        
        // Khởi tạo và chạy Server
        new AuctionServer(1234).start();
    }
}