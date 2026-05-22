package database;

import model.auction.Auction;
import model.item.Electronics;
import model.item.Item;
import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuctionDAOTest extends BaseDAOTest {

    private AuctionDAO auctionDAO;
    private ItemDAO itemDAO;
    private UserDAO userDAO;
    private BidTransactionDAO bidDAO;

    @BeforeEach
    void setUp() {
        auctionDAO = new AuctionDAO();
        itemDAO = new ItemDAO();
        userDAO = new UserDAO();
        bidDAO = new BidTransactionDAO();

        // Setup base data
        userDAO.save(new Bidder("s-001", "seller1", "s1@ex.com", "p"));
        userDAO.save(new Bidder("b-001", "bidder1", "b1@ex.com", "p"));
        itemDAO.save(new Electronics("i-001", "Item 1", "Desc 1", 1000.0, "Brand 1"), "s-001");
    }

    @Test
    void testSaveAndFindById() {
        Item item = itemDAO.findById("i-001");
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusHours(2);
        Auction auction = new Auction("a-001", item, start, end);
        
        boolean saved = auctionDAO.save(auction);
        assertTrue(saved);

        Auction found = auctionDAO.findById("a-001");
        assertNotNull(found);
        assertEquals("a-001", found.getAuctionId());
        assertEquals("OPEN", found.getStatus());
        // H2 might have slight precision difference in nanos, but LocalDateTime.now() should be fine if compared with a margin or using truncatedTo
        // Or just check if it's roughly the same
        assertTrue(found.getStartTime().isAfter(LocalDateTime.now()));
    }

    @Test
    void testUpdateStatus() {
        Item item = itemDAO.findById("i-001");
        Auction auction = new Auction("a-002", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auctionDAO.save(auction);

        boolean updated = auctionDAO.updateStatus("a-002", "RUNNING");
        assertTrue(updated);

        assertEquals("RUNNING", auctionDAO.findById("a-002").getStatus());
    }

    @Test
    void testFindWinnerInfo() {
        Item item = itemDAO.findById("i-001");
        Auction auction = new Auction("a-003", item, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        auction.setStatus("FINISHED");
        auctionDAO.save(auction);

        // Add some bids
        bidDAO.save("a-003", "b-001", 1100.0);
        
        String[] winnerInfo = auctionDAO.findWinnerInfo("a-003");
        assertNotNull(winnerInfo);
        assertEquals("b-001", winnerInfo[0]); // winner_id
        assertEquals("s-001", winnerInfo[1]); // seller_id
        assertEquals("1100.0", winnerInfo[2]); // final_price
    }

    @Test
    void testCancelAuction() {
        Item item = itemDAO.findById("i-001");
        Auction auction = new Auction("a-004", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auctionDAO.save(auction);

        // Try cancel by non-owner, non-admin
        int result = auctionDAO.cancelAuction("a-004", "b-001", false);
        assertEquals(0, result);

        // Cancel by owner
        result = auctionDAO.cancelAuction("a-004", "s-001", false);
        assertEquals(1, result);
        assertEquals("CANCELED", auctionDAO.findById("a-004").getStatus());
    }
}
