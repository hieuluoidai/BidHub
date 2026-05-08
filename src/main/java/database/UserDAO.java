package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import utils.PasswordUtils;

/**
 * Lớp quản lý dữ liệu người dùng trong Database.
 */
public class UserDAO {

    private final Connection conn;

    public UserDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Tìm user theo username.
     */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm Username: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tìm user theo ID.
     */
    public User findById(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm User theo ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Kiểm tra nhanh username đã tồn tại chưa (cho luồng đăng ký).
     */
    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra trùng username: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra nhanh email đã tồn tại chưa (cho luồng đăng ký).
     */
    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra trùng email: " + e.getMessage());
            return false;
        }
    }

    /**
     * Xác thực đăng nhập.
     */
    public User login(String username, String plainPassword) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password");
                // BCrypt.verify trong PasswordUtils
                if (PasswordUtils.verify(plainPassword, storedHash)) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
        }
        return null; // Sai username hoặc mật khẩu
    }

    /**
     * Lưu user mới (Đăng ký).
     */
    public boolean save(User user) {
        String sql = "INSERT INTO users " +
                     "(user_id, username, email, password, role, full_name, date_of_birth, phone_number) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUserId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPassword()); // ĐÃ HASH bởi caller
            stmt.setString(5, getRoleString(user));

            stmt.setString(6, user.getFullName());
            if (user.getDateOfBirth() != null) {
                stmt.setDate(7, java.sql.Date.valueOf(user.getDateOfBirth()));
            } else {
                stmt.setNull(7, Types.DATE);
            }
            stmt.setString(8, user.getPhoneNumber());

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                System.err.println("Lỗi: Tên đăng nhập hoặc Email này đã có người dùng rồi!");
            } else {
                System.err.println("Lỗi lưu người dùng: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Map dòng kết quả từ MySQL → đối tượng User tương ứng.
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        String userId   = rs.getString("user_id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password"); // Đã hash
        String role     = rs.getString("role");

        User user = switch (role) {
            case "SELLER" -> new Seller(userId, username, email, password);
            case "ADMIN"  -> new Admin(userId, username, email, password);
            default       -> new Bidder(userId, username, email, password);
        };

        // Đọc các cột thông tin cá nhân — bọc try/catch để tương thích DB cũ
        try {
            String fullName = rs.getString("full_name");
            java.sql.Date dobSql = rs.getDate("date_of_birth");
            String phone = rs.getString("phone_number");

            user.setFullName(fullName);
            if (dobSql != null) user.setDateOfBirth(dobSql.toLocalDate());
            user.setPhoneNumber(phone);
        } catch (SQLException ignore) {
            // Cột chưa được migrate → bỏ qua, các trường sẽ là null
        }

        return user;
    }

    private String getRoleString(User user) {
        if (user instanceof Admin)  return "ADMIN";
        if (user instanceof Seller) return "SELLER";
        return "BIDDER";
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách users: " + e.getMessage());
        }
        return users;
    }
}
