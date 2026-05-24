package database;

import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.PasswordUtils;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserDAOTest extends BaseDAOTest {

    private UserDAO userDAO;

    @BeforeEach
    void setUp() {
        userDAO = new UserDAO();
    }

    @Test
    void testSaveAndFindByUsername() {
        User user = new Bidder("u-001", "testuser", "test@example.com", PasswordUtils.hash("password"));
        user.setFullName("Test User");
        
        boolean saved = userDAO.save(user);
        assertTrue(saved);

        User found = userDAO.findByUsername("testuser");
        assertNotNull(found);
        assertEquals("u-001", found.getUserId());
        assertEquals("test@example.com", found.getEmail());
        assertEquals("Test User", found.getFullName());
    }

    @Test
    void testSetBalance() {
        User user = new Bidder("u-002", "richuser", "rich@example.com", "pass");
        userDAO.save(user);

        boolean updated = userDAO.setBalance("u-002", 5000.0);
        assertTrue(updated);

        User found = userDAO.findById("u-002");
        assertEquals(5000.0, found.getBalance());
    }

    @Test
    void testLockBalance() {
        User user = new Bidder("u-003", "lockuser", "lock@example.com", "pass");
        userDAO.save(user);
        userDAO.setBalance("u-003", 1000.0);

        boolean locked = userDAO.lockBalance("u-003", 400.0);
        assertTrue(locked);

        User found = userDAO.findById("u-003");
        assertEquals(600.0, found.getBalance());
        assertEquals(400.0, found.getLockedBalance());
    }

    @Test
    void testUpdateAndSearch() {
        User user = new Bidder("u-upd", "upd", "upd@ex.com", "p");
        userDAO.save(user);

        // Test updateProfile
        assertTrue(userDAO.updateProfile("u-upd", "new@ex.com", "123456", "2000-01-01"));

        User found = userDAO.findById("u-upd");
        assertEquals("new@ex.com", found.getEmail());
        assertEquals("123456", found.getPhoneNumber());

        // Test searchByUsername
        List<User> search = userDAO.searchByUsername("upd", null);
        assertFalse(search.isEmpty());
        assertEquals("u-upd", search.get(0).getUserId());
    }

    @Test
    void testRecalculateLocked() {
        String uid = "u-recalc";
        userDAO.save(new Bidder(uid, "recalc", "recalc@ex.com", "p"));
        userDAO.setBalance(uid, 1000.0);
        userDAO.setLockedBalance(uid, 500.0);
        
        // totalNeeded will be 0, balance should become 1500, locked 0
        assertTrue(userDAO.recalculateLockedBalance(uid));
        assertEquals(0.0, userDAO.getLockedBalance(uid));
        assertEquals(1500.0, userDAO.getBalance(uid));
    }

    @Test
    void testSellerFields() {
        model.user.Seller s = new model.user.Seller("s-2", "sell2", "s2@ex.com", "p");
        userDAO.save(s);

        User found = userDAO.findById("s-2");
        assertTrue(found instanceof model.user.Seller);
    }

    @Test
    void testTransferAtomic() {
        User u1 = new Bidder("u-004", "user1", "u1@ex.com", "p");
        User u2 = new Bidder("u-005", "user2", "u2@ex.com", "p");
        userDAO.save(u1);
        userDAO.save(u2);
        userDAO.setBalance("u-004", 1000.0);

        boolean transferred = userDAO.transferAtomic("u-004", "u-005", 300.0);
        assertTrue(transferred);

        assertEquals(700.0, userDAO.findById("u-004").getBalance());
        assertEquals(300.0, userDAO.findById("u-005").getBalance());
    }
}
