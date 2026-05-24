package utils;

import model.friendship.Friendship;
import model.manager.AppState;
import model.user.Bidder;
import network.AuctionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

public class FriendCenterTest {

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
        FriendCenter.reset();
        appStateStatic.close();
    }

    @Test
    public void testInitAndReset() {
        FriendCenter.init();
        verify(mockClient).addFriendBundleListener(any());
        verify(mockClient).addFriendSearchListener(any());
        verify(mockClient).addStringMessageListener(any());

        FriendCenter.reset();
        verify(mockClient).removeFriendBundleListener(any());
        verify(mockClient).removeFriendSearchListener(any());
        verify(mockClient).removeStringMessageListener(any());
    }

    @Test
    public void testActions() {
        when(mockAppState.getCurrentUser()).thenReturn(new Bidder("me", "Me", "me@test.com", "pass"));
        
        FriendCenter.fetchBundle();
        verify(mockClient).send("FRIEND_LIST:me");

        FriendCenter.search("query");
        verify(mockClient).send("USER_SEARCH:me:query");

        FriendCenter.sendRequest("target");
        verify(mockClient).send("FRIEND_REQUEST:me:target");

        FriendCenter.accept("req");
        verify(mockClient).send("FRIEND_ACCEPT:req:me");

        FriendCenter.decline("req");
        verify(mockClient).send("FRIEND_DECLINE:req:me");
    }
}
