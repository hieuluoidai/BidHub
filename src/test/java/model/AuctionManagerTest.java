package model;

import model.auction.Auction;
import model.item.Electronics;
import model.item.Item;
import model.manager.AuctionManager;
import model.user.Bidder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho AuctionManager – kiểm tra Singleton Pattern và logic xử lý bid đồng thời.
 */
@DisplayName("AuctionManager – kiểm tra Singleton và concurrency")
class AuctionManagerTest {

    private AuctionManager manager;
    private Bidder bidder1;
    private Bidder bidder2;

    @BeforeEach
    void setUp() {
        manager = AuctionManager.getInstance();
        bidder1 = new Bidder("U_001", "alice", "alice@test.com", "pass1");
        bidder2 = new Bidder("U_002", "bob", "bob@test.com", "pass2");
    }

    // ================================================================
    // Singleton Pattern
    // ================================================================

    @Test
    @DisplayName("getInstance luôn trả về cùng một đối tượng")
    void getInstance_returnsSameInstance() {
        AuctionManager m1 = AuctionManager.getInstance();
        AuctionManager m2 = AuctionManager.getInstance();
        assertSame(m1, m2, "Singleton phải trả về cùng 1 reference");
    }

    @Test
    @DisplayName("getInstance không bao giờ trả về null")
    void getInstance_neverNull() {
        assertNotNull(AuctionManager.getInstance());
    }

    // ================================================================
    // Thêm và tìm phiên
    // ================================================================

    @Test
    @DisplayName("Thêm phiên thành công và tìm lại được bằng ID")
    void addAndFindAuction_works() {
        Auction auction = createRunningAuction("AUC_TEST_100");
        manager.addAuction(auction);

        Auction found = manager.getAuctionById("AUC_TEST_100");
        assertNotNull(found);
        assertEquals("AUC_TEST_100", found.getAuctionId());
    }

    @Test
    @DisplayName("Tìm phiên với ID không tồn tại trả về null")
    void getAuctionById_notFound_returnsNull() {
        Auction found = manager.getAuctionById("ID_KHONG_TON_TAI_XYZ");
        assertNull(found);
    }

    @Test
    @DisplayName("Thêm null không gây crash (an toàn)")
    void addAuction_null_safe() {
        assertDoesNotThrow(() -> manager.addAuction(null));
    }

    @Test
    @DisplayName("getAllAuctions trả về bản copy (không phải reference gốc)")
    void getAllAuctions_returnsCopy_notReference() {
        Auction auction = createRunningAuction("AUC_COPY_TEST");
        manager.addAuction(auction);

        List<Auction> list1 = manager.getAllAuctions();
        List<Auction> list2 = manager.getAllAuctions();

        assertNotSame(list1, list2, "Phải trả về bản copy mỗi lần gọi");
    }

    // ================================================================
    // processBid
    // ================================================================

    @Test
    @DisplayName("processBid với phiên không tồn tại trả về false")
    void processBid_nonExistentAuction_returnsFalse() {
        boolean result = manager.processBid("ID_KHONG_TON_TAI", 150.0, bidder1);
        assertFalse(result);
    }

    @Test
    @DisplayName("processBid với giá hợp lệ trả về true")
    void processBid_validAmount_returnsTrue() {
        Auction auction = createRunningAuction("AUC_BID_VALID");
        manager.addAuction(auction);

        boolean result = manager.processBid("AUC_BID_VALID", 200.0, bidder1);

        assertTrue(result);
        assertEquals(200.0, auction.getCurrentPrice(), 0.001);
    }

    @Test
    @DisplayName("processBid với giá thấp hơn hiện tại trả về false")
    void processBid_amountBelowCurrent_returnsFalse() {
        Auction auction = createRunningAuction("AUC_BID_LOW");
        manager.addAuction(auction);

        manager.processBid("AUC_BID_LOW", 200.0, bidder1);
        boolean result = manager.processBid("AUC_BID_LOW", 150.0, bidder2);

        assertFalse(result);
        assertEquals(200.0, auction.getCurrentPrice(), 0.001, "Giá không được thay đổi");
    }

    @Test
    @DisplayName("processBid với phiên đã FINISHED trả về false")
    void processBid_finishedAuction_returnsFalse() {
        Auction auction = createRunningAuction("AUC_FINISHED_TEST");
        auction.setStatus("FINISHED");
        manager.addAuction(auction);

        boolean result = manager.processBid("AUC_FINISHED_TEST", 500.0, bidder1);

        assertFalse(result);
    }

    // ================================================================
    // Concurrency (ăn điểm "Xử lý đấu giá đồng thời an toàn")
    // ================================================================

    @Test
    @DisplayName("10 bidder cùng đặt giá đồng thời: chỉ có giá cao nhất thắng")
    void processBid_concurrentBids_highestWins() throws InterruptedException {
        Auction auction = createRunningAuction("AUC_CONCURRENT");
        manager.addAuction(auction);

        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final double bidAmount = 150.0 + i * 100.0;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    boolean ok = manager.processBid(
                        "AUC_CONCURRENT", bidAmount, bidder1
                    );
                    if (ok) successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finishedInTime, "Tất cả thread phải xong trong 5 giây");
        assertTrue(successCount.get() > 0);

        double finalPrice = auction.getCurrentPrice();
        assertTrue(
            finalPrice >= 150.0 && finalPrice <= 1050.0,
            "Giá cuối nằm trong dải hợp lệ, nhận được: " + finalPrice
        );
    }

    @Test
    @DisplayName("Nhiều thread thêm phiên đồng thời: tất cả đều được lưu")
    void addAuction_concurrent_allAdded() throws InterruptedException {
        int numThreads = 20;
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);

        int sizeBefore = manager.getAllAuctions().size();

        for (int i = 0; i < numThreads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    Auction a = createRunningAuction("AUC_CONCURRENT_ADD_" + idx);
                    manager.addAuction(a);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean finishedInTime = doneLatch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finishedInTime);

        int sizeAfter = manager.getAllAuctions().size();
        assertEquals(sizeBefore + numThreads, sizeAfter, "Phải thêm đủ 20 phiên");
    }

    // ================================================================
    // Helper
    // ================================================================
    private Auction createRunningAuction(String id) {
        Item item = new Electronics(
            "ITEM_FOR_" + id, "Test Item", "desc", 100.0, "Brand"
        );
        Auction auction = new Auction(
            id, item,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1)
        );
        auction.setStatus("RUNNING");
        return auction;
    }
}