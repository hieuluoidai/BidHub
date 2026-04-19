package database;

import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import java.sql.*;

/**
 * Lớp quản lý dữ liệu người dùng trong Database.
 * Đóng vai trò là cầu nối để Tìm kiếm, Đăng nhập và Lưu trữ User.
 */
public class UserDAO {

    private final Connection conn;

    /**
     * Khởi tạo DAO: Lấy kết nối duy nhất từ DatabaseConnection (Singleton).
     */
    public UserDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Tìm kiếm một người dùng dựa trên tên đăng nhập.
     */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                // Nếu thấy, chuyển đổi dòng dữ liệu MySQL thành đối tượng Java
                return mapResultSetToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm Username: " + e.getMessage());
        }
        return null; // Không tìm thấy
    }

    /**
     * Xác thực đăng nhập: Kiểm tra cả tên đăng nhập và mật khẩu.
     */
    public User login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
        }
        return null; // Sai tài khoản hoặc mật khẩu
    }

    /**
     * Lưu một người dùng mới vào hệ thống (Dùng khi Đăng ký).
     */
    public boolean save(User user) {
        String sql = "INSERT INTO users (user_id, username, email, password, role) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUserId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPassword());
            stmt.setString(5, getRoleString(user)); // Chuyển kiểu Class thành chữ (ADMIN, SELLER, BIDDER)
            
            stmt.executeUpdate(); // Thực thi lệnh chèn dữ liệu
            return true;
        } catch (SQLException e) {
            // Mã lỗi 1062 là Duplicate Entry (Trùng lặp dữ liệu duy nhất trong MySQL)
            if (e.getErrorCode() == 1062) {
                System.err.println("Lỗi: Tên đăng nhập hoặc Email này đã có người dùng rồi!");
            } else {
                System.err.println("Lỗi lưu người dùng: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Tiện ích: Chuyển đổi một dòng kết quả từ MySQL (ResultSet) thành đối tượng User tương ứng.
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        String userId   = rs.getString("user_id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password");
        String role     = rs.getString("role");

        // Dựa vào chữ "role" trong DB để tạo đúng loại đối tượng (Đa hình)
        return switch (role) {
            case "SELLER" -> new Seller(userId, username, email, password);
            case "ADMIN"  -> new Admin(userId, username, email, password);
            default       -> new Bidder(userId, username, email, password);
        };
    }

    /**
     * Tiện ích: Kiểm tra đối tượng Java thuộc loại nào để lưu chữ tương ứng vào DB.
     */
    private String getRoleString(User user) {
        if (user instanceof Admin)  return "ADMIN";
        if (user instanceof Seller) return "SELLER";
        return "BIDDER";
    }
}