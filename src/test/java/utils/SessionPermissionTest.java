package utils;

import database.AuctionDAO;
import model.auction.Auction;
import model.manager.AppState;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionPermissionTest {

    private Auction auction;
    private AppState appStateMock;

    @BeforeEach
    void setUp() {
        auction = mock(Auction.class);
        appStateMock = mock(AppState.class);
        // Sử dụng reflection để gán appStateMock nếu cần, hoặc mock static nếu AppState là singleton
        // AppState có getInstance() và là singleton.
    }

    @AfterEach
    void tearDown() {
        SessionPermission.invalidateCache();
    }

    @Test
    void testCanEditAdmin() {
        try (var mockedAppState = mockStatic(AppState.class)) {
            mockedAppState.when(AppState::getInstance).thenReturn(appStateMock);
            when(appStateMock.getCurrentUser()).thenReturn(new Admin("a1", "admin", "a@ex.com", "p"));
            when(auction.getStatus()).thenReturn("OPEN");

            assertTrue(SessionPermission.canEdit(auction));
        }
    }

    @Test
    void testCanEditSellerOwned() {
        try (var mockedAppState = mockStatic(AppState.class)) {
            mockedAppState.when(AppState::getInstance).thenReturn(appStateMock);
            when(appStateMock.getCurrentUser()).thenReturn(new Seller("s1", "seller", "s@ex.com", "p"));
            when(auction.getStatus()).thenReturn("OPEN");
            when(auction.getAuctionId()).thenReturn("auc1");

            Set<String> ownedIds = new HashSet<>();
            ownedIds.add("auc1");

            try (MockedConstruction<AuctionDAO> mockedDAO = mockConstruction(AuctionDAO.class, (mock, context) -> {
                when(mock.getAuctionIdsBySeller("s1")).thenReturn(ownedIds);
            })) {
                assertTrue(SessionPermission.canEdit(auction));
            }
        }
    }

    @Test
    void testCanEditSellerNotOwned() {
        try (var mockedAppState = mockStatic(AppState.class)) {
            mockedAppState.when(AppState::getInstance).thenReturn(appStateMock);
            when(appStateMock.getCurrentUser()).thenReturn(new Seller("s1", "seller", "s@ex.com", "p"));
            when(auction.getStatus()).thenReturn("OPEN");
            when(auction.getAuctionId()).thenReturn("auc2");

            Set<String> ownedIds = new HashSet<>();
            ownedIds.add("auc1");

            try (MockedConstruction<AuctionDAO> mockedDAO = mockConstruction(AuctionDAO.class, (mock, context) -> {
                when(mock.getAuctionIdsBySeller("s1")).thenReturn(ownedIds);
            })) {
                assertFalse(SessionPermission.canEdit(auction));
            }
        }
    }

    @Test
    void testCanEditBidder() {
        try (var mockedAppState = mockStatic(AppState.class)) {
            mockedAppState.when(AppState::getInstance).thenReturn(appStateMock);
            when(appStateMock.getCurrentUser()).thenReturn(new Bidder("b1", "bidder", "b@ex.com", "p"));
            when(auction.getStatus()).thenReturn("OPEN");

            assertFalse(SessionPermission.canEdit(auction));
        }
    }

    @Test
    void testCanCancelRunningAdmin() {
        try (var mockedAppState = mockStatic(AppState.class)) {
            mockedAppState.when(AppState::getInstance).thenReturn(appStateMock);
            when(appStateMock.getCurrentUser()).thenReturn(new Admin("a1", "admin", "a@ex.com", "p"));
            when(auction.getStatus()).thenReturn("RUNNING");

            assertTrue(SessionPermission.canCancel(auction));
        }
    }

    @Test
    void testCanPayWinner() {
        try (var mockedAppState = mockStatic(AppState.class)) {
            mockedAppState.when(AppState::getInstance).thenReturn(appStateMock);
            when(appStateMock.getCurrentUser()).thenReturn(new Bidder("b1", "bidder", "b@ex.com", "p"));
            when(auction.getStatus()).thenReturn("FINISHED");

            assertTrue(SessionPermission.canPay(auction, "b1"));
            assertFalse(SessionPermission.canPay(auction, "b2"));
        }
    }
}
