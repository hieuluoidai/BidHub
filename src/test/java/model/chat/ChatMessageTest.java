package model.chat;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChatMessageTest {

    @Test
    public void testChatMessagePOJO() {
        LocalDateTime now = LocalDateTime.now();
        ChatMessage msg = new ChatMessage("m1", "s1", "r1", "Hello", now, null, false);
        
        assertEquals("m1", msg.getMessageId());
        assertEquals("s1", msg.getSenderId());
        assertEquals("r1", msg.getReceiverId());
        assertEquals("Hello", msg.getContent());
        assertEquals(now, msg.getSentAt());
        assertNull(msg.getReadAt());
        assertFalse(msg.isLiked());
        assertFalse(msg.isRead());
        assertFalse(msg.isRecalled());

        msg.setReadAt(now);
        assertTrue(msg.isRead());
        msg.setLiked(true);
        assertTrue(msg.isLiked());
        msg.setRecalled(true);
        assertTrue(msg.isRecalled());

        ChatMessage empty = new ChatMessage();
        empty.setMessageId("e1");
        empty.setSenderId("s2");
        empty.setReceiverId("r2");
        empty.setContent("Bye");
        empty.setSentAt(now);
        assertEquals("e1", empty.getMessageId());
        assertEquals("s2", empty.getSenderId());
        assertEquals("r2", empty.getReceiverId());
        assertEquals("Bye", empty.getContent());
    }

    @Test
    public void testInnerClasses() {
        List<ChatMessage> list = new ArrayList<>();
        ChatMessage.Bundle bundle = new ChatMessage.Bundle("p1", list);
        assertEquals("p1", bundle.partnerId);
        assertEquals(list, bundle.items);

        LocalDateTime now = LocalDateTime.now();
        ChatMessage.Summary summary = new ChatMessage.Summary("p1", "user", "path", "last", now, 5, true);
        assertEquals("p1", summary.partnerId);
        assertEquals("user", summary.partnerUsername);
        assertEquals("path", summary.partnerAvatarPath);
        assertEquals("last", summary.lastMessage);
        assertEquals(now, summary.lastAt);
        assertEquals(5, summary.unreadCount);
        assertTrue(summary.lastFromMe);

        List<ChatMessage.Summary> summaries = new ArrayList<>();
        summaries.add(summary);
        ChatMessage.SummaryBundle summaryBundle = new ChatMessage.SummaryBundle(summaries, 5);
        assertEquals(summaries, summaryBundle.items);
        assertEquals(5, summaryBundle.totalUnread);
    }
}
