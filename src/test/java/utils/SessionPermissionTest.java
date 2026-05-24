package utils;

import database.BaseDAOTest;
import database.UserDAO;
import database.ItemDAO;
import database.AuctionDAO;
import model.auction.Auction;
import model.item.Electronics;
import model.manager.AppState;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SessionPermissionTest extends BaseDAOTest {

    private AppState mockAppState;
    private MockedStatic<AppState> appStateStatic;

    @BeforeEach
    public void setUp() {
        mockAppState = mock(AppState.class);
        appStateStatic = mockStatic(AppState.class);
        appStateStatic.when(AppState::getInstance).thenReturn(mockAppState);
        SessionPermission.invalidateCache();
    }

    @AfterEach
    public void tearDown() {
        appStateStatic.close();
    }

    @Test
    public void testAdminPermissions() {
        when(mockAppState.getCurrentUser()).thenReturn(new Admin("a1", "admin", "admin@test.com", "pass"));
        Auction auction = new Auction("auc1", null, null, null);
        auction.setStatus("RUNNING");

        assertTrue(SessionPermission.canDelete(auction));
        assertTrue(SessionPermission.canCancel(auction));
        // Edit only if OPEN
        assertFalse(SessionPermission.canEdit(auction));
        auction.setStatus("OPEN");
        assertTrue(SessionPermission.canEdit(auction));
    }

    @Test
    public void testSellerPermissions() {
        String sellerId = "s1";
        Seller seller = new Seller(sellerId, "Seller", "s@test.com", "pass");
        when(mockAppState.getCurrentUser()).thenReturn(seller);
        
        // Prepare real data in H2
        UserDAO userDAO = new UserDAO();
        userDAO.save(seller);
        
        ItemDAO itemDAO = new ItemDAO();
        Electronics item = new Electronics("i1", "Item", "Desc", 100.0, "Brand");
        itemDAO.save(item, sellerId);
        
        AuctionDAO auctionDAO = new AuctionDAO();
        Auction auction = new Auction("auc1", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auction.setStatus("OPEN");
        auctionDAO.save(auction);

        // Test with real DB backing
        assertTrue(SessionPermission.canEdit(auction));
        assertTrue(SessionPermission.canDelete(auction));
        assertTrue(SessionPermission.canCancel(auction));
        
        auction.setStatus("FINISHED");
        assertFalse(SessionPermission.canEdit(auction));
        assertFalse(SessionPermission.canDelete(auction));
    }

    @Test
    public void testBidderPermissions() {
        when(mockAppState.getCurrentUser()).thenReturn(new Bidder("b1", "Bidder", "b@test.com", "pass"));
        Auction auction = new Auction("auc1", null, null, null);
        auction.setStatus("OPEN");

        assertFalse(SessionPermission.canEdit(auction));
        assertFalse(SessionPermission.canDelete(auction));
        assertFalse(SessionPermission.canCancel(auction));
    }
}
