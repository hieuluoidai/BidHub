package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import model.auction.Auction;
import model.manager.AuctionManager;
import model.user.User;

/**
 * Xử lý từng Client kết nối tới Server.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean active = true; // Biến kiểm soát trạng thái kết nối của Client hiện tại

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    /**
     * Kiểm tra xem Client có còn đang kết nối tới Server hay không
     */
    public boolean isAlive() {
        return active && !socket.isClosed();
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (active) {
                // Đọc yêu cầu từ Client và phân loại xử lý
                Object request = in.readObject();
                handleRequest(request);
            }
        } catch (EOFException | SocketException e) {
            System.out.println(">>> Thông báo: Một Client đã thoát.");
        } catch (Exception e) {
            System.err.println(">>> Lỗi ClientHandler: " + e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Phân loại request để xử lí
     */
    private void handleRequest(Object request) {
        // TH1: Nhận Object Auction (Tạo mới hoặc Update giá)
        if (request instanceof Auction incomingAuction) {
            Auction existing = AuctionManager.getInstance().getAuctionById(incomingAuction.getAuctionId());
            
            if (existing != null) {
                // Xử lý cập nhật giá và lưu xuống Database
                AuctionManager.getInstance().updateAuction(incomingAuction);
                
                if (incomingAuction.getHighestBid() != null) {
                    saveBidToDatabase(incomingAuction);
                }
            } else {
                // Tạo mới phiên đấu giá
                AuctionManager.getInstance().addAuction(incomingAuction);
                System.out.println(">>> Đã tạo phiên mới: " + incomingAuction.getAuctionId());
            }

            // Update toàn bộ danh cách các phiên lên hệ thống
            server.broadcast(AuctionManager.getInstance().getAllAuctions()); 
        } 
        // TRƯỜNG HỢP 2: Nhận lệnh dạng chuỗi văn bản (String)
        else if (request instanceof String msg) {
            handleStringRequest(msg);
        }
    }

    /**
     * Xử lý các lệnh điều khiển dạng String
     */
    private void handleStringRequest(String msg) {
        try {
            if (msg.equals("REFRESH_DATA")) {
                send(AuctionManager.getInstance().getAllAuctions());
            }
            else if (msg.startsWith("BID:")) {
                handleConcurrentBid(msg);
            }
        } catch (Exception e) {
            send("ERROR: Lệnh sai định dạng - " + e.getMessage());
        }
    }

    /**
     * Xử lý bid đồng thời. Format: "BID:<auctionId>:<amount>:<bidderId>"
     */
    private void handleConcurrentBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) {
            send(model.auction.BidResult.failure("?", 0, "Lệnh BID sai định dạng!"));
            return;
        }

        String auctionId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            send(model.auction.BidResult.failure(auctionId, 0, "Số tiền không hợp lệ!"));
            return;
        }
        String bidderId = parts[3];

        User bidder = new database.UserDAO().findById(bidderId);
        if (bidder == null) {
            send(model.auction.BidResult.failure(auctionId, amount, "Người dùng không tồn tại!"));
            return;
        }

        model.auction.BidResult result =
                model.manager.ConcurrentBidManager.getInstance().processBid(auctionId, amount, bidder);

        System.out.printf(">>> [BID] %s $%.2f phiên %s → %s%n",
                bidder.getUsername(), amount, auctionId, result.getStatus());

        // Gửi kết quả riêng về client vừa bid
        send(result);

        if (result.isSuccess()) {
            Auction updated = AuctionManager.getInstance().getAuctionById(auctionId);
            if (updated != null) saveBidToDatabase(updated);
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        }
    }

    /**
     * Lưu giao dịch xuống MySQL để đảm bảo không mất dữ liệu khi Server sập.
     */
    private void saveBidToDatabase(Auction auction) {
        database.BidTransactionDAO bidDao = new database.BidTransactionDAO();
        String auctionId = auction.getAuctionId();
        String bidderId = auction.getHighestBid().getBidder().getUserId();
        double amount = auction.getCurrentPrice();
        
        if (bidDao.save(auctionId, bidderId, amount)) {
            System.out.println(">>> Backup thành công giá $" + amount + " cho phiên " + auctionId);
        }
    }

    /**
     * Gửi Object dữ liệu qua Socket về phía Client.
     */
    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset(); // Xóa bộ nhớ đệm để gửi Object mới nhất
            }
        } catch (IOException e) {
            active = false;
        }
    }

    /**
     * Đóng các luồng dữ liệu và giải phóng tài nguyên.
     */
    private void close() {
        active = false;
        try {
            if (socket != null) socket.close();
            server.removeObserver(this); // Rút tên khỏi danh sách theo dõi của Server
        } catch (IOException e) { 
            e.printStackTrace();
        }
    }
}