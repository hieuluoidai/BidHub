package model.manager;

import model.auction.Auction;
import model.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;

/**
 * Quản lý và điều hành toàn hệ thống phía Server
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
    
    // Xử lý bid đồng thời, ngăn chặn Race Condition.
    public synchronized boolean processBid(String auctionId, double newPrice, User bidder) {
        Auction auction = getAuctionById(auctionId);
        
        if (auction == null) {
            System.err.println("Lỗi: Không tìm thấy ID phiên " + auctionId);
            return false;
        }
        
        try {
            // Kiểm tra giá và bid
            auction.placeBid(bidder, newPrice);
            return true;
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.err.println("Lỗi đặt giá: " + e.getMessage());
            return false;
        }
    }
    

    // Trả về copy List để bảo vệ List gốc (Encapsulation).
    public synchronized List<Auction> getAllAuctions() {
        return new ArrayList<>(this.auctions);
    }
    
    /**
     * Tự động update trạng thái phiên t(Real-time update).
     */
    public void startAutoClosureService(network.AuctionServer server) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (auctions) {
                LocalDateTime now = LocalDateTime.now();
                for (Auction auction : auctions) {
                    // 1. Tự động set trạng thái RUNNING khi đến giờ bắt đầu
                    if ("OPEN".equals(auction.getStatus()) && now.isAfter(auction.getStartTime())) {
                        auction.setStatus("RUNNING");
                        System.out.println(">>> [BẮT ĐẦU] Phiên " + auction.getAuctionId());
                        server.broadcast(auction); // Update ngay lập tức cho tất cả Client
                    }
                    
                    // 2. Tự động kết thúc phiên khi hết thời gian
                    if ("RUNNING".equals(auction.getStatus()) && now.isAfter(auction.getEndTime())) {
                        auction.setStatus("FINISHED");
                        
                        String winner = (auction.getHighestBid() != null) 
                                        ? auction.getHighestBid().getBidder().getUsername() 
                                        : "Không có";
                        System.out.println(">>> [KẾT THÚC] Phiên " + auction.getAuctionId() + " - Người thắng: " + winner);
                        
                        server.broadcast(auction); // Thông báo kết quả cuối cùng cho các Client
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS); // Kiểm tra chính xác mỗi giây một lần
    }
    
    /**
     * Đồng bộ hóa dữ liệu phiên từ Database or Client gửi lên.
     */
    public synchronized void updateAuction(Auction updatedAuction) {
        if (updatedAuction == null) return;
        
        for (int i = 0; i < auctions.size(); i++) {
            if (auctions.get(i).getAuctionId().equals(updatedAuction.getAuctionId())) {
                auctions.set(i, updatedAuction);
                System.out.println(">>> Đã đồng bộ giá phiên " + updatedAuction.getAuctionId() + " ($" + updatedAuction.getCurrentPrice() + ")");
                return;
            }
        }
        addAuction(updatedAuction); // Thêm mới nếu chưa tồn tại
    }
}