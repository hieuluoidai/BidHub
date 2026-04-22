package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Lớp quản lý kết nối đến MySQL.
 * Sử dụng Singleton Pattern
 */
public class DatabaseConnection {

    // Đường dẫn tới Database.
    private static final String URL = "jdbc:mysql://localhost:3306/auction_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";     // Tên tài khoản MySQL
    private static final String PASSWORD = "password"; // Mật khẩu

    private static DatabaseConnection instance;
    // Biến lưu trữ đường truyền thực tế đến MySQL
    private Connection connection;

    /**
     * Constructor được để là private. 
     */
    private DatabaseConnection() {
        try {
            // 1. Nạp Driver của MySQL vào bộ nhớ Java
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // 2. Kết nối tới MySQL
            this.connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println(">>> Kết nối MySQL thành công!");
            
        } catch (ClassNotFoundException e) {
            System.err.println("Lỗi: Không tìm thấy MySQL Driver. Thêm dependency vào pom.xml!");
            throw new RuntimeException(e);
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối MySQL: Kiểm tra lại tên DB, tài khoản hoặc mật khẩu!");
            throw new RuntimeException(e);
        }
    }

    // Singleton
    public static synchronized DatabaseConnection getInstance() {
        try {
            // Nếu chưa có kết nối nào or kết nối cũ đã bị ngắt -> Tạo mới
            if (instance == null || instance.connection.isClosed()) {
                instance = new DatabaseConnection();
            }
        } catch (SQLException e) {
        	// Đảm bảo luôn có kết nối mới nếu việc kiểm tra trạng thái gặp lỗi
        		instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Lấy đường truyền ra để các DAO gửi lệnh SQL.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Đóng kết nối khi Server chuẩn bị tắt để giải phóng tài nguyên cho máy tính.
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println(">>> Đã đóng kết nối MySQL an toàn.");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }
}