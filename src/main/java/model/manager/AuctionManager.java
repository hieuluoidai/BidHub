package model.manager;

import model.auction.Auction;
import model.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;

public class AuctionManager {
    private static AuctionManager instance;
    private final List<Auction> auctions; // Dùng final để đảm bảo tính bất biến của tham chiếu list

    private AuctionManager() {
        auctions = new ArrayList<>();
    }

    // Singleton Pattern: Quản lý tập trung toàn bộ phiên đấu giá [cite: 141]
    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    // Thêm đồng bộ hóa để tránh xung đột khi nhiều Seller cùng thêm sản phẩm [cite: 83]
    public synchronized void addAuction(Auction auction) {
        if (auction != null) {
            auctions.add(auction);
            System.out.println("Auction " + auction.getAuctionId() + " has been added successfully.");
        }
    }

    public synchronized Auction getAuctionById(String auctionId) {
        for (Auction auction : auctions) {
            if (auction.getAuctionId().equals(auctionId)) {
                return auction;
            }
        }
        return null; 
    }
    
    /**
     * Xử lý đấu giá đồng thời (Concurrent Bidding) [cite: 83]
     * Đảm bảo không xảy ra Lost Update hoặc hai người cùng thắng [cite: 85, 86, 88]
     */
    public synchronized boolean processBid(String auctionId, double newPrice, User bidder) {
        Auction auction = getAuctionById(auctionId);
        
        if (auction == null) {
            System.out.println("Lỗi: Không tìm thấy phiên đấu giá " + auctionId);
            return false;
        }
        
        try {
            // Gọi hàm placeBid đã có cơ chế kiểm tra logic nghiệp vụ nội bộ
            auction.placeBid(bidder, newPrice);
            return true;
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Xử lý lỗi & ngoại lệ: Đặt giá thấp hoặc phiên đã đóng [cite: 56, 58, 59]
            System.err.println("Lỗi đấu giá: " + e.getMessage());
            return false;
        }
    }
    
    // Trả về bản sao của danh sách để đảm bảo tính đóng gói (Encapsulation) 
    public synchronized List<Auction> getAllAuctions() {
        return new ArrayList<>(this.auctions);
    }
    
    public void startAutoClosureService(network.AuctionServer server) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (auctions) {
                LocalDateTime now = LocalDateTime.now();
                for (Auction auction : auctions) {
                    // 1. Tự động chuyển từ OPEN sang RUNNING khi đến giờ bắt đầu 
                    if ("OPEN".equals(auction.getStatus()) && now.isAfter(auction.getStartTime())) {
                        auction.setStatus("RUNNING");
                        System.out.println(">>> Phiên " + auction.getAuctionId() + " bắt đầu RUNNING.");
                        server.broadcast(auction); // Notify Observers (Realtime Update) [cite: 94, 95]
                    }
                    
                    // 2. Tự động chuyển từ RUNNING sang FINISHED khi hết thời gian [cite: 53, 55]
                    if ("RUNNING".equals(auction.getStatus()) && now.isAfter(auction.getEndTime())) {
                        auction.setStatus("FINISHED");
                        
                        // Xác định người thắng cuộc (Winner) 
                        if (auction.getHighestBid() != null) {
                            System.out.println(">>> Phiên " + auction.getAuctionId() + " KẾT THÚC. Người thắng: " 
                                               + auction.getHighestBid().getBidder().getUsername());
                        } else {
                            System.out.println(">>> Phiên " + auction.getAuctionId() + " KẾT THÚC. Không có người mua.");
                        }
                        
                        server.broadcast(auction); // Thông báo kết quả cho tất cả Client [cite: 95]
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS); // Kiểm tra mỗi giây một lần để đảm bảo tính Realtime 
    }
    
    public synchronized void updateAuction(Auction updatedAuction) {
        if (updatedAuction == null) return;
        
        for (int i = 0; i < auctions.size(); i++) {
            if (auctions.get(i).getAuctionId().equals(updatedAuction.getAuctionId())) {
                // Thay thế phần tử cũ bằng phần tử mới truyền lên
                auctions.set(i, updatedAuction);
                System.out.println(">>> SERVER GHI NHẬN: Phiên " + updatedAuction.getAuctionId() + " cập nhật giá thành " + updatedAuction.getCurrentPrice());
                return; // Xong việc thì thoát vòng lặp
            }
        }
        
        // Nếu quét hết danh sách mà không thấy (trường hợp hiếm), thì coi như thêm mới
        System.out.println(">>> SERVER: Không tìm thấy phiên cũ, tiến hành thêm mới.");
        addAuction(updatedAuction);
    }
}