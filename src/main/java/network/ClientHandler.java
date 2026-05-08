package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import model.auction.Auction;
import model.auction.BidResult;
import model.manager.AuctionManager;
import model.manager.ConcurrentBidManager;
import model.user.User;

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
        // TH1: Nhận Object Auction (Tạo mới hoặc Update giá theo cách cũ)
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
        // TH2: Lệnh String
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
        } catch (Exception e) {
            send("ERROR: Lệnh sai định dạng - " + e.getMessage());
        }
    }

    /**
     * Xử lý bid đồng thời. Format: "BID:&lt;auctionId&gt;:&lt;amount&gt;:&lt;bidderId&gt;".
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

        User bidder = new database.UserDAO().findById(bidderId);
        if (bidder == null) {
            send(BidResult.failure(auctionId, amount, "Người dùng không tồn tại!"));
            return;
        }

        // Atomic: memory + DB cùng nằm trong critical section của ConcurrentBidManager
        database.BidTransactionDAO bidDao = new database.BidTransactionDAO();
        BidResult result = ConcurrentBidManager.getInstance()
                .processBid(auctionId, amount, bidder, bidDao);

        System.out.printf(">>> [BID] %s $%.2f phiên %s → %s | %s%n",
                bidder.getUsername(), amount, auctionId, result.getStatus(),
                ConcurrentBidManager.getInstance().metricsSummary());

        // Phản hồi riêng cho client vừa bid
        send(result);

        // Broadcast cho mọi client nếu bid thành công
        if (result.isSuccess()) {
            server.broadcast(AuctionManager.getInstance().getAllAuctions());
        }
    }

    /**
     * Lưu giao dịch xuống MySQL — chỉ dùng cho luồng cũ (Auction object update).
     * Luồng BID mới đã lưu DB atomic trong ConcurrentBidManager.
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
            server.removeObserver(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
