package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Lớp quản lý kết nối đến MySQL sử dụng HikariCP (Connection Pooling).
 * Đọc cấu hình từ server.properties để hỗ trợ cả local và remote DB.
 */
public class DatabaseConnection {

    private static final HikariDataSource DATA_SOURCE;

    static {
        Properties props = new Properties();
        try (InputStream in = DatabaseConnection.class.getResourceAsStream("/server.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) { }

        String host = props.getProperty("server.host", "localhost");
        String port = props.getProperty("db.port", "3306");
        String name = props.getProperty("db.name", "auction_db");
        String user = props.getProperty("db.user", "root");
        String pass = props.getProperty("db.password", "password");

        // Hỗ trợ truyền full URL (ví dụ cho H2 Testing)
        String url = props.getProperty("db.url");
        if (url == null || url.isEmpty()) {
            url = "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&allowPublicKeyRetrieval=true";
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300_000);
            config.setConnectionTimeout(20_000);
            config.setMaxLifetime(1_800_000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            DATA_SOURCE = new HikariDataSource(config);
            System.out.println(">>> HikariCP kết nối tới " + host + ":" + port + "/" + name);
        } catch (Exception e) {
            System.err.println(">>> Lỗi khởi tạo HikariCP: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Lấy một kết nối từ Pool.
     */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /**
     * @deprecated Dùng getConnection() và try-with-resources thay thế.
     */
    @Deprecated
    public static DatabaseConnection getInstance() {
        return new DatabaseConnection();
    }

    /**
     * Đóng Pool khi tắt Server.
     */
    public static void closePool() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
            System.out.println(">>> Đã đóng HikariCP Pool an toàn.");
        }
    }
}
