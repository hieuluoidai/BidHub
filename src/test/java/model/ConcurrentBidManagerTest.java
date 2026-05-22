package model;

import database.AuctionDAO;
import database.AutoBidDAO;
import database.BidTransactionDAO;
import database.UserDAO;
import model.auction.Auction;
import model.auction.BidResult;
import model.auction.BidTransaction;
import model.item.Electronics;
import model.manager.AuctionManager;
import model.manager.ConcurrentBidManager;
import model.user.Bidder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ConcurrentBidManager – điều phối đặt giá")
class ConcurrentBidManagerTest {

    private ConcurrentBidManager manager;

    @BeforeEach
    void setUp() {
        manager = ConcurrentBidManager.getInstance();
        manager.resetMetrics();
        AuctionManager.getInstance().clearAll();
    }

    @Test
    @DisplayName("processBid: trả về lỗi nếu input không hợp lệ")
    void processBid_invalidInputs() {
        BidResult r1 = manager.processBid(null, 100, new Bidder("U1", "N", "E", "P"));
        assertEquals(BidResult.Status.FAILURE, r1.getStatus());
        assertEquals(exception.ErrorCode.VALIDATION_ERROR, r1.getErrorCode());

        BidResult r2 = manager.processBid("A1", -1, new Bidder("U1", "N", "E", "P"));
        assertEquals(BidResult.Status.FAILURE, r2.getStatus());
        assertEquals(exception.ErrorCode.VALIDATION_ERROR, r2.getErrorCode());

        BidResult r3 = manager.processBid("A1", 100, null);
        assertEquals(BidResult.Status.FAILURE, r3.getStatus());
        assertEquals(exception.ErrorCode.VALIDATION_ERROR, r3.getErrorCode());
    }

    @Test
    @DisplayName("processBid: trả về lỗi nếu auction không tồn tại")
    void processBid_auctionNotFound() {
        BidResult result = manager.processBid("NOT_EXIST", 100, new Bidder("U1", "N", "E", "P"));
        assertEquals(BidResult.Status.FAILURE, result.getStatus());
        assertEquals(exception.ErrorCode.AUCTION_NOT_FOUND, result.getErrorCode());
    }

    @Test
    @DisplayName("processBid: trả về lỗi nếu auction không trong trạng thái RUNNING")
    void processBid_notRunning() {
        String id = "AUC_CLOSED";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now(), LocalDateTime.now());
        auction.setStatus("FINISHED");
        AuctionManager.getInstance().addAuction(auction);

        BidResult result = manager.processBid(id, 200, new Bidder("U1", "N", "E", "P"));
        assertEquals(BidResult.Status.FAILURE, result.getStatus());
        assertEquals(exception.ErrorCode.AUCTION_NOT_RUNNING, result.getErrorCode());
    }

    @Test
    @DisplayName("processBid: trả về OUTBID nếu giá đặt thấp hơn hoặc bằng giá hiện tại")
    void processBid_outbid() {
        String id = "AUC_OUTBID";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        BidResult result = manager.processBid(id, 50, new Bidder("U1", "N", "E", "P"));
        assertEquals("OUTBID", result.getStatus().name());
    }

    @Test
    @DisplayName("processBid: thành công và khóa tiền người mới, mở khóa người cũ")
    void processBid_success_locksAndUnlocks() {
        String id = "AUC_OK";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        
        // Previous bidder: Bob at 150
        Bidder bob = new Bidder("U_BOB", "bob", "b@t.com", "p");
        auction.placeBid(bob, 150);
        
        AuctionManager.getInstance().addAuction(auction);

        Bidder alice = new Bidder("U_ALICE", "alice", "a@t.com", "p");
        BidTransactionDAO bidDao = mock(BidTransactionDAO.class);

        // Mock DAOs used inside the method via mockConstruction
        try (MockedConstruction<UserDAO> userDAOConstruction = mockConstruction(UserDAO.class, (mock, context) -> {
                 when(mock.lockBalance("U_ALICE", 200.0)).thenReturn(true);
             });
             MockedConstruction<AutoBidDAO> autoBidDAOConstruction = mockConstruction(AutoBidDAO.class, (mock, context) -> {
                 when(mock.findByUserAndAuction(anyString(), anyString())).thenReturn(null);
             })
        ) {
            BidResult result = manager.processBid(id, 200, alice, bidDao);

            assertEquals("SUCCESS", result.getStatus().name());
            assertEquals(200.0, auction.getCurrentPrice());
            assertEquals("U_ALICE", auction.getHighestBid().getBidder().getUserId());

            // Verify UserDAO interactions
            UserDAO mockedUserDAO = userDAOConstruction.constructed().get(0);
            verify(mockedUserDAO).lockBalance("U_ALICE", 200.0);
            verify(mockedUserDAO).unlockBalance("U_BOB", 150.0);
            
            // Verify BidDAO interaction
            verify(bidDao).save(eq(id), eq("U_ALICE"), eq(200.0), any(), any(), anyBoolean(), any());
        }
    }

    @Test
    @DisplayName("processBid: người đang dẫn đầu nâng giá, chỉ khóa phần chênh lệch")
    void processBid_sameBidder_raising() {
        String id = "AUC_SAME";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        
        Bidder alice = new Bidder("U_ALICE", "alice", "a@t.com", "p");
        auction.placeBid(alice, 150);
        AuctionManager.getInstance().addAuction(auction);

        try (MockedConstruction<UserDAO> userDAOConstruction = mockConstruction(UserDAO.class, (mock, context) -> {
                 when(mock.lockBalance("U_ALICE", 50.0)).thenReturn(true); // 200 - 150 = 50
             });
             MockedConstruction<AutoBidDAO> autoBidDAOConstruction = mockConstruction(AutoBidDAO.class)
        ) {
            BidResult result = manager.processBid(id, 200, alice, mock(BidTransactionDAO.class));

            assertEquals(BidResult.Status.SUCCESS, result.getStatus());
            UserDAO mockedUserDAO = userDAOConstruction.constructed().get(0);
            verify(mockedUserDAO).lockBalance("U_ALICE", 50.0);
        }
    }

    @Test
    @DisplayName("processBid: thất bại nếu không đủ số dư")
    void processBid_insufficientBalance() {
        String id = "AUC_POOR";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        Bidder alice = new Bidder("U_ALICE", "alice", "a@t.com", "p");

        try (MockedConstruction<UserDAO> userDAOConstruction = mockConstruction(UserDAO.class, (mock, context) -> {
                 when(mock.lockBalance("U_ALICE", 200.0)).thenReturn(false);
             });
             MockedConstruction<AutoBidDAO> autoBidDAOConstruction = mockConstruction(AutoBidDAO.class)
        ) {
            BidResult result = manager.processBid(id, 200, alice, mock(BidTransactionDAO.class));

            assertEquals(BidResult.Status.FAILURE, result.getStatus());
            assertEquals(exception.ErrorCode.INSUFFICIENT_BALANCE, result.getErrorCode());
        }
    }

    @Test
    @DisplayName("restorePersistedHistoryIfNeeded: nạp lại lịch sử nếu RAM trống")
    void restoreHistory() {
        String id = "AUC_RESTORE";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now(), LocalDateTime.now());
        // history is empty
        
        BidTransactionDAO bidDao = mock(BidTransactionDAO.class);
        java.util.List<BidTransaction> history = new java.util.ArrayList<>();
        history.add(new BidTransaction(new Bidder("U1", "N", "E", "P"), 150.0, LocalDateTime.now(), BidTransaction.BidType.MANUAL));
        when(bidDao.findTransactionsByAuctionId(id)).thenReturn(history);

        AuctionManager.getInstance().addAuction(auction);
        
        manager.processBid(id, 200, new Bidder("U2", "N", "E", "P"), bidDao);
        
        assertEquals(150.0, auction.getCurrentPrice()); 
    }

    @Test
    @DisplayName("metrics: kiểm tra đếm số lượng thành công/thất bại")
    void metrics() {
        manager.resetMetrics();
        assertEquals(0, manager.getSuccessCount());
        
        // Trigger a failure
        manager.processBid(null, 0, null);
        assertEquals(1, manager.getFailureCount());
        
        // Trigger an outbid
        String id = "AUC_M";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 500, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);
        manager.processBid(id, 100, new Bidder("U1", "N", "E", "P"));
        assertEquals(1, manager.getOutbidCount());
    }

    @Test
    @DisplayName("releaseLock: giải phóng tài nguyên lock")
    void releaseLock() {
        String id = "AUC_LOCK";
        manager.processBid(id, 100, new Bidder("U1", "N", "E", "P"));
        assertTrue(manager.activeLockCount() >= 1);
        
        manager.releaseLock(id);
    }

    @Test
    @DisplayName("processBid: anti-sniping tự động gia hạn khi bid phút chót")
    void processBid_antiSniping() {
        String id = "AUC_SNIPE";
        LocalDateTime endTime = LocalDateTime.now().plusSeconds(5); // Less than 10s
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), endTime);
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        Bidder alice = new Bidder("U_ALICE", "alice", "a@t.com", "p");
        BidTransactionDAO bidDao = mock(BidTransactionDAO.class);

        try (MockedConstruction<UserDAO> userDAOConstruction = mockConstruction(UserDAO.class, (mock, context) -> {
                 when(mock.lockBalance(anyString(), anyDouble())).thenReturn(true);
             });
             MockedConstruction<AutoBidDAO> autoBidDAOConstruction = mockConstruction(AutoBidDAO.class);
             MockedConstruction<AuctionDAO> auctionDAOConstruction = mockConstruction(AuctionDAO.class, (mock, context) -> {
                 when(mock.updateEndTime(anyString(), any())).thenReturn(true);
             })
        ) {
            manager.processBid(id, 200, alice, bidDao);

            // Verify end time extended (usually by 60s)
            assertTrue(auction.getEndTime().isAfter(endTime));
            AuctionDAO mockedAuctionDAO = auctionDAOConstruction.constructed().get(0);
            verify(mockedAuctionDAO).updateEndTime(eq(id), any(LocalDateTime.class));
        }
    }

    @Test
    @DisplayName("processBid: tự động hủy Auto-Bid nếu Manual Bid cao hơn MaxBid")
    void processBid_manualBidExceedsAutoBid_cancelsAutoBid() {
        String id = "AUC_AUTO_CANCEL";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        Bidder alice = new Bidder("U_ALICE", "alice", "a@t.com", "p");
        model.manager.AutoBidManager mockedAutoBidManager = mock(model.manager.AutoBidManager.class);

        try (org.mockito.MockedStatic<model.manager.AutoBidManager> abmStatic = mockStatic(model.manager.AutoBidManager.class);
             MockedConstruction<AutoBidDAO> autoBidDAOConstruction = mockConstruction(AutoBidDAO.class, (mock, context) -> {
                 when(mock.findByUserAndAuction("U_ALICE", id))
                     .thenReturn(new model.auction.AutoBid("AB1", id, "U_ALICE", 1500.0, 50.0, false));
             });
             MockedConstruction<UserDAO> userDAOConstruction = mockConstruction(UserDAO.class, (mock, context) -> {
                 when(mock.lockBalance(anyString(), anyDouble())).thenReturn(true);
             })
        ) {
            abmStatic.when(model.manager.AutoBidManager::getInstance).thenReturn(mockedAutoBidManager);
            
            BidResult result = manager.processBid(id, 1600.0, alice, mock(BidTransactionDAO.class));

            assertEquals(BidResult.Status.SUCCESS, result.getStatus());
            verify(mockedAutoBidManager).cancelAutoBid("U_ALICE", id);
        }
    }

    @Test
    @DisplayName("processBid: KHÔNG hủy Auto-Bid nếu Manual Bid thấp hơn hoặc bằng MaxBid")
    void processBid_manualBidLowerThanAutoBid_keepsAutoBid() {
        String id = "AUC_AUTO_KEEP";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);

        Bidder alice = new Bidder("U_ALICE", "alice", "a@t.com", "p");
        model.manager.AutoBidManager mockedAutoBidManager = mock(model.manager.AutoBidManager.class);

        try (org.mockito.MockedStatic<model.manager.AutoBidManager> abmStatic = mockStatic(model.manager.AutoBidManager.class);
             MockedConstruction<AutoBidDAO> autoBidDAOConstruction = mockConstruction(AutoBidDAO.class, (mock, context) -> {
                 when(mock.findByUserAndAuction("U_ALICE", id))
                     .thenReturn(new model.auction.AutoBid("AB1", id, "U_ALICE", 3500.0, 50.0, false));
             });
             MockedConstruction<UserDAO> userDAOConstruction = mockConstruction(UserDAO.class, (mock, context) -> {
                 when(mock.lockBalance(anyString(), anyDouble())).thenReturn(true);
             })
        ) {
            abmStatic.when(model.manager.AutoBidManager::getInstance).thenReturn(mockedAutoBidManager);
            
            BidResult result = manager.processBid(id, 200.0, alice, mock(BidTransactionDAO.class));

            assertEquals(BidResult.Status.SUCCESS, result.getStatus());
            verify(mockedAutoBidManager, never()).cancelAutoBid(anyString(), anyString());
        }
    }

    @Test
    @DisplayName("processBid: giải phóng toàn bộ MaxBid của người cũ (nếu có AutoBid) khi bị outbid")
    void processBid_outbid_unlocksMaxAmount() {
        String id = "AUC_UNLOCK_MAX";
        Auction auction = new Auction(id, new Electronics("I1", "N", "D", 100, "B"), LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        
        Bidder bob = new Bidder("U_BOB", "bob", "b@t.com", "p");
        auction.placeBid(bob, 150);
        
        AuctionManager.getInstance().addAuction(auction);

        Bidder alice = new Bidder("U_ALICE", "alice", "a@t.com", "p");

        try (MockedConstruction<AutoBidDAO> autoBidDAOConstruction = mockConstruction(AutoBidDAO.class, (mock, context) -> {
                 when(mock.findByUserAndAuction("U_BOB", id))
                     .thenReturn(new model.auction.AutoBid("AB_BOB", id, "U_BOB", 1000.0, 50.0, false));
             });
             MockedConstruction<UserDAO> userDAOConstruction = mockConstruction(UserDAO.class, (mock, context) -> {
                 when(mock.lockBalance(anyString(), anyDouble())).thenReturn(true);
             })
        ) {
            BidResult result = manager.processBid(id, 200.0, alice, mock(BidTransactionDAO.class));

            assertEquals(BidResult.Status.SUCCESS, result.getStatus());
            UserDAO mockedUserDAO = userDAOConstruction.constructed().get(0);
            
            verify(mockedUserDAO).unlockBalance("U_BOB", 1000.0);
        }
    }
}
