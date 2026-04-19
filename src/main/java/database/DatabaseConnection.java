package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

	private static final String URL = "jdbc:mysql://localhost:3306/auction_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "password";

    private static DatabaseConnection instance;
    private Connection connection;

    private DatabaseConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println(">>> Kết nối MySQL thành công!");
        } catch (ClassNotFoundException e) {
            System.err.println("Lỗi: Không tìm thấy MySQL Driver. Thêm dependency vào pom.xml!");
            throw new RuntimeException(e);
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối MySQL: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    public static synchronized DatabaseConnection getInstance() {
        try {
            if (instance == null || instance.connection.isClosed()) {
                instance = new DatabaseConnection();
            }
        } catch (SQLException e) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println(">>> Đã đóng kết nối MySQL.");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }
}
