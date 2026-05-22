package model;

import model.manager.AppState;
import model.user.Bidder;
import network.AuctionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AppState – quản lý trạng thái Client")
class AppStateTest {

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton
        Field instanceField = AppState.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    @DisplayName("Singleton hoạt động đúng")
    void singleton() {
        AppState s1 = AppState.getInstance();
        AppState s2 = AppState.getInstance();
        assertSame(s1, s2);
    }

    @Test
    @DisplayName("setCurrentUser: cập nhật user và notify client")
    void setCurrentUser() {
        // AuctionClient is created in AppState constructor
        try (MockedConstruction<AuctionClient> mockClient = mockConstruction(AuctionClient.class)) {
            AppState state = AppState.getInstance();
            AuctionClient client = mockClient.constructed().get(0);
            
            Bidder user = new Bidder("U1", "alice", "a@t.com", "p");
            state.setCurrentUser(user);
            
            assertEquals(user, state.getCurrentUser());
            verify(client).send("IDENTIFY:U1");
            
            // Logout
            state.setCurrentUser(null);
            assertNull(state.getCurrentUser());
            assertTrue(state.getMyAutoBidIds().isEmpty());
        }
    }

    @Test
    @DisplayName("AutoBid & Starred states")
    void autoBidAndStarred() {
        AppState state = AppState.getInstance();
        
        state.setMyAutoBid("A1", true);
        assertTrue(state.hasMyAutoBid("A1"));
        state.setMyAutoBid("A1", false);
        assertFalse(state.hasMyAutoBid("A1"));
        
        state.setStarred("A2", true);
        assertTrue(state.isStarred("A2"));
        state.setStarred("A2", false);
        assertFalse(state.isStarred("A2"));
    }

    @Test
    @DisplayName("JavaFX Properties")
    void properties() {
        AppState state = AppState.getInstance();
        
        state.setTotalUnreadChat(5);
        assertEquals(5, state.getTotalUnreadChat());
        assertEquals(5, state.totalUnreadChatProperty().get());
        
        state.setPendingFriendCount(3);
        assertEquals(3, state.getPendingFriendCount());
        assertEquals(3, state.pendingFriendCountProperty().get());
    }

    @Test
    @DisplayName("OpenChatHook")
    void chatHook() {
        AppState state = AppState.getInstance();
        AtomicReference<String[]> received = new AtomicReference<>();
        
        state.setOpenChatHook(received::set);
        state.requestOpenChat("P1", "partner", "path");
        
        assertNotNull(received.get());
        assertEquals("P1", received.get()[0]);
        assertEquals("partner", received.get()[1]);
        assertEquals("path", received.get()[2]);
    }
}
