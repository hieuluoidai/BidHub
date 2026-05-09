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
            else if (msg.startsWith("CANCEL_AUCTION:")) {
                handleCancelAuction(msg);
            }
            else if (msg.startsWith("PAY_AUCTION:")) {
                handlePayAuction(msg);
            }
            else if (msg.startsWith("TOPUP:")) {
                handleTopUp(msg);
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
     * Hủy phiên đấu giá: chuyển status sang CANCELED.
     * Format: "CANCEL_AUCTION:<auctionId>:<requesterId>"
     *
     * Quyền:
     *  - Admin: hủy phiên ở mọi trạng thái (trừ CANCELED, PAID)
     *  - Seller: chỉ hủy được phiên của mình ở trạng thái OPEN/RUNNING
     */
    private void handleCancelAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("CANCEL_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        User requester = new UserDAO().findById(requesterId);
        if (requester == null) {
            send("CANCEL_FAILED: Không tìm thấy người yêu cầu");
            return;
        }

        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction == null) {
            send("CANCEL_FAILED: Phiên không tồn tại");
            return;
        }

        boolean isAdmin = (requester instanceof Admin);
        int result = new AuctionDAO().cancelAuction(auctionId, requesterId, isAdmin);

        switch (result) {
            case 1 -> {
                // Cập nhật memory để client thấy ngay status mới
                auction.setStatus("CANCELED");
                // Giải phóng lock nếu phiên đang RUNNING bị hủy
                model.manager.ConcurrentBidManager.getInstance().releaseLock(auctionId);
                System.out.println(">>> Phiên " + auctionId + " đã bị HỦY bởi "
                        + requester.getUsername());
                send("CANCEL_OK:" + auctionId);
                server.broadcast(AuctionManager.getInstance().getAllAuctions());
            }
            case 0  -> send("CANCEL_FAILED: Bạn không có quyền hủy phiên này");
            case -1 -> send("CANCEL_FAILED: Trạng thái phiên không cho phép hủy "
                    + "(chỉ OPEN/RUNNING với seller, hoặc OPEN/RUNNING/FINISHED với admin)");
            default -> send("CANCEL_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    /**
     * Thanh toán phiên đấu giá đã FINISHED. Chỉ winner mới được gọi.
     * Format: "PAY_AUCTION:<auctionId>:<requesterId>"
     *
     * Quy trình ATOMIC (transaction trong UserDAO.transferAtomic):
     *   1. Tra winner_id, seller_id, final_price từ DB
     *   2. Kiểm tra requester có đúng là winner không
     *   3. Transfer atomic (lock row, kiểm balance, trừ winner, cộng seller)
     *   4. Nếu OK → markAsPaid → broadcast
     *
     * Phản hồi:
     *   PAY_OK:<auctionId>:<newBalance>      — thành công, kèm số dư mới của winner
     *   PAY_FAILED:<reason>                  — thiếu tiền hoặc không phải winner
     */
    private void handlePayAuction(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("PAY_FAILED: Lệnh sai định dạng");
            return;
        }
        String auctionId   = parts[1];
        String requesterId = parts[2];

        // 1. Lấy info winner từ DB
        AuctionDAO auctionDao = new AuctionDAO();
        String[] winnerInfo = auctionDao.findWinnerInfo(auctionId);
        if (winnerInfo == null) {
            send("PAY_FAILED: Phiên chưa kết thúc hoặc không có người thắng");
            return;
        }

        String winnerId  = winnerInfo[0];
        String sellerId  = winnerInfo[1];
        double finalPrice;
        try {
            finalPrice = Double.parseDouble(winnerInfo[2]);
        } catch (NumberFormatException e) {
            send("PAY_FAILED: Dữ liệu phiên bị lỗi");
            return;
        }

        // 2. Verify requester là winner thật sự (chống tampering)
        if (!winnerId.equals(requesterId)) {
            send("PAY_FAILED: Bạn không phải người thắng phiên này");
            return;
        }

        // 3. Transfer atomic
        UserDAO userDao = new UserDAO();
        double currentBalance = userDao.getBalance(winnerId);
        if (currentBalance < finalPrice) {
            send(String.format("PAY_FAILED: Số dư không đủ (cần $%.2f, có $%.2f)",
                    finalPrice, currentBalance));
            return;
        }

        boolean transferOk = userDao.transferAtomic(winnerId, sellerId, finalPrice);
        if (!transferOk) {
            send("PAY_FAILED: Lỗi chuyển tiền (có thể do race condition, vui lòng thử lại)");
            return;
        }

        // 4. Cập nhật status PAID
        boolean paidOk = auctionDao.markAsPaid(auctionId);
        if (!paidOk) {
            // Edge case: transfer rồi mà status không update — log để admin biết
            System.err.println(">>> [PAY] DB inconsistent! Transfer OK nhưng PAID fail cho phiên "
                    + auctionId);
            send("PAY_FAILED: Lỗi cập nhật trạng thái phiên");
            return;
        }

        // 5. Cập nhật memory + broadcast
        Auction auction = AuctionManager.getInstance().getAuctionById(auctionId);
        if (auction != null) auction.setStatus("PAID");

        double newBalance = userDao.getBalance(winnerId);
        System.out.printf(">>> [PAID] Phiên %s — %s trả $%.2f cho %s | balance còn $%.2f%n",
                auctionId, winnerId, finalPrice, sellerId, newBalance);

        send("PAY_OK:" + auctionId + ":" + newBalance);
        server.broadcast(AuctionManager.getInstance().getAllAuctions());
    }

    /**
     * Nạp tiền vào ví user.
     * Format: "TOPUP:<userId>:<amount>"
     *
     * Mô phỏng nạp tiền: server "xử lý giao dịch" trong 1.5s rồi cộng vào balance.
     * Tương lai sẽ tích hợp payment gateway thật.
     *
     * Phản hồi:
     *   TOPUP_OK:<newBalance>      — thành công
     *   TOPUP_FAILED:<reason>      — số tiền không hợp lệ / user không tồn tại
     */
    private void handleTopUp(String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            send("TOPUP_FAILED: Lệnh sai định dạng");
            return;
        }
        String userId = parts[1];
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            send("TOPUP_FAILED: Số tiền không hợp lệ");
            return;
        }

        if (amount <= 0) {
            send("TOPUP_FAILED: Số tiền phải lớn hơn 0");
            return;
        }
        if (amount > 1_000_000_000) {
            send("TOPUP_FAILED: Số tiền vượt quá giới hạn cho phép ($1,000,000,000)");
            return;
        }

        UserDAO userDao = new UserDAO();
        User user = userDao.findById(userId);
        if (user == null) {
            send("TOPUP_FAILED: Người dùng không tồn tại");
            return;
        }

        // Mô phỏng "xử lý giao dịch" — sleep 1.5s
        try {
            System.out.println(">>> [TOPUP] Đang xử lý $" + amount + " cho " + user.getUsername() + "...");
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Cộng tiền: lấy balance hiện tại + amount, set lại
        // (không dùng transferAtomic vì đây không phải transfer giữa 2 user)
        double current = userDao.getBalance(userId);
        double newBalance = current + amount;
        boolean ok = userDao.setBalance(userId, newBalance);

        if (ok) {
            System.out.printf(">>> [TOPUP] %s nạp $%.2f thành công | balance: $%.2f → $%.2f%n",
                    user.getUsername(), amount, current, newBalance);
            send("TOPUP_OK:" + newBalance);
        } else {
            send("TOPUP_FAILED: Lỗi cập nhật số dư");
        }
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