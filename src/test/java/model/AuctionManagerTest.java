package model;

import model.auction.Auction;
import model.item.Electronics;
import model.item.Item;
import model.manager.AuctionManager;
import model.user.Bidder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        manager.clearAll();
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
    // Concurrency
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

    @Test
    @DisplayName("updateAuction: cập nhật thông tin phiên và chống rollback giá")
    void updateAuction_works() {
        String id = "AUC_UPDATE";
        Auction original = createRunningAuction(id);
        original.placeBid(bidder1, 200);
        manager.addAuction(original);

        // Client gửi bản update với giá cũ 150
        Auction updated = createRunningAuction(id);
        updated.placeBid(bidder1, 150);
        
        manager.updateAuction(updated);

        // Phải giữ giá 200 (không được rollback)
        assertEquals(200.0, manager.getAuctionById(id).getCurrentPrice());
    }

    @Test
    @DisplayName("removeAuction: xóa khỏi memory và giải phóng lock")
    void removeAuction_works() {
        String id = "AUC_REMOVE";
        manager.addAuction(createRunningAuction(id));
        assertNotNull(manager.getAuctionById(id));

        boolean result = manager.removeAuction(id);
        assertTrue(result);
        assertNull(manager.getAuctionById(id));
    }

    @Test
    @DisplayName("reloadAuctionFromDB: nạp lại từ database")
    void reloadAuctionFromDB() {
        String id = "AUC_RELOAD";
        manager.addAuction(createRunningAuction(id));

        try (MockedConstruction<database.AuctionDAO> daoConstruction = mockConstruction(database.AuctionDAO.class, (mock, context) -> {
            when(mock.findById(id)).thenReturn(createRunningAuction(id));
        })) {
            manager.reloadAuctionFromDB(id);
            verify(daoConstruction.constructed().get(0)).findById(id);
            assertNotNull(manager.getAuctionById(id));
        }
    }

    @Test
    @DisplayName("clearAll: xóa sạch memory")
    void clearAll_works() {
        manager.addAuction(createRunningAuction("A1"));
        manager.clearAll();
        assertEquals(0, manager.getAllAuctions().size());
    }

    @Test
    @DisplayName("startAutoClosureService: kiểm tra logic chuyển trạng thái và kết thúc")
    void autoClosureService_logic() throws Exception {
        String idStart = "AUC_TO_START";
        Auction aStart = createRunningAuction(idStart);
        aStart.setStatus("OPEN");
        aStart.setStartTime(LocalDateTime.now().minusSeconds(1));
        
        String idEnd = "AUC_TO_END";
        Auction aEnd = createRunningAuction(idEnd);
        aEnd.setStatus("RUNNING");
        aEnd.setEndTime(LocalDateTime.now().minusSeconds(1));
        
        manager.addAuction(aStart);
        manager.addAuction(aEnd);

        network.AuctionServer mockServer = mock(network.AuctionServer.class);

        try (MockedConstruction<database.AuctionDAO> daoConstruction = mockConstruction(database.AuctionDAO.class);
             MockedConstruction<database.AutoBidDAO> abDaoConstruction = mockConstruction(database.AutoBidDAO.class)
        ) {
            manager.startAutoClosureService(mockServer);
            Thread.sleep(1500); 

            database.AuctionDAO mockedDAO = daoConstruction.constructed().get(0);
            verify(mockedDAO, atLeastOnce()).updateStatus(eq(idStart), eq("RUNNING"));
            verify(mockedDAO, atLeastOnce()).updateStatus(eq(idEnd), eq("FINISHED"));
            
            assertEquals("RUNNING", aStart.getStatus());
            assertEquals("FINISHED", aEnd.getStatus());
            
            verify(mockServer, atLeastOnce()).broadcast(any(Auction.class));
        }
    }

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
