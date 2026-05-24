package database;

import model.auction.AutoBid;
import model.auction.Auction;
import model.item.Electronics;
import model.user.Bidder;
import model.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AutoBidDAOTest extends BaseDAOTest {
    private AutoBidDAO autoBidDAO;
    private UserDAO userDAO;
    private ItemDAO itemDAO;
    private AuctionDAO auctionDAO;
    
    private final String userId = "u-autobid";
    private final String auctionId = "a-autobid";

    @BeforeEach
    public void setUp() {
        autoBidDAO = new AutoBidDAO();
        userDAO = new UserDAO();
        itemDAO = new ItemDAO();
        auctionDAO = new AuctionDAO();

        userDAO.save(new Bidder(userId, "AutoBidder", "auto@test.com", "pass"));
        userDAO.save(new Seller("s-1", "Seller", "seller@test.com", "pass"));
        
        Electronics item = new Electronics("i-1", "Phone", "Desc", 100.0, "Apple");
        itemDAO.save(item, "s-1");
        
        Auction a = new Auction(auctionId, item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auctionDAO.save(a);
    }

    @Test
    public void testSaveAndDelete() {
        AutoBid ab = new AutoBid("ab-1", auctionId, userId, 1000.0, 10.0, false);
        ab.setCreatedAt(LocalDateTime.now());
        
        assertTrue(autoBidDAO.save(ab));
        
        AutoBid found = autoBidDAO.findByUserAndAuction(userId, auctionId);
        assertNotNull(found);
        assertEquals(1000.0, found.getMaxBid());

        assertTrue(autoBidDAO.delete("ab-1"));
        assertNull(autoBidDAO.findByUserAndAuction(userId, auctionId));
    }

    @Test
    public void testFindAllAndByAuction() {
        AutoBid ab = new AutoBid("ab-1", auctionId, userId, 1000.0, 10.0, false);
        ab.setCreatedAt(LocalDateTime.now());
        autoBidDAO.save(ab);

        List<AutoBid> all = autoBidDAO.findAll();
        assertFalse(all.isEmpty());

        List<AutoBid> byAuction = autoBidDAO.findByAuctionId(auctionId);
        assertEquals(1, byAuction.size());
    }

    @Test
    public void testDeleteByUserAndAuction() {
        AutoBid ab = new AutoBid("ab-1", auctionId, userId, 1000.0, 10.0, false);
        ab.setCreatedAt(LocalDateTime.now());
        autoBidDAO.save(ab);

        assertTrue(autoBidDAO.deleteByUserAndAuction(userId, auctionId));
        assertNull(autoBidDAO.findByUserAndAuction(userId, auctionId));
    }
}
