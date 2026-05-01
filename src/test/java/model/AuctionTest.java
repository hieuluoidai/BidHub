package model;

import model.auction.Auction;
import model.auction.BidTransaction;
import model.item.Electronics;
import model.item.Item;
import model.user.Bidder;

import exception.InvalidBidException;
import exception.AuctionClosedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho lớp Auction – kiểm tra toàn bộ logic nghiệp vụ đấu giá.
 */
@DisplayName("Auction – kiểm tra logic đấu giá")
class AuctionTest {

    // Dữ liệu dùng chung cho mọi test
    private Auction auction;
    private Bidder  bidder1;
    private Bidder  bidder2;

    /** Giá khởi điểm dùng xuyên suốt */
    private static final double STARTING_PRICE = 100.0;

    /**
     * Tạo mới auction và 2 bidder trước mỗi test case.
     * Trạng thái ban đầu: OPEN (chưa bắt đầu).
     */
    @BeforeEach
    void setUp() {
        Item item = new Electronics(
            "ITEM_001", "Laptop Test", "Mô tả test", STARTING_PRICE, "TestBrand"
        );

        auction = new Auction(
            "AUC_001",
            item,
            LocalDateTime.now().minusHours(1),  // đã bắt đầu 1 tiếng trước
            LocalDateTime.now().plusHours(1)    // còn 1 tiếng nữa kết thúc
        );

        bidder1 = new Bidder("U_001", "alice", "alice@test.com", "pass1");
        bidder2 = new Bidder("U_002", "bob",   "bob@test.com",   "pass2");
    }

    // ================================================================
    // NHÓM 1 – Trạng thái ban đầu
    // ================================================================

    @Test
    @DisplayName("Trạng thái ban đầu phải là OPEN")
    void initialStatus_shouldBeOpen() {
        assertEquals("OPEN", auction.getStatus());
    }

    @Test
    @DisplayName("Giá hiện tại ban đầu bằng giá khởi điểm khi chưa có bid")
    void currentPrice_beforeAnyBid_equalsStartingPrice() {
        assertEquals(STARTING_PRICE, auction.getCurrentPrice(), 0.001);
    }

    @Test
    @DisplayName("Lịch sử bid ban đầu phải rỗng")
    void bidHistory_initially_isEmpty() {
        assertTrue(auction.getBidHistory().isEmpty());
    }

    // ================================================================
    // NHÓM 2 – Bid hợp lệ
    // ================================================================

    @Test
    @DisplayName("Bid hợp lệ: giá tăng lên đúng sau khi bid thành công")
    void placeBid_validAmount_updateCurrentPrice() {
        auction.setStatus("RUNNING");
        double bidAmount = 150.0;

        auction.placeBid(bidder1, bidAmount);

        assertEquals(bidAmount, auction.getCurrentPrice(), 0.001);
    }

    @Test
    @DisplayName("Bid hợp lệ: highestBid phải trỏ đúng vào người bid")
    void placeBid_validAmount_updatesHighestBidder() {
        auction.setStatus("RUNNING");

        auction.placeBid(bidder1, 150.0);

        assertNotNull(auction.getHighestBid());
        assertEquals("alice", auction.getHighestBid().getBidder().getUsername());
    }

    @Test
    @DisplayName("Bid hợp lệ: giao dịch được thêm vào bidHistory")
    void placeBid_validAmount_addsToHistory() {
        auction.setStatus("RUNNING");

        auction.placeBid(bidder1, 150.0);
        auction.placeBid(bidder2, 200.0);

        assertEquals(2, auction.getBidHistory().size());
    }

    @Test
    @DisplayName("Bid liên tiếp: người bid sau phải thắng nếu giá cao hơn")
    void placeBid_consecutiveBids_lastHighestWins() {
        auction.setStatus("RUNNING");

        auction.placeBid(bidder1, 150.0);
        auction.placeBid(bidder2, 200.0);

        assertEquals("bob",   auction.getHighestBid().getBidder().getUsername());
        assertEquals(200.0,   auction.getCurrentPrice(), 0.001);
    }

    // ================================================================
    // NHÓM 3 – Bid không hợp lệ (phải throw exception)
    // ================================================================

    @Test
    @DisplayName("Bid thấp hơn giá hiện tại phải ném InvalidBidException")
    void placeBid_amountBelowCurrent_throwsInvalidBid() {
        auction.setStatus("RUNNING");

        InvalidBidException ex = assertThrows(
            InvalidBidException.class,
            () -> auction.placeBid(bidder1, STARTING_PRICE - 1)
        );

        assertTrue(ex.getMessage().contains("phải cao hơn"));
    }

    @Test
    @DisplayName("Bid bằng giá hiện tại phải ném InvalidBidException")
    void placeBid_amountEqualCurrent_throwsInvalidBid() {
        auction.setStatus("RUNNING");

        assertThrows(
            InvalidBidException.class,
            () -> auction.placeBid(bidder1, STARTING_PRICE)
        );
    }

    @Test
    @DisplayName("Bid âm phải ném InvalidBidException")
    void placeBid_negativeAmount_throwsInvalidBid() {
        auction.setStatus("RUNNING");

        assertThrows(
            InvalidBidException.class,
            () -> auction.placeBid(bidder1, -50.0)
        );
    }

    // ================================================================
    // NHÓM 4 – Bid sai trạng thái phiên
    // ================================================================

    @Test
    @DisplayName("Bid khi phiên OPEN phải ném AuctionClosedException")
    void placeBid_whenStatusOpen_throwsAuctionClosed() {
        // auction mặc định ở trạng thái OPEN
        AuctionClosedException ex = assertThrows(
            AuctionClosedException.class,
            () -> auction.placeBid(bidder1, 150.0)
        );

        assertTrue(ex.getMessage().contains("OPEN"));
    }

    @Test
    @DisplayName("Bid khi phiên FINISHED phải ném AuctionClosedException")
    void placeBid_whenStatusFinished_throwsAuctionClosed() {
        auction.setStatus("FINISHED");

        assertThrows(
            AuctionClosedException.class,
            () -> auction.placeBid(bidder1, 150.0)
        );
    }

    @Test
    @DisplayName("Bid khi phiên CANCELED phải ném AuctionClosedException")
    void placeBid_whenStatusCanceled_throwsAuctionClosed() {
        auction.setStatus("CANCELED");

        assertThrows(
                AuctionClosedException.class,
            () -> auction.placeBid(bidder1, 150.0)
        );
    }

    // ================================================================
    // NHÓM 5 – Chuyển trạng thái phiên
    // ================================================================

    @Test
    @DisplayName("setStatus: chuyển OPEN → RUNNING thành công")
    void setStatus_openToRunning_works() {
        auction.setStatus("RUNNING");
        assertEquals("RUNNING", auction.getStatus());
    }

    @Test
    @DisplayName("setStatus: chuyển RUNNING → FINISHED thành công")
    void setStatus_runningToFinished_works() {
        auction.setStatus("RUNNING");
        auction.setStatus("FINISHED");
        assertEquals("FINISHED", auction.getStatus());
    }

    @Test
    @DisplayName("Khi FINISHED không có bid nào: highestBid là null")
    void finish_withNoBids_highestBidIsNull() {
        auction.setStatus("RUNNING");
        auction.setStatus("FINISHED");

        assertNull(auction.getHighestBid());
    }

    @Test
    @DisplayName("Khi FINISHED có bid: highestBid trỏ đúng người thắng")
    void finish_withBids_highestBidIsWinner() {
        auction.setStatus("RUNNING");
        auction.placeBid(bidder1, 150.0);
        auction.placeBid(bidder2, 250.0);
        auction.setStatus("FINISHED");

        assertEquals("bob", auction.getHighestBid().getBidder().getUsername());
    }

    // ================================================================
    // NHÓM 6 – getBidHistory trả về bản sao (defensive copy)
    // ================================================================

    @Test
    @DisplayName("getBidHistory trả về bản sao – không ảnh hưởng list nội bộ")
    void getBidHistory_returnsDefensiveCopy() {
        auction.setStatus("RUNNING");
        auction.placeBid(bidder1, 150.0);

        List<BidTransaction> copy = auction.getBidHistory();
        copy.clear(); // Cố tình xóa bản copy

        // List nội bộ không bị ảnh hưởng
        assertEquals(1, auction.getBidHistory().size());
    }

    // ================================================================
    // NHÓM 7 – Đa luồng (Concurrent Bidding)
    // ================================================================

    @Test
    @DisplayName("Concurrent: nhiều thread bid đồng thời – chỉ một giá thắng duy nhất, không mất dữ liệu")
    void placeBid_concurrent_noLostUpdate() throws InterruptedException {
        auction.setStatus("RUNNING");

        int threadCount = 10;
        // Mỗi thread bid với giá khác nhau: 110, 120, 130, ..., 200
        double[] bidAmounts = new double[threadCount];
        for (int i = 0; i < threadCount; i++) {
            bidAmounts[i] = STARTING_PRICE + (i + 1) * 10;
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);
        List<String> errors        = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final double amount = bidAmounts[i];
            final Bidder bidder = new Bidder("U_T" + i, "user" + i, "u" + i + "@t.com", "p");

            executor.submit(() -> {
                try {
                    startSignal.await(); // Chờ tín hiệu bắt đầu cùng lúc
                    auction.placeBid(bidder, amount);
                } catch (InvalidBidException | AuctionClosedException ignored) {
                    // Các bid bị từ chối (giá đã bị vượt qua) là hành vi đúng
                } catch (Exception e) {
                    errors.add(e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startSignal.countDown(); // Bắt đầu tất cả thread cùng lúc
        doneLatch.await();
        executor.shutdown();

        // Không có lỗi ngoài ý muốn
        assertTrue(errors.isEmpty(), "Lỗi không mong đợi: " + errors);

        // Giá cuối phải cao hơn giá khởi điểm
        assertTrue(auction.getCurrentPrice() > STARTING_PRICE,
            "Giá phải tăng sau khi bid");

        // Chỉ có đúng một người thắng tại mỗi thời điểm
        assertNotNull(auction.getHighestBid(), "Phải có highestBid sau khi bid");

        // Số lượng bid trong history phải >= 1 và <= threadCount
        int historySize = auction.getBidHistory().size();
        assertTrue(historySize >= 1 && historySize <= threadCount,
            "Số bid trong history không hợp lệ: " + historySize);
    }
}