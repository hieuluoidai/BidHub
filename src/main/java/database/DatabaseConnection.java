package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Lớp quản lý kết nối đến MySQL sử dụng HikariCP (Connection Pooling).
 * Giúp tối ưu hiệu năng khi có nhiều truy vấn đồng thời.
 */
public class DatabaseConnection {

    private static final String URL = "jdbc:mysql://localhost:3306/auction_db?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "password";

    private static HikariDataSource dataSource;

    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(URL);
            config.setUsername(USERNAME);
            config.setPassword(PASSWORD);

            // Cấu hình Pool
            config.setMaximumPoolSize(10);          // Tối đa 10 kết nối đồng thời
            config.setMinimumIdle(2);               // Luôn giữ ít nhất 2 kết nối rảnh
            config.setIdleTimeout(300000);          // 5 phút rảnh thì đóng kết nối bớt
            config.setConnectionTimeout(20000);     // Chờ tối đa 20s để lấy kết nối
            config.setMaxLifetime(1800000);         // 30 phút thì reset kết nối để tránh lỗi stale

            // Tối ưu cho MySQL
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            System.out.println(">>> HikariCP Connection Pool đã khởi tạo thành công!");
        } catch (Exception e) {
            System.err.println(">>> Lỗi khởi tạo HikariCP: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Lấy một kết nối từ Pool.
     * Lưu ý: Sau khi dùng xong, cần đóng kết nối (conn.close()) để trả lại Pool.
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Phương thức cũ để duy trì tương thích (Legacy Support).
     * @deprecated Nên sử dụng getConnection() trực tiếp và try-with-resources.
     */
    @Deprecated
    public static DatabaseConnection getInstance() {
        return new DatabaseConnection();
    }

    /**
     * Đóng Pool khi tắt Server.
     */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println(">>> Đã đóng HikariCP Pool an toàn.");
        }
    }
}
