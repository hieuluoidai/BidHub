package database;

import model.auction.Auction;
import model.auction.BidTransaction;
import model.item.Electronics;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BidTransactionDAOTest extends BaseDAOTest {
    private BidTransactionDAO bidDAO;
    private UserDAO userDAO;
    private ItemDAO itemDAO;
    private AuctionDAO auctionDAO;

    private final String bidderId = "b-1";
    private final String auctionId = "a-1";

    @BeforeEach
    public void setUp() {
        bidDAO = new BidTransactionDAO();
        userDAO = new UserDAO();
        itemDAO = new ItemDAO();
        auctionDAO = new AuctionDAO();

        userDAO.save(new Bidder(bidderId, "Bidder", "b@test.com", "pass"));
        userDAO.save(new Seller("s-1", "Seller", "s@test.com", "pass"));

        Electronics item = new Electronics("i-1", "Laptop", "Desc", 500.0, "Dell");
        itemDAO.save(item, "s-1");

        Auction a = new Auction(auctionId, item, LocalDateTime.now(), LocalDateTime.now().plusHours(2));
        a.setStatus("RUNNING");
        auctionDAO.save(a);
        }

    @Test
    public void testSaveAndFetch() {
        assertTrue(bidDAO.save(auctionId, bidderId, 600.0));
        assertTrue(bidDAO.save(auctionId, bidderId, 700.0, BidTransaction.BidType.AUTO_BID));

        List<BidTransaction> txs = bidDAO.findTransactionsByAuctionId(auctionId);
        assertEquals(2, txs.size());
        assertEquals(700.0, txs.get(1).getBidAmount());
        assertEquals(BidTransaction.BidType.AUTO_BID, txs.get(1).getBidType());
    }

    @Test
    public void testWinnerAndCounts() {
        bidDAO.save(auctionId, bidderId, 600.0);
        String bidder2 = "b-2";
        userDAO.save(new Bidder(bidder2, "Bidder2", "b2@test.com", "pass"));
        bidDAO.save(auctionId, bidder2, 800.0);

        String[] winner = bidDAO.findWinner(auctionId);
        assertNotNull(winner);
        assertEquals(bidder2, winner[0]);
        assertEquals("800.0", winner[1]);

        assertEquals(2, bidDAO.countBidsByAuctionId(auctionId));
        assertEquals(1, bidDAO.countBidsByBidderId(bidderId));
        assertEquals(1, bidDAO.countBidsByBidderId(bidder2));
    }

    @Test
    public void testCommitmentAndParticipation() {
        bidDAO.save(auctionId, bidderId, 600.0);
        
        assertEquals(600.0, bidDAO.getTopBidCommitment(bidderId, null));
        assertEquals(1, bidDAO.countActiveParticipations(bidderId));
    }
}
