package utils;

import database.BaseDAOTest;
import database.NotificationDAO;
import database.UserDAO;
import model.notification.Notification;
import model.user.Admin;
import model.user.Bidder;
import network.AuctionServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class NotificationServiceTest extends BaseDAOTest {

    private AuctionServer mockServer;
    private NotificationDAO notificationDAO;
    private UserDAO userDAO;

    @BeforeEach
    public void setUp() {
        mockServer = mock(AuctionServer.class);
        notificationDAO = new NotificationDAO();
        userDAO = new UserDAO();
    }

    @Test
    public void testNotifyUser() {
        String userId = "u1";
        userDAO.save(new Bidder(userId, "User", "u@test.com", "pass"));
        
        NotificationService.notifyUser(mockServer, userId, Notification.Type.AUCTION_WON, "Title", "Msg");
        
        List<Notification> list = notificationDAO.findRecent(userId, 10);
        assertFalse(list.isEmpty());
        assertEquals("Title", list.get(0).getTitle());
        verify(mockServer).sendToUser(eq(userId), any(Notification.RefreshSignal.class));
    }

    @Test
    public void testNotifyChat() {
        String receiverId = "r1";
        String senderId = "s1";
        userDAO.save(new Bidder(receiverId, "Receiver", "r@test.com", "pass"));
        
        NotificationService.notifyChat(mockServer, receiverId, senderId, "SenderName", "Hello");
        
        List<Notification> list = notificationDAO.findRecent(receiverId, 10);
        assertFalse(list.isEmpty());
        assertTrue(list.get(0).getTitle().contains("SenderName"));
        verify(mockServer).sendToUser(eq(receiverId), any(Notification.RefreshSignal.class));
    }

    @Test
    public void testNotifyAdmins() {
        String adminId = "a1";
        userDAO.save(new Admin(adminId, "Admin", "a@test.com", "pass"));
        userDAO.save(new Bidder("b1", "Bidder", "b@test.com", "pass"));
        
        NotificationService.notifyAdmins(mockServer, Notification.Type.ADMIN_NEW_USER, "New User", "Joined");
        
        List<Notification> list = notificationDAO.findRecent(adminId, 10);
        assertFalse(list.isEmpty());
        assertEquals("New User", list.get(0).getTitle());
        verify(mockServer).sendToUser(eq(adminId), any(Notification.RefreshSignal.class));
    }
}
