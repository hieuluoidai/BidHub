package database;

import model.chat.ChatMessage;
import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChatMessageDAOTest extends BaseDAOTest {
    private ChatMessageDAO chatDAO;
    private UserDAO userDAO;
    private String userA = "u-001";
    private String userB = "u-002";

    @BeforeEach
    public void setUp() {
        chatDAO = new ChatMessageDAO();
        userDAO = new UserDAO();
        
        // Create test users
        User u1 = new Bidder(userA, "userA", "a@test.com", "pass");
        userDAO.save(u1);

        User u2 = new Bidder(userB, "userB", "b@test.com", "pass");
        userDAO.save(u2);
    }

    @Test
    public void testInsertAndFind() {
        ChatMessage msg = chatDAO.insert(userA, userB, "Hello B");
        assertNotNull(msg);
        assertEquals(userA, msg.getSenderId());
        assertEquals(userB, msg.getReceiverId());
        assertEquals("Hello B", msg.getContent());

        ChatMessage found = chatDAO.findById(msg.getMessageId());
        assertNotNull(found);
        assertEquals(msg.getMessageId(), found.getMessageId());
    }

    @Test
    public void testFindConversation() throws InterruptedException {
        chatDAO.insert(userA, userB, "Msg 1");
        Thread.sleep(20);
        chatDAO.insert(userB, userA, "Msg 2");
        Thread.sleep(20);
        chatDAO.insert(userA, userB, "Msg 3");

        List<ChatMessage> conv = chatDAO.findConversation(userA, userB, 10);
        assertEquals(3, conv.size());
        assertEquals("Msg 1", conv.get(0).getContent());
        assertEquals("Msg 2", conv.get(1).getContent());
        assertEquals("Msg 3", conv.get(2).getContent());
    }

    @Test
    public void testMarkAsRead() {
        chatDAO.insert(userA, userB, "Unread 1");
        chatDAO.insert(userA, userB, "Unread 2");
        
        int unreadBefore = chatDAO.getTotalUnread(userB);
        assertEquals(2, unreadBefore);

        List<String> readIds = chatDAO.markConversationRead(userB, userA);
        assertEquals(2, readIds.size());

        int unreadAfter = chatDAO.getTotalUnread(userB);
        assertEquals(0, unreadAfter);
    }

    @Test
    public void testSummaries() throws InterruptedException {
        chatDAO.insert(userA, userB, "First message");
        Thread.sleep(20);
        chatDAO.insert(userB, userA, "Latest reply");

        List<ChatMessage.Summary> summaries = chatDAO.findSummaries(userA);
        assertFalse(summaries.isEmpty());
        ChatMessage.Summary s = summaries.get(0);
        assertEquals(userB, s.partnerId);
        assertEquals("Latest reply", s.lastMessage);
        assertFalse(s.lastFromMe); // Last message was from B

        List<ChatMessage.Summary> summariesB = chatDAO.findSummaries(userB);
        ChatMessage.Summary sB = summariesB.get(0);
        assertTrue(sB.lastFromMe); // Last message was from B (me)
    }

    @Test
    public void testRecallAndHide() {
        ChatMessage msg = chatDAO.insert(userA, userB, "Secret");
        
        // Test Recall For All
        ChatMessage recalled = chatDAO.recallForAll(msg.getMessageId(), userA);
        assertNotNull(recalled);
        assertTrue(recalled.isRecalled());

        // Test Hide Self
        ChatMessage msg2 = chatDAO.insert(userA, userB, "Hide me");
        boolean hidden = chatDAO.hideSelf(msg2.getMessageId(), userA);
        assertTrue(hidden);

        List<ChatMessage> conv = chatDAO.findConversation(userA, userB, 10);
        // Should not see msg2 because it's hidden by userA
        assertFalse(conv.stream().anyMatch(m -> m.getMessageId().equals(msg2.getMessageId())));
    }

    @Test
    public void testSetLiked() {
        ChatMessage msg = chatDAO.insert(userA, userB, "Like this");
        assertNotNull(msg);
        assertFalse(msg.isLiked());

        boolean success = chatDAO.setLiked(msg.getMessageId(), true);
        assertTrue(success);

        ChatMessage updated = chatDAO.findById(msg.getMessageId());
        assertTrue(updated.isLiked());
    }
}
