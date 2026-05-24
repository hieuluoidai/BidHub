package database;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class BaseDAOTest {
    protected static Connection h2Connection;

    @BeforeAll
    public static void setupH2() throws SQLException {
        // Khởi tạo H2 database in-memory với MySQL compatibility mode
        // DatabaseConnection sẽ tự động dùng URL này nếu có server.properties trong test resources
        h2Connection = DriverManager.getConnection("jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        createSchema();
    }

    @AfterAll
    public static void tearDownH2() throws SQLException {
        if (h2Connection != null) {
            h2Connection.close();
        }
    }

    @BeforeEach
    protected void setupMock() {
        // Không cần mockStatic nữa vì DatabaseConnection đã trỏ tới H2 qua server.properties
    }

    @AfterEach
    protected void tearDownMock() throws SQLException {
        // Clear data after each test
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE users");
            stmt.execute("TRUNCATE TABLE items");
            stmt.execute("TRUNCATE TABLE auctions");
            stmt.execute("TRUNCATE TABLE bid_transactions");
            stmt.execute("TRUNCATE TABLE auto_bids");
            stmt.execute("TRUNCATE TABLE wallet_transactions");
            stmt.execute("TRUNCATE TABLE notifications");
            stmt.execute("TRUNCATE TABLE deposit_requests");
            stmt.execute("TRUNCATE TABLE chat_messages");
            stmt.execute("TRUNCATE TABLE friendships");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private static void createSchema() throws SQLException {
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "user_id VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL UNIQUE, " +
                    "email VARCHAR(100) NOT NULL UNIQUE, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "full_name VARCHAR(150), " +
                    "date_of_birth DATE, " +
                    "phone_number VARCHAR(20), " +
                    "balance DECIMAL(15,2) DEFAULT 0.00, " +
                    "locked_balance DECIMAL(15,2) DEFAULT 0.00, " +
                    "role VARCHAR(20) DEFAULT 'BIDDER', " +
                    "pending_seller TINYINT(1) DEFAULT 0, " +
                    "avatar_path VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS items (" +
                    "item_id VARCHAR(36) PRIMARY KEY, " +
                    "item_name VARCHAR(200) NOT NULL, " +
                    "description TEXT, " +
                    "image_path VARCHAR(500), " +
                    "starting_price DOUBLE NOT NULL, " +
                    "item_type VARCHAR(20) NOT NULL, " +
                    "seller_id VARCHAR(36) NOT NULL, " +
                    "brand VARCHAR(100), " +
                    "warranty_months INT, " +
                    "artist VARCHAR(100), " +
                    "material VARCHAR(100), " +
                    "model VARCHAR(100), " +
                    "manufacture_year INT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS auctions (" +
                    "auction_id VARCHAR(36) PRIMARY KEY, " +
                    "item_id VARCHAR(36) NOT NULL, " +
                    "status VARCHAR(20) DEFAULT 'OPEN', " +
                    "start_time TIMESTAMP NOT NULL, " +
                    "end_time TIMESTAMP NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS bid_transactions (" +
                    "transaction_id VARCHAR(36) PRIMARY KEY, " +
                    "auction_id VARCHAR(36) NOT NULL, " +
                    "bidder_id VARCHAR(36) NOT NULL, " +
                    "bid_amount DOUBLE NOT NULL, " +
                    "bid_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "bid_type VARCHAR(20) DEFAULT 'MANUAL', " +
                    "is_anonymous TINYINT(1) DEFAULT 0, " +
                    "anonymous_display_name VARCHAR(50), " +
                    "FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (bidder_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS auto_bids (" +
                    "auto_bid_id VARCHAR(36) PRIMARY KEY, " +
                    "auction_id VARCHAR(36) NOT NULL, " +
                    "user_id VARCHAR(36) NOT NULL, " +
                    "max_bid DECIMAL(15,2) NOT NULL, " +
                    "increment DECIMAL(15,2) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "is_anonymous TINYINT(1) DEFAULT 0, " +
                    "FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE, " +
                    "UNIQUE (auction_id, user_id)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS wallet_transactions (" +
                    "wallet_tx_id VARCHAR(36) PRIMARY KEY, " +
                    "user_id VARCHAR(36) NOT NULL, " +
                    "amount DOUBLE NOT NULL, " +
                    "type VARCHAR(20) NOT NULL, " +
                    "description VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS notifications (" +
                    "notification_id VARCHAR(36) PRIMARY KEY, " +
                    "user_id VARCHAR(36) NOT NULL, " +
                    "type VARCHAR(40) NOT NULL, " +
                    "title VARCHAR(150) NOT NULL, " +
                    "message VARCHAR(500) NOT NULL, " +
                    "is_read TINYINT(1) DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "source_id VARCHAR(64), " +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS deposit_requests (" +
                    "request_id VARCHAR(30) PRIMARY KEY, " +
                    "user_id VARCHAR(36) NOT NULL, " +
                    "amount DECIMAL(15,2) NOT NULL, " +
                    "status VARCHAR(20) DEFAULT 'PENDING', " +
                    "admin_note VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "reviewed_at TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS chat_messages (" +
                    "message_id VARCHAR(64) PRIMARY KEY, " +
                    "sender_id VARCHAR(64) NOT NULL, " +
                    "receiver_id VARCHAR(64) NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "read_at TIMESTAMP NULL, " +
                    "liked TINYINT(1) DEFAULT 0, " +
                    "recalled TINYINT(1) DEFAULT 0, " +
                    "hidden_by_sender TINYINT(1) DEFAULT 0, " +
                    "hidden_by_receiver TINYINT(1) DEFAULT 0" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS friendships (" +
                    "requester_id VARCHAR(64) NOT NULL, " +
                    "addressee_id VARCHAR(64) NOT NULL, " +
                    "status VARCHAR(20) DEFAULT 'PENDING', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (requester_id, addressee_id), " +
                    "FOREIGN KEY (requester_id) REFERENCES users(user_id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (addressee_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ")");
        }
    }
}
