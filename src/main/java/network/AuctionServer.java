package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import model.auction.Auction;
import model.manager.AuctionManager;

public class AuctionServer {
    private final int port;
    // Danh sách các "người phục vụ" cho từng Client
    private final static List<ClientHandler> clients = new ArrayList<>();
    private final List<ClientHandler> observers = new ArrayList<>();
    
    public synchronized void addObserver(ClientHandler observer) {
        observers.add(observer);
    }

    public synchronized void removeObserver(ClientHandler observer) {
        observers.remove(observer);
    }
    
    public synchronized void notifyObservers(Object data) {
        for (ClientHandler observer : observers) {
            observer.send(data); // Gửi dữ liệu qua Socket
        }
    }

    public AuctionServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server đấu giá đang chạy trên port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Có người mới kết nối: " + clientSocket.getInetAddress());

                // Mỗi Client kết nối vào sẽ có một Thread riêng để phục vụ
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Gửi dữ liệu tới tất cả mọi người (Dùng để cập nhật Dashboard đồng loạt)
    public synchronized static void broadcast(Object data) {
        for (ClientHandler client : clients) {
            client.send(data);
        }
    }

    public static void main(String[] args) {
    	System.out.println(">>> Đang khởi động Server...");
    	
    	// 1. Kích hoạt luồng đếm thời gian TRƯỚC KHI bật Server
        startAuctionLifecycleMonitor();
        
        new AuctionServer(1234).start();
    }
    
    private void startAuctionMonitor() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now();
            for (Auction auction : AuctionManager.getInstance().getAllAuctions()) {
                // Tự động mở phiên nếu đến giờ
                if (auction.getStatus().equals("OPEN") && now.isAfter(auction.getStartTime())) {
                    auction.setStatus("RUNNING");
                    broadcast(auction); // Thông báo trạng thái mới cho toàn bộ Client [cite: 95]
                }
                // Tự động đóng phiên khi hết thời gian [cite: 53]
                if (auction.getStatus().equals("RUNNING") && now.isAfter(auction.getEndTime())) {
                    auction.setStatus("FINISHED");
                    // Xác định người thắng cuộc (HighestBid) [cite: 54]
                    System.out.println("Phiên " + auction.getAuctionId() + " đã kết thúc!");
                    broadcast(auction); 
                }
            }
        }, 0, 1, TimeUnit.SECONDS); // Kiểm tra mỗi giây một lần
    }
    
    private static void startAuctionLifecycleMonitor() {
        new Thread(() -> {
            while (true) {
                try {
                    boolean hasChanges = false;
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    
                    // Lấy danh sách toàn bộ phiên đấu giá từ Manager
                    for (model.auction.Auction auction : model.manager.AuctionManager.getInstance().getAllAuctions()) {
                        
                        // 1. OPEN -> RUNNING (Đến giờ bắt đầu)
                        if ("OPEN".equals(auction.getStatus()) && !now.isBefore(auction.getStartTime())) {
                            auction.setStatus("RUNNING");
                            hasChanges = true;
                            System.out.println(">>> Server: Phiên " + auction.getAuctionId() + " đã chuyển sang RUNNING.");
                        }
                        // 2. RUNNING -> FINISHED (Hết thời gian đấu giá)
                        else if ("RUNNING".equals(auction.getStatus()) && !now.isBefore(auction.getEndTime())) {
                            auction.setStatus("FINISHED");
                            hasChanges = true;
                            System.out.println(">>> Server: Phiên " + auction.getAuctionId() + " đã chuyển sang FINISHED.");
                            
                            // Tại đây Hiếu có thể gọi thêm logic "Xác định người thắng cuộc"
                            // Ví dụ: auction.determineWinner();
                        }
                    }

                    // 3. Nếu có trạng thái thay đổi, phát sóng (broadcast) lại danh sách mới cho TẤT CẢ Client
                    if (hasChanges) {
                        broadcast(model.manager.AuctionManager.getInstance().getAllAuctions());
                        // Lưu ý: Đảm bảo bạn gọi đúng tên hàm broadcast() đang có trong Server của bạn
                    }

                    // Nghỉ ngơi 1 giây rồi quét tiếp (Tránh làm quá tải CPU)
                    Thread.sleep(1000);
                    
                } catch (InterruptedException e) {
                    System.err.println("Luồng kiểm tra thời gian bị gián đoạn!");
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}