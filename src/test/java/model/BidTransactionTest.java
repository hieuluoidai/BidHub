package model;

import model.auction.BidTransaction;
import model.user.Bidder;
import model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho BidTransaction – kiểm tra lớp giao dịch đặt giá.
 */
@DisplayName("BidTransaction – kiểm tra lớp giao dịch bid")
class BidTransactionTest {

    private User bidder;

    @BeforeEach
    void setUp() {
        bidder = new Bidder("U_001", "alice", "alice@test.com", "pass");
    }

    @Test
    @DisplayName("Tạo BidTransaction: lưu đúng bidder")
    void constructor_storesBidder() {
        BidTransaction bid = new BidTransaction(bidder, 500.0);
        assertEquals(bidder, bid.getBidder());
        assertEquals("alice", bid.getBidder().getUsername());
    }

    @Test
    @DisplayName("Tạo BidTransaction: lưu đúng bidAmount")
    void constructor_storesBidAmount() {
        BidTransaction bid = new BidTransaction(bidder, 750.5);
        assertEquals(750.5, bid.getBidAmount(), 0.001);
    }

    @Test
    @DisplayName("Tạo BidTransaction: tự động gán timestamp")
    void constructor_autoSetsTimestamp() {
        LocalDateTime before = LocalDateTime.now();
        BidTransaction bid = new BidTransaction(bidder, 500.0);
        LocalDateTime after = LocalDateTime.now();

        assertNotNull(bid.getTimestamp());
        assertFalse(bid.getTimestamp().isBefore(before));
        assertFalse(bid.getTimestamp().isAfter(after));
    }

    @Test
    @DisplayName("toString trả về chuỗi có chứa tên bidder và số tiền")
    void toString_containsBidderAndAmount() {
        BidTransaction bid = new BidTransaction(bidder, 500.0);
        String s = bid.toString();

        assertTrue(s.contains("alice"),    "Chuỗi phải chứa tên bidder");
        assertTrue(s.contains("500"),      "Chuỗi phải chứa số tiền");
    }

    @Test
    @DisplayName("toString với bidder null: không crash, hiển thị 'Ẩn danh'")
    void toString_nullBidder_showsAnonymous() {
        BidTransaction bid = new BidTransaction(null, 500.0);
        String s = bid.toString();

        assertTrue(s.contains("Ẩn danh"));
    }
}