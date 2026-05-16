package model;

import model.auction.Auction;
import model.item.Electronics;
import model.item.Item;
import model.user.Bidder;

import exception.InvalidBidException;
import exception.AuctionClosedException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho thuật toán Anti-sniping (Yêu cầu 3.2.3 — Gia hạn phiên đấu giá).
 *
 * <p>Quy tắc: nếu có bid hợp lệ trong X giây cuối → tự động gia hạn thêm Y giây.
 * Ví dụ đề bài: end 20:00:00, bid 19:59:50 (còn 10s) → kéo dài đến 20:01:00.</p>
 */
@DisplayName("Anti-sniping – kiểm tra logic gia hạn phiên đấu giá")
class AntiSnipingTest {

    private Bidder bidder1;
    private Bidder bidder2;
    private static final double STARTING_PRICE = 100.0;

    @BeforeEach
    void setUp() {
        // Cấu hình mặc định theo đề: X=10s, Y=60s
        Auction.configureAntiSnipe(10, 60);
        Auction.setAntiSnipeEnabled(true);
        bidder1 = new Bidder("U_001", "alice", "alice@test.com", "pass1");
        bidder2 = new Bidder("U_002", "bob",   "bob@test.com",   "pass2");
    }

    @AfterEach
    void tearDown() {
        // Trả lại cấu hình mặc định để không ảnh hưởng test khác (Singleton-static)
        Auction.configureAntiSnipe(10, 60);
        Auction.setAntiSnipeEnabled(true);
    }

    private Auction runningAuction(LocalDateTime start, LocalDateTime end) {
        Item item = new Electronics("ITEM_001", "Laptop Test", "Mô tả", STARTING_PRICE, "Brand");
        Auction a = new Auction("AUC_001", item, start, end);
        a.setStatus("RUNNING");
        return a;
    }

    // ================================================================
    // NHÓM 1 — Kích hoạt gia hạn đúng ví dụ đề bài
    // ================================================================

    @Test
    @DisplayName("Bid trong X giây cuối → gia hạn thêm đúng Y giây")
    void bidInLastXSeconds_extendsByY() {
        LocalDateTime now = LocalDateTime.now();
        Auction a = runningAuction(now.minusHours(1), now.plusSeconds(8)); // còn 8s ≤ X=10s

        LocalDateTime before = a.getEndTime();
        a.placeBid(bidder1, 150.0);
        LocalDateTime after = a.getEndTime();

        assertTrue(after.isAfter(before), "endTime phải được đẩy về sau");
        assertEquals(60, java.time.Duration.between(before, after).getSeconds(),
                "Phải gia hạn đúng Y=60 giây");
        assertEquals(1, a.getExtensionCount());
    }

    @Test
    @DisplayName("Bid đúng ngưỡng biên X (=10s) vẫn được gia hạn")
    void bidExactlyAtThreshold_stillExtends() {
        LocalDateTime now = LocalDateTime.now();
        Auction a = runningAuction(now.minusHours(1), now.plusSeconds(10));

        LocalDateTime before = a.getEndTime();
        a.placeBid(bidder1, 150.0);

        assertTrue(a.getEndTime().isAfter(before));
        assertEquals(1, a.getExtensionCount());
    }

    // ================================================================
    // NHÓM 2 — KHÔNG gia hạn khi không thuộc vùng nguy hiểm
    // ================================================================

    @Test
    @DisplayName("Bid sớm (ngoài X giây cuối) → KHÔNG gia hạn")
    void bidEarly_doesNotExtend() {
        LocalDateTime now = LocalDateTime.now();
        Auction a = runningAuction(now.minusHours(1), now.plusSeconds(120)); // còn 120s

        LocalDateTime before = a.getEndTime();
        a.placeBid(bidder1, 150.0);

        assertEquals(before, a.getEndTime());
        assertEquals(0, a.getExtensionCount());
    }

    @Test
    @DisplayName("Bid không hợp lệ (giá thấp) → KHÔNG gia hạn")
    void invalidBid_doesNotExtend() {
        LocalDateTime now = LocalDateTime.now();
        Auction a = runningAuction(now.minusHours(1), now.plusSeconds(3));

        LocalDateTime before = a.getEndTime();
        assertThrows(InvalidBidException.class, () -> a.placeBid(bidder1, 50.0));

        assertEquals(before, a.getEndTime(), "Bid bị từ chối không được gia hạn");
        assertEquals(0, a.getExtensionCount());
    }

    @Test
    @DisplayName("Bid đến SAU thời điểm kết thúc → KHÔNG hồi sinh phiên")
    void bidAfterEndTime_doesNotRevive() {
        LocalDateTime now = LocalDateTime.now();
        Auction a = runningAuction(now.minusHours(2), now.minusSeconds(5)); // đã quá hạn

        LocalDateTime before = a.getEndTime();
        a.placeBid(bidder1, 150.0); // status còn RUNNING vì auto-closure chưa chạy

        assertEquals(before, a.getEndTime());
        assertEquals(0, a.getExtensionCount());
    }

    // ================================================================
    // NHÓM 3 — Sniping lặp & bật/tắt
    // ================================================================

    @Test
    @DisplayName("Snipe liên tục ở phút cuối → gia hạn nhiều lần")
    void repeatedSniping_extendsMultipleTimes() {
        Auction.configureAntiSnipe(2, 3); // X=2s, Y=3s cho test nhanh
        LocalDateTime now = LocalDateTime.now();
        Auction a = runningAuction(now.minusHours(1), now.plusSeconds(1));

        a.placeBid(bidder1, 150.0);                       // còn 1s → gia hạn lần 1
        a.setEndTime(LocalDateTime.now().plusSeconds(1)); // mô phỏng đợi gần hết
        a.placeBid(bidder2, 200.0);                       // còn 1s → gia hạn lần 2

        assertEquals(2, a.getExtensionCount());
    }

    @Test
    @DisplayName("Tắt anti-sniping → không bao giờ gia hạn")
    void disabled_neverExtends() {
        Auction.setAntiSnipeEnabled(false);
        LocalDateTime now = LocalDateTime.now();
        Auction a = runningAuction(now.minusHours(1), now.plusSeconds(3));

        LocalDateTime before = a.getEndTime();
        a.placeBid(bidder1, 150.0);

        assertEquals(before, a.getEndTime());
        assertEquals(0, a.getExtensionCount());
    }

    @Test
    @DisplayName("Cấu hình X/Y <= 0 bị từ chối")
    void invalidConfig_throws() {
        assertThrows(IllegalArgumentException.class, () -> Auction.configureAntiSnipe(0, 60));
        assertThrows(IllegalArgumentException.class, () -> Auction.configureAntiSnipe(10, 0));
        assertThrows(IllegalArgumentException.class, () -> Auction.configureAntiSnipe(-5, -5));
    }

    // ================================================================
    // NHÓM 4 — An toàn đa luồng (kết hợp Concurrent Bidding)
    // ================================================================

    @Test
    @DisplayName("Concurrent: nhiều thread snipe đồng thời — state không hỏng")
    void concurrentSniping_stateRemainsConsistent() throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        final Auction a = runningAuction(now.minusHours(1), now.plusSeconds(5));

        int threadCount = 20;
        ExecutorService ex = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threadCount);
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final double amt = STARTING_PRICE + (i + 1) * 10;
            final Bidder b = new Bidder("T" + i, "u" + i, "u" + i + "@t.com", "p");
            ex.submit(() -> {
                try { start.await(); a.placeBid(b, amt); }
                catch (InvalidBidException | AuctionClosedException ignored) {}
                catch (Exception e) { unexpected.incrementAndGet(); }
                finally { done.countDown(); }
            });
        }
        start.countDown();
        done.await();
        ex.shutdown();

        assertEquals(0, unexpected.get(), "Không được có lỗi bất ngờ");
        assertTrue(a.getEndTime().isAfter(now.plusSeconds(5)), "Phiên phải đã được gia hạn");
        assertTrue(a.getExtensionCount() >= 1 && a.getExtensionCount() <= threadCount,
                "Số lần gia hạn phải hợp lý");
        assertNotNull(a.getHighestBid());
        assertTrue(a.getCurrentPrice() > STARTING_PRICE);
    }
}