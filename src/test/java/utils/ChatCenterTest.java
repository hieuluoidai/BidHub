package utils;

import model.chat.ChatMessage;
import model.manager.AppState;
import model.user.Bidder;
import network.AuctionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

public class ChatCenterTest {

    private AppState mockAppState;
    private AuctionClient mockClient;
    private MockedStatic<AppState> appStateStatic;

    @BeforeEach
    public void setUp() {
        mockAppState = mock(AppState.class);
        mockClient = mock(AuctionClient.class);
        appStateStatic = mockStatic(AppState.class);
        appStateStatic.when(AppState::getInstance).thenReturn(mockAppState);
        when(mockAppState.getClient()).thenReturn(mockClient);
    }

    @AfterEach
    public void tearDown() {
        ChatCenter.reset();
        appStateStatic.close();
    }

    @Test
    public void testInitAddsListeners() {
        ChatCenter.init();
        verify(mockClient).addChatMessageListener(any());
        verify(mockClient).addChatBundleListener(any());
        verify(mockClient).addChatSummaryListener(any());
        verify(mockClient).addStringMessageListener(any());
    }

    @Test
    public void testResetClearsListeners() {
        ChatCenter.init();
        ChatCenter.reset();
        verify(mockClient).removeChatMessageListener(any());
        verify(mockClient).removeChatBundleListener(any());
        verify(mockClient).removeChatSummaryListener(any());
        verify(mockClient).removeStringMessageListener(any());
    }

    @Test
    public void testFetchSummaries() {
        when(mockAppState.getCurrentUser()).thenReturn(new Bidder("u1", "user", "email", "pass"));
        ChatCenter.fetchSummariesForBadge();
        verify(mockClient).send(contains("CHAT_FETCH_LIST:u1"));
    }

    @Test
    public void testAddRemoveListeners() {
        Consumer<ChatMessage> listener = msg -> {};
        ChatCenter.addMessageListener(listener);
        ChatCenter.removeMessageListener(listener);
        // This is just to cover the lines since the list is private
    }
}
