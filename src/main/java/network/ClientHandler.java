package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import model.auction.Auction;
import model.auction.BidResult;
import model.manager.AuctionManager;
import model.manager.ConcurrentBidManager;
import model.user.User;
import model.user.Admin;
import database.UserDAO;
import database.AuctionDAO;
import database.BidTransactionDAO;

/**
 * Xử lý từng Client kết nối tới Server.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean active = true;

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    public boolean isAlive() {
        return active && !socket.isClosed();
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (active) {
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

    private void handleRequest(Object request) {
        // TRƯỜNG HỢP 1: Nhận Object Auction (Tạo mới hoặc Update theo luồng cũ)
        if (request instanceof Auction incomingAuction) {
            Auction existing = AuctionManager.getInstance().getAuctionById(incomingAuction.getAuctionId());

            if (existing != null) {
                AuctionManager.getInstance().updateAuction(incomingAuction);
                if (incomingAuction.getHighestBid() != null) {
                    saveBidToDatabase(incomingAuction);
                }
            } else {
                AuctionManager.getInstance().addAuction(incomingAuction);
                System.out.println(">>> Đã tạo phiên mới: " + incomingAuction.getAuctionId());
            }
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        } 
        // TRƯỜNG HỢP 2: Nhận lệnh dạng chuỗi văn bản (String)
        else if (request instanceof String msg) {
            handleStringRequest(msg);
        }
    }

    private void handleStringRequest(String msg) {
        try {
            if (msg.equals("REFRESH_DATA")) {
                send(AuctionManager.getInstance().getAllAuctions());
            }
            else if (msg.startsWith("BID:")) {
                handleConcurrentBid(msg);
            }
            else if (msg.startsWith("DELETE_AUCTION:")) {
                handleDeleteAuction(msg);
            }
            else if (msg.startsWith("RELOAD_AUCTION:")) {
                handleReloadAuction(msg);
            }
        } catch (Exception e) {
            send("ERROR: Lệnh sai định dạng - " + e.getMessage());
        }
    }

    /**
     * Xóa 1 phiên đấu giá khỏi hệ thống.
     * Format: "DELETE_AUCTION:<auctionId>:<requesterId>"
     */
    private void handleDeleteAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("DELETE_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        User requester = new UserDAO().findById(requesterId);
        if (requester == null) {
            send("DELETE_FAILED: Không tìm thấy người yêu cầu");
            return;
        }

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null) {
            send("DELETE_FAILED: Phiên không tồn tại");
            return;
        }

        boolean ok;
        if (requester instanceof Admin) {
            // Admin có quyền xóa mọi phiên
            ok = new AuctionDAO().delete(auctionId);
        } else {
            // Seller: Không cho xóa phiên đã kết thúc
            if ("FINISHED".equals(auction.getStatus())) {
                send("DELETE_FAILED: Phiên đã kết thúc, không thể xóa!");
                return;
            }
            int result = new AuctionDAO().deleteIfOwner(auctionId, requesterId);
            if (result == 0) { send("DELETE_FAILED: Bạn không có quyền xóa phiên này"); return; }
            ok = (result == 1);
        }

        if (ok) {
            AuctionManager.getInstance().removeAuction(auctionId);
            System.out.println(">>> Phiên " + auctionId + " đã bị xóa bởi " + requester.getUsername());
            send("DELETE_OK:" + auctionId);
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        } else {
            send("DELETE_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    /**
     * Reload 1 phiên từ DB sau khi Client cập nhật DB (thông qua DAO).
     */
    private void handleReloadAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 2) {
            send("RELOAD_FAILED: Sai định dạng");
            return;
        }
        String auctionId = parts[1];
        AuctionManager.getInstance().reloadAuctionFromDB(auctionId);
        send("RELOAD_OK:" + auctionId);
        server.broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    /**
     * Xử lý bid đồng thời cực nhanh và an toàn.
     * Format: "BID:<auctionId>:<amount>:<bidderId>"
     */
    private void handleConcurrentBid(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) {
            send(BidResult.failure("?", 0, "Lệnh BID sai định dạng!"));
            return;
        }

        String auctionId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            send(BidResult.failure(auctionId, 0, "Số tiền không hợp lệ!"));
            return;
        }
        
        String bidderId = parts[3];
        User bidder = new UserDAO().findById(bidderId);
        if (bidder == null) {
            send(BidResult.failure(auctionId, amount, "Người dùng không tồn tại!"));
            return;
        }

        // Tích hợp luồng lưu DB Atomic của Hiếu
        BidTransactionDAO bidDao = new BidTransactionDAO();
        BidResult result = ConcurrentBidManager.getInstance()
                .processBid(auctionId, amount, bidder, bidDao);

        System.out.printf(">>> [BID] %s $%.2f phiên %s → %s | %s%n",
                bidder.getUsername(), amount, auctionId, result.getStatus(),
                ConcurrentBidManager.getInstance().metricsSummary());

        // Phản hồi riêng cho Client vừa đặt giá
        send(result);

        // Nếu bid thành công, thông báo giá mới cho toàn bộ Client khác
        if (result.isSuccess()) {
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        }
    }

    /**
     * Lưu giao dịch xuống MySQL (Dành cho luồng Object cũ).
     */
    private void saveBidToDatabase(Auction auction) {
        BidTransactionDAO bidDao = new BidTransactionDAO();
        String auctionId = auction.getAuctionId();
        String bidderId = auction.getHighestBid().getBidder().getUserId();
        double amount = auction.getCurrentPrice();

        if (bidDao.save(auctionId, bidderId, amount)) {
            System.out.println(">>> Backup thành công giá $" + amount + " cho phiên " + auctionId);
        }
    }

    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            active = false;
        }
    }

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