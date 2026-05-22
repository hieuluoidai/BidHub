package database;

import model.item.Electronics;
import model.item.Item;
import model.user.Bidder;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemDAOTest extends BaseDAOTest {

    private ItemDAO itemDAO;
    private UserDAO userDAO;

    @BeforeEach
    void setUp() {
        itemDAO = new ItemDAO();
        userDAO = new UserDAO();
        // Cần có user seller trước khi lưu item
        userDAO.save(new Bidder("s-001", "seller1", "s1@ex.com", "pass"));
    }

    @Test
    void testSaveAndFindById() {
        Item item = new Electronics("i-001", "Laptop", "Gaming laptop", 1500.0, "Dell");
        boolean saved = itemDAO.save(item, "s-001");
        assertTrue(saved);

        Item found = itemDAO.findById("i-001");
        assertNotNull(found);
        assertEquals("Laptop", found.getItemName());
        assertTrue(found instanceof Electronics);
        assertEquals("Dell", ((Electronics) found).getBrand());
    }

    @Test
    void testUpdate() {
        Item item = new Electronics("i-002", "Phone", "Old phone", 500.0, "Sony");
        itemDAO.save(item, "s-001");

        item.setItemName("Phone Pro");
        item.setStartingPrice(600.0);
        
        boolean updated = itemDAO.update(item, "s-001");
        assertTrue(updated);

        Item found = itemDAO.findById("i-002");
        assertEquals("Phone Pro", found.getItemName());
        assertEquals(600.0, found.getStartingPrice());
    }

    @Test
    void testDelete() {
        Item item = new Electronics("i-003", "Tablet", "New tablet", 300.0, "Apple");
        itemDAO.save(item, "s-001");
        assertNotNull(itemDAO.findById("i-003"));

        boolean deleted = itemDAO.delete("i-003");
        assertTrue(deleted);
        assertNull(itemDAO.findById("i-003"));
    }

    @Test
    void testFindAll() {
        itemDAO.save(new Electronics("i-004", "E1", "D1", 100, "B1"), "s-001");
        itemDAO.save(new Electronics("i-005", "E2", "D2", 200, "B2"), "s-001");

        List<Item> all = itemDAO.findAll();
        assertTrue(all.size() >= 2);
    }
}
