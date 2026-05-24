package database;

import model.notification.Notification;
import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationDAOTest extends BaseDAOTest {
    private NotificationDAO notificationDAO;
    private UserDAO userDAO;
    private final String userId = "user-123";
    private final String senderId = "sender-456";

    @BeforeEach
    public void setUp() {
        notificationDAO = new NotificationDAO();
        userDAO = new UserDAO();
        userDAO.save(new Bidder(userId, "Receiver", "receiver@test.com", "pass"));
        userDAO.save(new Bidder(senderId, "Sender", "sender@test.com", "pass"));
    }

    @Test
    public void testInsertAndFindRecent() {
        String id = notificationDAO.insert(userId, Notification.Type.AUCTION_NEW_BID, "New Bid", "You placed a bid.");
        assertNotNull(id);

        List<Notification> recent = notificationDAO.findRecent(userId, 10);
        assertEquals(1, recent.size());
        assertEquals("New Bid", recent.get(0).getTitle());
    }

    @Test
    public void testUpsertChat() {
        // First message
        String id1 = notificationDAO.upsertChat(userId, senderId, "New Message", "Hello!");
        assertNotNull(id1);

        // Second message from same sender should update
        String result = notificationDAO.upsertChat(userId, senderId, "New Message", "Hello again!");
        assertEquals("updated", result);

        List<Notification> recent = notificationDAO.findRecent(userId, 10);
        assertEquals(1, recent.size());
        assertEquals("Hello again!", recent.get(0).getMessage());
    }

    @Test
    public void testUnreadCountAndMarkRead() {
        notificationDAO.insert(userId, Notification.Type.AUCTION_OUTBID, "Outbid", "Someone outbid you.");
        assertEquals(1, notificationDAO.getUnreadCount(userId));

        List<Notification> recent = notificationDAO.findRecent(userId, 1);
        String nid = recent.get(0).getNotificationId();

        assertTrue(notificationDAO.markAsRead(nid));
        assertEquals(0, notificationDAO.getUnreadCount(userId));
    }

    @Test
    public void testMarkAllAsRead() {
        notificationDAO.insert(userId, Notification.Type.AUCTION_WON, "Won", "You won!");
        notificationDAO.insert(userId, Notification.Type.AUCTION_ENDED_SOLD, "Sold", "Item sold.");
        assertEquals(2, notificationDAO.getUnreadCount(userId));

        assertTrue(notificationDAO.markAllAsRead(userId));
        assertEquals(0, notificationDAO.getUnreadCount(userId));
    }

    @Test
    public void testMarkChatAsRead() {
        notificationDAO.upsertChat(userId, senderId, "Chat", "Hi");
        assertEquals(1, notificationDAO.getUnreadCount(userId));

        notificationDAO.markChatAsRead(userId, senderId);
        assertEquals(0, notificationDAO.getUnreadCount(userId));
    }
}
