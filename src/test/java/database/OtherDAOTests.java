package database;

import model.auction.BidTransaction;
import model.chat.ChatMessage;
import model.notification.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OtherDAOTests extends BaseDAOTest {

    private BidTransactionDAO bidDAO;
    private ChatMessageDAO chatDAO;
    private NotificationDAO notificationDAO;
    private UserDAO userDAO;

    @BeforeEach
    void setUp() {
        bidDAO = new BidTransactionDAO();
        chatDAO = new ChatMessageDAO();
        notificationDAO = new NotificationDAO();
        userDAO = new UserDAO();

        userDAO.save(new model.user.Bidder("u1", "user1", "u1@ex.com", "p"));
        userDAO.save(new model.user.Bidder("u2", "user2", "u2@ex.com", "p"));
    }

    @Test
    void testBidTransaction() {
        // Cần tạo item và auction trước vì có FK constraint
        model.item.Item item = new model.item.Electronics("item1", "Item 1", "D", 100.0, "B");
        new ItemDAO().save(item, "u1");
        AuctionDAO auctionDAO = new AuctionDAO();
        auctionDAO.save(new model.auction.Auction("auc1", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1)));

        boolean saved = bidDAO.save("auc1", "u1", 110.0, BidTransaction.BidType.MANUAL);
        assertTrue(saved);

        List<BidTransaction> history = bidDAO.findTransactionsByAuctionId("auc1");
        assertEquals(1, history.size());
        assertEquals(110.0, history.get(0).getBidAmount());
    }

    @Test
    void testChatMessage() {
        ChatMessage msg = chatDAO.insert("u1", "u2", "Hello");
        assertNotNull(msg);
        assertEquals("Hello", msg.getContent());

        List<ChatMessage> history = chatDAO.findConversation("u1", "u2", 10);
        assertFalse(history.isEmpty());
        assertEquals("Hello", history.get(0).getContent());
    }

    @Test
    void testNotification() {
        String noteId = notificationDAO.insert("u1", Notification.Type.AUCTION_WON, "Title", "Message");
        assertNotNull(noteId);

        List<Notification> list = notificationDAO.findRecent("u1", 10);
        assertEquals(1, list.size());
        assertEquals("Title", list.get(0).getTitle());
    }
}
