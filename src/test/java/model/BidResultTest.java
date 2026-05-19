package model;

import model.auction.BidResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho BidResult – kiểm tra các factory method và trạng thái kết quả bid.
 */
@DisplayName("BidResult – kiểm tra factory methods và trạng thái")
class BidResultTest {

    private static final String AUCTION_ID = "AUC_001";

    // ================================================================
    // NHÓM 1 — success()
    // ================================================================

    @Test
    @DisplayName("success(): isSuccess() = true, isOutbid() = false")
    void success_statusFlags_correct() {
        BidResult r = BidResult.success(AUCTION_ID, 200.0, "alice");
        assertTrue(r.isSuccess());
        assertFalse(r.isOutbid());
    }

    @Test
    @DisplayName("success(): getStatus() = SUCCESS")
    void success_getStatus_success() {
        BidResult r = BidResult.success(AUCTION_ID, 200.0, "alice");
        assertEquals(BidResult.Status.SUCCESS, r.getStatus());
    }

    @Test
    @DisplayName("success(): currentPrice = bidAmount")
    void success_currentPriceEqualsBidAmount() {
        BidResult r = BidResult.success(AUCTION_ID, 350.0, "alice");
        assertEquals(350.0, r.getBidAmount(),    0.001);
        assertEquals(350.0, r.getCurrentPrice(), 0.001);
    }

    @Test
    @DisplayName("success(): winnerUsername được lưu đúng")
    void success_storesWinnerUsername() {
        BidResult r = BidResult.success(AUCTION_ID, 200.0, "alice");
        assertEquals("alice", r.getWinnerUsername());
    }

    @Test
    @DisplayName("success(): message chứa số tiền đặt giá")
    void success_messageContainsAmount() {
        BidResult r = BidResult.success(AUCTION_ID, 200.0, "alice");
        assertTrue(r.getMessage().contains("200"));
    }

    // ================================================================
    // NHÓM 2 — outbid()
    // ================================================================

    @Test
    @DisplayName("outbid(): isOutbid() = true, isSuccess() = false")
    void outbid_statusFlags_correct() {
        BidResult r = BidResult.outbid(AUCTION_ID, 150.0, 200.0, "Bị vượt giá");
        assertTrue(r.isOutbid());
        assertFalse(r.isSuccess());
    }

    @Test
    @DisplayName("outbid(): getStatus() = OUTBID")
    void outbid_getStatus_outbid() {
        BidResult r = BidResult.outbid(AUCTION_ID, 150.0, 200.0, "Bị vượt giá");
        assertEquals(BidResult.Status.OUTBID, r.getStatus());
    }

    @Test
    @DisplayName("outbid(): bidAmount và currentPrice được lưu riêng đúng")
    void outbid_storesAmountsCorrectly() {
        BidResult r = BidResult.outbid(AUCTION_ID, 150.0, 200.0, "msg");
        assertEquals(150.0, r.getBidAmount(),    0.001);
        assertEquals(200.0, r.getCurrentPrice(), 0.001);
    }

    @Test
    @DisplayName("outbid(): winnerUsername là null")
    void outbid_winnerUsernameIsNull() {
        BidResult r = BidResult.outbid(AUCTION_ID, 150.0, 200.0, "msg");
        assertNull(r.getWinnerUsername());
    }

    // ================================================================
    // NHÓM 3 — failure()
    // ================================================================

    @Test
    @DisplayName("failure(): isSuccess() = false, isOutbid() = false")
    void failure_statusFlags_correct() {
        BidResult r = BidResult.failure(AUCTION_ID, 50.0, "Phiên đã đóng");
        assertFalse(r.isSuccess());
        assertFalse(r.isOutbid());
    }

    @Test
    @DisplayName("failure(): getStatus() = FAILURE")
    void failure_getStatus_failure() {
        BidResult r = BidResult.failure(AUCTION_ID, 50.0, "Lỗi");
        assertEquals(BidResult.Status.FAILURE, r.getStatus());
    }

    @Test
    @DisplayName("failure(): message được lưu đúng")
    void failure_storesReason() {
        BidResult r = BidResult.failure(AUCTION_ID, 50.0, "Phiên đã đóng");
        assertEquals("Phiên đã đóng", r.getMessage());
    }

    // ================================================================
    // NHÓM 4 — auctionId
    // ================================================================

    @Test
    @DisplayName("Tất cả factory methods lưu đúng auctionId")
    void allFactories_storeAuctionId() {
        assertEquals(AUCTION_ID, BidResult.success(AUCTION_ID, 200.0, "u").getAuctionId());
        assertEquals(AUCTION_ID, BidResult.outbid(AUCTION_ID, 100.0, 200.0, "m").getAuctionId());
        assertEquals(AUCTION_ID, BidResult.failure(AUCTION_ID, 50.0, "r").getAuctionId());
    }

    @Test
    @DisplayName("toString chứa status, auctionId, bidAmount")
    void toString_containsKeyInfo() {
        BidResult r = BidResult.success(AUCTION_ID, 200.0, "alice");
        String s = r.toString();
        assertTrue(s.contains("SUCCESS"));
        assertTrue(s.contains(AUCTION_ID));
        assertTrue(s.contains("200"));
    }
}
