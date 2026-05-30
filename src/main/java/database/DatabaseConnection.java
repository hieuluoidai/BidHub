package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Quan ly ket noi den MySQL bang HikariCP.
 */
public class DatabaseConnection {

    private static final HikariDataSource DATA_SOURCE;

    static {
        Properties props = new Properties();
        try (InputStream in = DatabaseConnection.class.getResourceAsStream("/server.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }

        String host = props.getProperty("db.host",
                props.getProperty("server.host", "localhost"));
        String port = props.getProperty("db.port", "3306");
        String name = props.getProperty("db.name", "auction_db");
        String user = props.getProperty("db.user", "root");
        String pass = props.getProperty("db.password", "password");

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
            System.out.println(">>> HikariCP ket noi toi " + host + ":" + port + "/" + name);
            ensureStartupSchema(DATA_SOURCE);
        } catch (Exception e) {
            System.err.println(">>> Loi khoi tao HikariCP: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    @Deprecated
    public static DatabaseConnection getInstance() {
        return new DatabaseConnection();
    }

    public static void closePool() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
            System.out.println(">>> Da dong HikariCP pool an toan.");
        }
    }

    private static void ensureStartupSchema(HikariDataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            ensureNotificationsSchema(conn);
        } catch (SQLException e) {
            System.err.println(">>> [SCHEMA] Khong the kiem tra startup schema: " + e.getMessage());
        }
    }

    private static void ensureNotificationsSchema(Connection conn) throws SQLException {
        if (!columnExists(conn, "notifications", "source_id")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE notifications ADD COLUMN source_id VARCHAR(64) DEFAULT NULL");
                System.out.println(">>> [SCHEMA] Da bo sung notifications.source_id");
            } catch (SQLException e) {
                System.err.println(">>> [SCHEMA] Khong the them notifications.source_id: " + e.getMessage());
                return;
            }
        }

        ensureIndex(conn, "notifications", "idx_user_unread",
                "CREATE INDEX idx_user_unread ON notifications (user_id, is_read, created_at)");
        ensureIndex(conn, "notifications", "idx_chat_upsert",
                "CREATE INDEX idx_chat_upsert ON notifications (user_id, type, source_id, is_read)");
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName)
            throws SQLException {
        String catalog = conn.getCatalog();
        if (columnExists(conn, catalog, tableName, columnName)) {
            return true;
        }
        if (columnExists(conn, catalog, tableName.toUpperCase(), columnName.toUpperCase())) {
            return true;
        }
        return columnExists(conn, catalog, tableName.toLowerCase(), columnName.toLowerCase());
    }

    private static boolean columnExists(Connection conn, String catalog, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(catalog, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static void ensureIndex(Connection conn, String tableName, String indexName, String createSql)
            throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(conn.getCatalog(), null, tableName, false, false)) {
            while (rs.next()) {
                String current = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(current)) {
                    return;
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createSql);
            System.out.println(">>> [SCHEMA] Da tao index " + indexName + " tren " + tableName);
        } catch (SQLException e) {
            System.err.println(">>> [SCHEMA] Khong the tao index " + indexName + ": " + e.getMessage());
        }
    }
}
