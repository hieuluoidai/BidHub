package database;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

public class DatabaseConnectionTest {

    @Test
    public void testGetConnection() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        conn.close();
    }

    @Test
    public void testGetInstance() {
        // Test the deprecated method for coverage
        DatabaseConnection instance = DatabaseConnection.getInstance();
        assertNotNull(instance);
    }
}
