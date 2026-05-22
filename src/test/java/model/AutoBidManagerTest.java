package model;

import database.AutoBidDAO;
import database.BidTransactionDAO;
import database.UserDAO;
import model.auction.Auction;
import model.auction.AutoBid;
import model.item.Electronics;
import model.item.Item;
import model.manager.AuctionManager;
import model.manager.AutoBidManager;
import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AutoBidManager – logic đấu giá tự động")
class AutoBidManagerTest {

    private AutoBidManager manager;
    private AutoBidDAO autoBidDAO;
    private UserDAO userDAO;
    private BidTransactionDAO bidTransactionDAO;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton
        Field instanceField = AutoBidManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Mock DAOs
        autoBidDAO = mock(AutoBidDAO.class);
        userDAO = mock(UserDAO.class);
        bidTransactionDAO = mock(BidTransactionDAO.class);

        // Inject mocks via reflection
        manager = AutoBidManager.getInstance();
        setPrivateField(manager, "autoBidDAO", autoBidDAO);
        setPrivateField(manager, "userDAO", userDAO);

        // Clear AuctionManager
        AuctionManager.getInstance().clearAll();
    }

    private void setPrivateField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Test
    @DisplayName("registerAutoBid: trả về USER_NOT_FOUND nếu user không tồn tại")
    void registerAutoBid_userNotFound() {
        when(userDAO.findById("U001")).thenReturn(null);
        String result = manager.registerAutoBid("U001", "A001", 1000, 50);
        assertEquals("USER_NOT_FOUND", result);
    }

    @Test
    @DisplayName("registerAutoBid: trả về INSUFFICIENT_BALANCE nếu không đủ tiền")
    void registerAutoBid_insufficientBalance() {
        Bidder user = new Bidder("U001", "alice", "alice@test.com", "pass");
        user.setBalance(500.0);
        when(userDAO.findById("U001")).thenReturn(user);
        when(userDAO.getBalance("U001")).thenReturn(500.0);

        String result = manager.registerAutoBid("U001", "A001", 1000, 50);
        assertEquals("INSUFFICIENT_BALANCE", result);
    }

    @Test
    @DisplayName("registerAutoBid: thành công và khóa tiền")
    void registerAutoBid_success() {
        Bidder user = new Bidder("U001", "alice", "alice@test.com", "pass");
        user.setBalance(2000.0);
        when(userDAO.findById("U001")).thenReturn(user);
        when(userDAO.getBalance("U001")).thenReturn(2000.0);
        when(userDAO.lockBalance(eq("U001"), anyDouble())).thenReturn(true);
        when(autoBidDAO.save(any(AutoBid.class))).thenReturn(true);

        String result = manager.registerAutoBid("U001", "A001", 1000, 50);
        assertEquals("SUCCESS", result);
        verify(userDAO).lockBalance("U001", 1000.0);
        verify(autoBidDAO).save(any(AutoBid.class));
    }

    @Test
    @DisplayName("executeAutoBids: kích hoạt nhảy giá khi có AutoBid")
    void executeAutoBids_singleAutoBid_jumpsPrice() {
        // 1. Setup Auction
        String auctionId = "AUC_001";
        Item item = new Electronics("I001", "Laptop", "desc", 100, "Dell");
        Auction auction = new Auction(auctionId, item, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        // 2. Setup AutoBid in memory (inject via reflection to bypass DB loading in constructor)
        AutoBid ab = new AutoBid("AB001", auctionId, "U001", 1000, 50);
        List<AutoBid> list = new ArrayList<>();
        list.add(ab);
        try {
            java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>> auctionAutoBids = 
                (java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>>) getPrivateField(manager, "auctionAutoBids");
            auctionAutoBids.put(auctionId, list);
        } catch (Exception e) { fail(e); }

        // 3. Setup User Mock
        Bidder user = new Bidder("U001", "alice", "alice@test.com", "pass");
        user.setBalance(2000.0);
        when(userDAO.findById("U001")).thenReturn(user);

        // 4. Execute
        manager.executeAutoBids(auctionId, bidTransactionDAO);

        // 5. Verify: Price should jump from 100 to 150 (Current + Increment)
        assertEquals(150.0, auction.getCurrentPrice());
        assertEquals("U001", auction.getHighestBid().getBidder().getUserId());
        verify(bidTransactionDAO).save(eq(auctionId), eq("U001"), eq(150.0), any(), any());
    }

    @Test
    @DisplayName("executeAutoBids: cuộc đua giữa 2 AutoBidders")
    void executeAutoBids_twoAutoBidders_war() {
        String auctionId = "AUC_WAR";
        Item item = new Electronics("I001", "Laptop", "desc", 100, "Dell");
        Auction auction = new Auction(auctionId, item, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        // Alice: Max 500, Inc 50
        // Bob: Max 800, Inc 100
        AutoBid aliceAB = new AutoBid("AB_A", auctionId, "U_ALICE", 500, 50);
        AutoBid bobAB = new AutoBid("AB_B", auctionId, "U_BOB", 800, 100);
        
        List<AutoBid> list = new ArrayList<>();
        list.add(aliceAB);
        list.add(bobAB);
        try {
            java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>> auctionAutoBids = 
                (java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>>) getPrivateField(manager, "auctionAutoBids");
            auctionAutoBids.put(auctionId, list);
        } catch (Exception e) { fail(e); }

        when(userDAO.findById("U_ALICE")).thenReturn(new Bidder("U_ALICE", "alice", "a@t.com", "p"));
        when(userDAO.findById("U_BOB")).thenReturn(new Bidder("U_BOB", "bob", "b@t.com", "p"));

        // Execute
        manager.executeAutoBids(auctionId, bidTransactionDAO);

        // Result: Bob should win. 
        // Alice max is 500. Bob should jump to 500 + 100 = 600.
        
        assertTrue(auction.getCurrentPrice() >= 500);
        assertEquals("U_BOB", auction.getHighestBid().getBidder().getUserId());
        
        // Alice should be unlocked and deleted from DB
        verify(userDAO).unlockBalance("U_ALICE", 500.0);
        verify(autoBidDAO).delete("AB_A");
    }

    @Test
    @DisplayName("cancelAutoBid: thành công giải phóng tiền")
    void cancelAutoBid_success() {
        String auctionId = "AUC_CANCEL";
        String userId = "U_CANCEL";
        AutoBid ab = new AutoBid("AB001", auctionId, userId, 1000, 50);
        when(autoBidDAO.findByUserAndAuction(userId, auctionId)).thenReturn(ab);
        when(autoBidDAO.deleteByUserAndAuction(userId, auctionId)).thenReturn(true);

        boolean result = manager.cancelAutoBid(userId, auctionId);

        assertTrue(result);
        verify(userDAO).unlockBalance(userId, 1000.0);
        verify(autoBidDAO).deleteByUserAndAuction(userId, auctionId);
    }

    @Test
    @DisplayName("cleanup: giải phóng tiền cho người thắng (phần dư) và người thua (tất cả)")
    void cleanup_winnerAndLosers() {
        String auctionId = "AUC_CLEAN";
        
        // Alice wins at 500, Max was 1000. Should unlock 500.
        AutoBid aliceAB = new AutoBid("AB_A", auctionId, "U_ALICE", 1000, 50);
        // Bob loses, Max was 800. Should unlock 800.
        AutoBid bobAB = new AutoBid("AB_B", auctionId, "U_BOB", 800, 100);
        
        List<AutoBid> list = new ArrayList<>();
        list.add(aliceAB);
        list.add(bobAB);
        try {
            java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>> auctionAutoBids = 
                (java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>>) getPrivateField(manager, "auctionAutoBids");
            auctionAutoBids.put(auctionId, list);
        } catch (Exception e) { fail(e); }

        // Setup Auction with Alice as winner
        Item item = new Electronics("I001", "L", "d", 100, "B");
        Auction auction = new Auction(auctionId, item, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        auction.placeBid(new Bidder("U_ALICE", "alice", "a@t.com", "p"), 500);
        AuctionManager.getInstance().addAuction(auction);

        manager.cleanup(auctionId);

        verify(userDAO).unlockBalance("U_ALICE", 500.0);
        verify(userDAO).unlockBalance("U_BOB", 800.0);
        verify(autoBidDAO).delete("AB_A");
        verify(autoBidDAO).delete("AB_B");
    }

    @Test
    @DisplayName("registerAutoBid: cập nhật AutoBid cũ, phải mở khóa tiền cũ")
    void registerAutoBid_updateExisting() {
        String userId = "U001";
        String auctionId = "A001";
        AutoBid existing = new AutoBid("AB_OLD", auctionId, userId, 500, 50);
        when(autoBidDAO.findByUserAndAuction(userId, auctionId)).thenReturn(existing);
        
        Bidder user = new Bidder(userId, "alice", "a@t.com", "p");
        user.setBalance(2000.0);
        when(userDAO.findById(userId)).thenReturn(user);
        when(userDAO.getBalance(userId)).thenReturn(2000.0);
        when(userDAO.lockBalance(eq(userId), anyDouble())).thenReturn(true);
        when(autoBidDAO.save(any(AutoBid.class))).thenReturn(true);

        manager.registerAutoBid(userId, auctionId, 1000, 100);

        // Verify old was unlocked
        verify(userDAO).unlockBalance(userId, 500.0);
        // Verify new was locked
        verify(userDAO).lockBalance(userId, 1000.0);
    }

    @Test
    @DisplayName("executeAutoBids: không làm gì nếu phiên không RUNNING")
    void executeAutoBids_notRunning() {
        String auctionId = "AUC_CLOSED";
        Auction auction = new Auction(auctionId, null, LocalDateTime.now(), LocalDateTime.now());
        auction.setStatus("FINISHED");
        AuctionManager.getInstance().addAuction(auction);

        manager.executeAutoBids(auctionId, bidTransactionDAO);
        verifyNoInteractions(bidTransactionDAO);
    }

    @Test
    @DisplayName("executeAutoBids: loại bỏ AutoBid đã bị vượt quá MaxBid")
    void executeAutoBids_retireExhausted() {
        String auctionId = "AUC_EXHAUST";
        Auction auction = new Auction(auctionId, new Electronics("I","N","D", 1000, "B"), LocalDateTime.now(), LocalDateTime.now());
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        AutoBid exhausted = new AutoBid("AB_EX", auctionId, "U_EX", 500, 50); // Max 500 < Current 1000
        List<AutoBid> list = new ArrayList<>();
        list.add(exhausted);
        try {
            java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>> auctionAutoBids = 
                (java.util.concurrent.ConcurrentHashMap<String, List<AutoBid>>) getPrivateField(manager, "auctionAutoBids");
            auctionAutoBids.put(auctionId, list);
        } catch (Exception e) { fail(e); }

        manager.executeAutoBids(auctionId, bidTransactionDAO);

        verify(userDAO).unlockBalance("U_EX", 500.0);
        verify(autoBidDAO).delete("AB_EX");
    }

    private Object getPrivateField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
}
