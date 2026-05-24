package utils;

import database.BaseDAOTest;
import database.DatabaseConnection;
import database.UserDAO;
import model.user.Bidder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PasswordMigrationToolTest extends BaseDAOTest {

    @Test
    public void testMigration() throws SQLException {
        UserDAO userDAO = new UserDAO();
        // Plain text password
        userDAO.save(new Bidder("m1", "migrator", "m@test.com", "plain123"));
        // Already hashed password
        userDAO.save(new Bidder("m2", "hashed", "h@test.com", PasswordUtils.hash("secret")));

        // Mock DatabaseConnection to prevent closePool() from killing other tests
        try (MockedStatic<DatabaseConnection> dbStatic = mockStatic(DatabaseConnection.class)) {
            // Return real connections from H2, but DO NOTHING for closePool
            dbStatic.when(DatabaseConnection::getConnection).thenAnswer(inv -> 
                java.sql.DriverManager.getConnection("jdbc:h2:mem:testdb;MODE=MySQL", "sa", "")
            );
            dbStatic.when(DatabaseConnection::closePool).thenAnswer(i -> null);
            
            PasswordMigrationTool.main(new String[0]);
        }

        // Verify state
        String pass1 = userDAO.findById("m1").getPassword();
        assertTrue(PasswordUtils.isBCryptHash(pass1));
        assertTrue(PasswordUtils.verify("plain123", pass1));

        String pass2 = userDAO.findById("m2").getPassword();
        assertTrue(PasswordUtils.isBCryptHash(pass2));
        assertTrue(PasswordUtils.verify("secret", pass2));
    }
}
