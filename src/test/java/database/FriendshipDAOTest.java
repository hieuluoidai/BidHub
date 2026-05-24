package database;

import model.friendship.Friendship;
import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FriendshipDAOTest extends BaseDAOTest {
    private FriendshipDAO friendshipDAO;
    private UserDAO userDAO;
    private final String u1 = "user-1";
    private final String u2 = "user-2";
    private final String u3 = "user-3";

    @BeforeEach
    public void setUp() {
        friendshipDAO = new FriendshipDAO();
        userDAO = new UserDAO();

        userDAO.save(new Bidder(u1, "Alice", "alice@test.com", "hash"));
        userDAO.save(new Bidder(u2, "Bob", "bob@test.com", "hash"));
        userDAO.save(new Bidder(u3, "Charlie", "charlie@test.com", "hash"));
    }

    @Test
    public void testFriendshipLifecycle() {
        // 1. Send request
        assertTrue(friendshipDAO.sendRequest(u1, u2));
        assertEquals("PENDING_SENT", friendshipDAO.getStatus(u1, u2));
        assertEquals("PENDING_RECEIVED", friendshipDAO.getStatus(u2, u1));

        // 2. Accept request
        assertTrue(friendshipDAO.accept(u1, u2));
        assertTrue(friendshipDAO.areFriends(u1, u2));
        assertEquals("ACCEPTED", friendshipDAO.getStatus(u1, u2));

        // 3. Unfriend
        assertTrue(friendshipDAO.unfriend(u1, u2));
        assertFalse(friendshipDAO.areFriends(u1, u2));
        assertEquals("NONE", friendshipDAO.getStatus(u1, u2));
    }

    @Test
    public void testDeclineRequest() {
        friendshipDAO.sendRequest(u1, u3);
        assertTrue(friendshipDAO.decline(u1, u3));
        assertEquals("NONE", friendshipDAO.getStatus(u1, u3));
    }

    @Test
    public void testGetFriendBundle() {
        // Alice friends with Bob
        friendshipDAO.sendRequest(u1, u2);
        friendshipDAO.accept(u1, u2);
        
        // Charlie invited Alice
        friendshipDAO.sendRequest(u3, u1);

        Friendship.Bundle aliceBundle = friendshipDAO.getFriendBundle(u1);
        assertEquals(1, aliceBundle.friends.size());
        assertEquals(u2, aliceBundle.friends.get(0).getPartnerId());
        
        assertEquals(1, aliceBundle.pending.size());
        assertEquals(u3, aliceBundle.pending.get(0).getPartnerId());
    }

    @Test
    public void testSearch() {
        Friendship.SearchBundle bundle = friendshipDAO.search(u1, "Bob");
        assertFalse(bundle.items.isEmpty());
        assertEquals(u2, bundle.items.get(0).userId);
        assertEquals("NONE", bundle.items.get(0).friendStatus);
    }
}
