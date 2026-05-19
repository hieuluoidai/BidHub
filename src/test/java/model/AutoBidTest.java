package model;

import model.auction.AutoBid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho AutoBid – kiểm tra model Auto-Bidding (yêu cầu 3.2.1).
 */
@DisplayName("AutoBid – kiểm tra model đấu giá tự động")
class AutoBidTest {

    private static final String AUTO_BID_ID = "AB_001";
    private static final String AUCTION_ID  = "AUC_001";
    private static final String USER_ID     = "U_001";
    private static final double MAX_BID     = 500.0;
    private static final double INCREMENT   = 10.0;

    private AutoBid autoBid;

    @BeforeEach
    void setUp() {
        autoBid = new AutoBid(AUTO_BID_ID, AUCTION_ID, USER_ID, MAX_BID, INCREMENT);
    }

    // ================================================================
    // NHÓM 1 — Constructor & Getters
    // ================================================================

    @Test
    @DisplayName("Constructor lưu đúng autoBidId")
    void constructor_storesAutoBidId() {
        assertEquals(AUTO_BID_ID, autoBid.getAutoBidId());
    }

    @Test
    @DisplayName("Constructor lưu đúng auctionId")
    void constructor_storesAuctionId() {
        assertEquals(AUCTION_ID, autoBid.getAuctionId());
    }

    @Test
    @DisplayName("Constructor lưu đúng userId")
    void constructor_storesUserId() {
        assertEquals(USER_ID, autoBid.getUserId());
    }

    @Test
    @DisplayName("Constructor lưu đúng maxBid")
    void constructor_storesMaxBid() {
        assertEquals(MAX_BID, autoBid.getMaxBid(), 0.001);
    }

    @Test
    @DisplayName("Constructor lưu đúng increment")
    void constructor_storesIncrement() {
        assertEquals(INCREMENT, autoBid.getIncrement(), 0.001);
    }

    @Test
    @DisplayName("Constructor tự động gán createdAt không null")
    void constructor_autoSetsCreatedAt() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        AutoBid ab = new AutoBid("AB_NEW", AUCTION_ID, USER_ID, MAX_BID, INCREMENT);
        assertNotNull(ab.getCreatedAt());
        assertTrue(ab.getCreatedAt().isAfter(before));
    }

    // ================================================================
    // NHÓM 2 — Setters
    // ================================================================

    @Test
    @DisplayName("setMaxBid cập nhật đúng giá tối đa mới")
    void setMaxBid_updatesValue() {
        autoBid.setMaxBid(750.0);
        assertEquals(750.0, autoBid.getMaxBid(), 0.001);
    }

    @Test
    @DisplayName("setIncrement cập nhật đúng bước giá mới")
    void setIncrement_updatesValue() {
        autoBid.setIncrement(25.0);
        assertEquals(25.0, autoBid.getIncrement(), 0.001);
    }

    @Test
    @DisplayName("setCreatedAt cập nhật đúng thời điểm tạo")
    void setCreatedAt_updatesValue() {
        LocalDateTime newTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
        autoBid.setCreatedAt(newTime);
        assertEquals(newTime, autoBid.getCreatedAt());
    }

    // ================================================================
    // NHÓM 3 — Nghiệp vụ Auto-Bidding
    // ================================================================

    @Test
    @DisplayName("Giá tối đa phải lớn hơn bước giá (điều kiện hợp lệ)")
    void maxBid_greaterThan_increment() {
        assertTrue(autoBid.getMaxBid() > autoBid.getIncrement(),
                "maxBid phải lớn hơn increment để có thể đặt giá");
    }

    @Test
    @DisplayName("Auto-bid với maxBid nhỏ: increment lớn hơn maxBid vẫn được lưu")
    void autoBid_incrementLargerThanMaxBid_stored() {
        AutoBid ab = new AutoBid("AB_EDGE", AUCTION_ID, USER_ID, 50.0, 100.0);
        assertEquals(50.0,  ab.getMaxBid(),     0.001);
        assertEquals(100.0, ab.getIncrement(),  0.001);
    }

    @Test
    @DisplayName("toString chứa thông tin autoBidId, userId, maxBid, increment")
    void toString_containsKeyInfo() {
        String s = autoBid.toString();
        assertTrue(s.contains(AUTO_BID_ID), "toString phải chứa autoBidId");
        assertTrue(s.contains(USER_ID),     "toString phải chứa userId");
        assertTrue(s.contains("500"),       "toString phải chứa maxBid");
        assertTrue(s.contains("10"),        "toString phải chứa increment");
    }

    @Test
    @DisplayName("Hai AutoBid khác nhau trên cùng phiên không xung đột ID")
    void twoAutoBids_sameAuction_differentIds() {
        AutoBid ab1 = new AutoBid("AB_001", AUCTION_ID, "U_001", 300.0, 10.0);
        AutoBid ab2 = new AutoBid("AB_002", AUCTION_ID, "U_002", 400.0, 20.0);

        assertNotEquals(ab1.getAutoBidId(), ab2.getAutoBidId());
        assertEquals(ab1.getAuctionId(), ab2.getAuctionId());
    }
}
