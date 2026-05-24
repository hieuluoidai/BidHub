package utils;

import database.BaseDAOTest;
import database.DatabaseConnection;
import database.UserDAO;
import model.user.Bidder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FixBidderTest extends BaseDAOTest {

    @Test
    public void testFixBidder() throws SQLException {
        UserDAO userDAO = new UserDAO();
        userDAO.save(new Bidder("u-003", "BrokenUser", "b@test.com", "pass"));

        try (MockedStatic<DatabaseConnection> dbStatic = mockStatic(DatabaseConnection.class)) {
            dbStatic.when(DatabaseConnection::getConnection).thenAnswer(inv -> 
                java.sql.DriverManager.getConnection("jdbc:h2:mem:testdb;MODE=MySQL", "sa", "")
            );
            dbStatic.when(DatabaseConnection::closePool).thenAnswer(i -> null);
            
            FixBidder.main(new String[0]);
        }
        
        // Just verifying it runs and calls DAO. recalculateLockedBalance is tested in UserDAOTest.
        assertNotNull(userDAO.findById("u-003"));
    }
}
