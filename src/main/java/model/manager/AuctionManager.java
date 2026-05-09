package model.manager;

import model.auction.Auction;
import model.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import exception.InvalidBidException;
import exception.AuctionClosedException;

/**
 * Quản lý và điều hành toàn hệ thống phía Server.
 */
public class AuctionManager {
    private static AuctionManager instance;
    private final List<Auction> auctions; // Danh sách gốc quản lý mọi phiên đấu giá

    private AuctionManager() {
        auctions = new ArrayList<>();
    }

    // Singleton
    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    // Thêm phiên đấu giá mới (Đảm bảo an toàn đa luồng)
    public synchronized void addAuction(Auction auction) {
        if (auction != null) {
            auctions.add(auction);
            System.out.println(">>> Hệ thống: Đã thêm phiên đấu giá " + auction.getAuctionId());
        }
    }

    // Tìm phiên đấu giá bằng Id
    public synchronized Auction getAuctionById(String auctionId) {
        for (Auction auction : auctions) {
            if (auction.getAuctionId().equals(auctionId)) {
                return auction;
            }
        }
        return null;
    }

    /**
     * Xử lý bid đồng thời, ngăn chặn Race Condition.
     * Sử dụng các Exception cụ thể để báo lỗi chính xác về Client.
     */
    public synchronized boolean processBid(String auctionId, double newPrice, User bidder) {
        Auction auction = getAuctionById(auctionId);

        if (auction == null) {
            System.err.println("Lỗi: Không tìm thấy ID phiên " + auctionId);
            return false;
        }

        try {
            auction.placeBid(bidder, newPrice);
            return true;
        } catch (InvalidBidException | AuctionClosedException e) {
            System.err.println("Lỗi đặt giá: " + e.getMessage());
            return false;
        }
    }

    // Trả về copy List để bảo vệ List gốc (Encapsulation).
    public synchronized List<Auction> getAllAuctions() {
        return new ArrayList<>(this.auctions);
    }

    /**
     * Tự động update trạng thái phiên (Real-time update).
     * Khi phiên FINISHED → giải phóng lock của ConcurrentBidManager để tránh
     * giữ lock vô tận cho các phiên đã kết thúc.
     */
    public void startAutoClosureService(network.AuctionServer server) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        database.AuctionDAO auctionDao = new database.AuctionDAO();

        scheduler.scheduleAtFixedRate(() -> {
            boolean hasChange = false;

            synchronized (auctions) {
                LocalDateTime now = LocalDateTime.now();
                for (Auction auction : auctions) {

                    // 1. Tự động set trạng thái RUNNING khi đến giờ bắt đầu
                    if ("OPEN".equals(auction.getStatus()) && now.isAfter(auction.getStartTime())) {
                        auction.setStatus("RUNNING");
                        auctionDao.updateStatus(auction.getAuctionId(), "RUNNING");
                        System.out.println(">>> [BẮT ĐẦU] Phiên " + auction.getAuctionId());
                        hasChange = true;
                    }

                    // 2. Tự động kết thúc phiên khi hết thời gian
                    if ("RUNNING".equals(auction.getStatus()) && now.isAfter(auction.getEndTime())) {
                        auction.setStatus("FINISHED");
                        auctionDao.updateStatus(auction.getAuctionId(), "FINISHED");

                        // Giải phóng lock của phiên đã đóng để không giữ tài nguyên (Logic của Hiếu)
                        ConcurrentBidManager.getInstance().releaseLock(auction.getAuctionId());

                        String winner = (auction.getHighestBid() != null)
                                ? auction.getHighestBid().getBidder().getUsername()
                                : "Không có";
                        System.out.println(">>> [KẾT THÚC] Phiên " + auction.getAuctionId()
                                + " - Người thắng: " + winner);
                        hasChange = true;

                        // Note: KHÔNG còn auto-pay. Winner phải tự bấm nút Thanh toán
                        // trong Item Details để chuyển FINISHED → PAID.
                    }
                }
            }

            if (hasChange) {
                server.broadcast(getAllAuctions());
            }

        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Đồng bộ hóa dữ liệu phiên từ Database or Client gửi lên.
     */
    public synchronized void updateAuction(Auction updatedAuction) {
        if (updatedAuction == null) return;

        for (int i = 0; i < auctions.size(); i++) {
            if (auctions.get(i).getAuctionId().equals(updatedAuction.getAuctionId())) {
                auctions.set(i, updatedAuction);
                System.out.println(">>> Đã đồng bộ giá phiên " + updatedAuction.getAuctionId()
                        + " ($" + updatedAuction.getCurrentPrice() + ")");
                return;
            }
        }
        addAuction(updatedAuction);
    }

    /**
     * Xóa phiên đấu giá khỏi danh sách trong bộ nhớ.
     * Cần gọi sau khi đã xóa khỏi DB để giữ trạng thái nhất quán giữa server-memory và DB.
     * Đồng thời giải phóng lock của ConcurrentBidManager nếu có.
     */
    public synchronized boolean removeAuction(String auctionId) {
        boolean removed = auctions.removeIf(a -> a.getAuctionId().equals(auctionId));
        if (removed) {
            ConcurrentBidManager.getInstance().releaseLock(auctionId);
            System.out.println(">>> Hệ thống: Đã xóa phiên đấu giá " + auctionId);
        }
        return removed;
    }

    /**
     * Reload lại 1 phiên đấu giá từ DB vào memory (sau khi user sửa thông tin).
     * Logic: xóa instance cũ → load lại từ DB → add vào.
     * Nếu DB không còn (do bị xóa) thì chỉ remove khỏi memory.
     */
    public synchronized void reloadAuctionFromDB(String auctionId) {
        auctions.removeIf(a -> a.getAuctionId().equals(auctionId));
        Auction reloaded = new database.AuctionDAO().findById(auctionId);
        if (reloaded != null) {
            auctions.add(reloaded);
            System.out.println(">>> Đã reload phiên " + auctionId + " từ DB");
        }
    }

    /**
     * Xóa toàn bộ phiên trong memory.
     * CHỈ DÙNG TRONG UNIT TEST để đảm bảo mỗi test bắt đầu với state sạch —
     * tránh state pollution của Singleton giữa các test method.
     * KHÔNG được dùng trong production logic.
     */
    public synchronized void clearAll() {
        auctions.clear();
        System.out.println(">>> [TEST] Đã xóa toàn bộ phiên trong memory");
    }
}