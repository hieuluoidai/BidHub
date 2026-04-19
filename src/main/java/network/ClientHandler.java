package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

import model.auction.Auction;
import model.manager.AuctionManager;
import model.user.User;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User currentUser; // Quản lý người dùng đang kết nối tại luồng này

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                // Đọc lệnh từ Client (Chấp nhận cả String và Object)
                Object request = in.readObject();
                handleRequest(request);
            }
        } catch (EOFException | SocketException e) {
            // Đây là lỗi bình thường khi Client chủ động đóng kết nối [cite: 60]
            System.out.println(">>> Client đã ngắt kết nối.");
        } catch (Exception e) {
            // Xử lý các lỗi dữ liệu hoặc lỗi kết nối bất ngờ khác [cite: 60]
            System.err.println(">>> Lỗi hệ thống: " + e.getMessage());
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void handleRequest(Object request) {
        // TRƯỜNG HỢP 1: Client gửi Object Auction (Tạo phiên mới HOẶC Cập nhật giá Bid)
        if (request instanceof Auction incomingAuction) {
            
            // 1. Kiểm tra xem phiên đấu giá này đã có trong "sổ gốc" chưa
            Auction existing = AuctionManager.getInstance().getAuctionById(incomingAuction.getAuctionId());
            
            if (existing != null) {
                // Nếu đã có -> Đây là lệnh cập nhật (Bid giá). 
                // CHỈNH SỬA DANH SÁCH GỐC TRÊN RAM
                AuctionManager.getInstance().updateAuction(incomingAuction);
                
                // THÊM MỚI: LƯU XUỐNG DATABASE NGAY LẬP TỨC
                if (incomingAuction.getHighestBid() != null) {
                    database.BidTransactionDAO bidDao = new database.BidTransactionDAO();
                    
                    String auctionId = incomingAuction.getAuctionId();
                    String bidderId = incomingAuction.getHighestBid().getBidder().getUserId();
                    double amount = incomingAuction.getCurrentPrice();
                    
                    // Gọi hàm save() từ file của đồng nghiệp
                    boolean isSaved = bidDao.save(auctionId, bidderId, amount);
                    if (isSaved) {
                        System.out.println(">>> Đã backup thành công giá " + amount + " xuống MySQL!");
                    }
                }
                
            } else {
                // Nếu chưa có -> Đây là lệnh Tạo Phiên Mới
                AuctionManager.getInstance().addAuction(incomingAuction);
                System.out.println(">>> Đã tạo phiên đấu giá mới: " + incomingAuction.getAuctionId());
            }

            // 2. QUAN TRỌNG: Phát sóng TOÀN BỘ danh sách về cho tất cả Client
            AuctionServer.broadcast(AuctionManager.getInstance().getAllAuctions()); 
        } 
        
        // TRƯỜNG HỢP 2: Client gửi lệnh String (Login, Refresh)
        else if (request instanceof String msg) {
            handleStringRequest(msg);
        }
    }

    private void handleStringRequest(String msg) {
        try {
            // 1. Lấy danh sách ban đầu [cite: 65]
            if (msg.equals("REFRESH_DATA")) {
                List<Auction> currentList = AuctionManager.getInstance().getAllAuctions();
                send(currentList);
            } 
            
            // 2. Xử lý đặt giá: "BID:ID:PRICE" [cite: 47]
            else if (msg.startsWith("BID:")) {
                String[] parts = msg.split(":");
                String auctionId = parts[1];
                double newPrice = Double.parseDouble(parts[2]);

                // Xử lý đấu giá đồng thời tại Manager [cite: 83]
                boolean success = AuctionManager.getInstance().processBid(auctionId, newPrice, this.currentUser);

                if (success) {
                    Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
                    server.broadcast(updated); // Realtime Update cho mọi người [cite: 143]
                } else {
                    send("ERROR: Giá đặt không hợp lệ hoặc phiên đã đóng!"); // Xử lý lỗi [cite: 58]
                }
            }
            
            // 3. Giả lập xử lý Login để gán User vào luồng
            else if (msg.startsWith("LOGIN_SUCCESS:")) {
                // Hiếu có thể gửi Object User qua mạng để gán vào đây
                // this.currentUser = ...
            }
        } catch (Exception e) {
            send("ERROR: Lệnh không hợp lệ - " + e.getMessage());
        }
    }

    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset(); // Quan trọng: Xóa cache Object để gửi dữ liệu mới nhất
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        try {
            if (socket != null) socket.close();
            // Xóa khỏi danh sách observers của server nếu Hiếu có làm Observer Pattern
        } catch (IOException e) { e.printStackTrace(); }
    }
}