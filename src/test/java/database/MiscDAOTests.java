package database;

import model.auction.AutoBid;
import model.auction.WalletTransaction;
import model.user.Bidder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiscDAOTests extends BaseDAOTest {

    private AutoBidDAO autoBidDAO;
    private WalletTransactionDAO walletDAO;
    private FriendshipDAO friendshipDAO;
    private UserDAO userDAO;

    @BeforeEach
    void setUp() {
        autoBidDAO = new AutoBidDAO();
        walletDAO = new WalletTransactionDAO();
        friendshipDAO = new FriendshipDAO();
        userDAO = new UserDAO();

        userDAO.save(new Bidder("u1", "user1", "u1@ex.com", "p"));
        userDAO.save(new Bidder("u2", "user2", "u2@ex.com", "p"));
    }

    @Test
    void testWalletTransaction() {
        boolean saved = walletDAO.save("u1", 500.0, WalletTransaction.TransactionType.TOPUP, "Top up test");
        assertTrue(saved);
        
        List<WalletTransaction> history = walletDAO.findByUserId("u1");
        assertFalse(history.isEmpty());
        assertEquals(500.0, history.get(0).getAmount());
    }

    @Test
    void testFriendship() {
        boolean requested = friendshipDAO.sendRequest("u1", "u2");
        assertTrue(requested);

        assertEquals("PENDING_SENT", friendshipDAO.getStatus("u1", "u2"));
        assertEquals("PENDING_RECEIVED", friendshipDAO.getStatus("u2", "u1"));

        boolean accepted = friendshipDAO.accept("u1", "u2");
        assertTrue(accepted);
        assertEquals("ACCEPTED", friendshipDAO.getStatus("u1", "u2"));
    }

    @Test
    void testAutoBid() {
        // Cần auction trước
        model.item.Item item = new model.item.Electronics("item-auto", "Auto Item", "D", 100.0, "B");
        new ItemDAO().save(item, "u1");
        new AuctionDAO().save(new model.auction.Auction("auc-auto", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1)));

        AutoBid ab = new AutoBid("ab1", "auc-auto", "u2", 1000.0, 50.0);
        boolean saved = autoBidDAO.save(ab);
        assertTrue(saved);

        assertNotNull(autoBidDAO.findByUserAndAuction("u2", "auc-auto"));
        
        boolean deleted = autoBidDAO.delete("ab1");
        assertTrue(deleted);
        assertNull(autoBidDAO.findByUserAndAuction("u2", "auc-auto"));
    }
}
