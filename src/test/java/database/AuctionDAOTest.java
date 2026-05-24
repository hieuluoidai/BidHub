package database;

import model.auction.Auction;
import model.item.Electronics;
import model.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionDAOTest extends BaseDAOTest {
    private AuctionDAO auctionDAO;
    private ItemDAO itemDAO;
    private UserDAO userDAO;
    private final String sellerId = "s-auction";

    @BeforeEach
    public void setUp() {
        auctionDAO = new AuctionDAO();
        itemDAO = new ItemDAO();
        userDAO = new UserDAO();
        userDAO.save(new Seller(sellerId, "Seller", "s@test.com", "pass"));
    }

    @Test
    public void testSaveAndFind() {
        Electronics item = new Electronics("i-1", "Phone", "Desc", 100.0, "Apple");
        itemDAO.save(item, sellerId);

        Auction a = new Auction("a-1", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        assertTrue(auctionDAO.save(a));

        Auction found = auctionDAO.findById("a-1");
        assertNotNull(found);
        assertEquals("a-1", found.getAuctionId());
    }

    @Test
    public void testStatusAndQueries() {
        Electronics item = new Electronics("i-2", "Laptop", "Desc", 500.0, "Dell");
        itemDAO.save(item, sellerId);

        Auction a = new Auction("a-2", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auctionDAO.save(a);

        assertTrue(auctionDAO.updateStatus("a-2", "RUNNING"));
        assertEquals("RUNNING", auctionDAO.findById("a-2").getStatus());

        List<Auction> all = auctionDAO.findAll();
        assertTrue(all.stream().anyMatch(auc -> auc.getAuctionId().equals("a-2")));

        Set<String> ids = auctionDAO.getAuctionIdsBySeller(sellerId);
        assertTrue(ids.contains("a-2"));
    }
}
