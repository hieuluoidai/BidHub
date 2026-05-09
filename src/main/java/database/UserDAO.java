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
 *
 * BẢO MẬT: Mọi mật khẩu lưu trong DB đều ở dạng hash BCrypt.
 *
 * BALANCE: Hỗ trợ load + cập nhật số dư ví ảo cho user. Có method
 * {@link #transferAtomic(String, String, double)} để chuyển tiền giữa 2 tài khoản
 * trong cùng 1 transaction — đảm bảo không bị mất tiền khi lỗi giữa chừng.
 */
public class UserDAO {

    private final Connection conn;

    public UserDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

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
     * Xác thực đăng nhập bằng BCrypt.
     */
    public User login(String username, String plainPassword) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password");
                if (PasswordUtils.verify(plainPassword, storedHash)) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lưu user mới (Đăng ký). Balance mặc định $10,000 (set qua DEFAULT trong schema).
     * Caller phải HASH password trước.
     */
    public boolean save(User user) {
        String sql = "INSERT INTO users " +
                     "(user_id, username, email, password, role, full_name, date_of_birth, phone_number) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUserId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPassword());
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

    // ================================================================
    //                   BALANCE / WALLET METHODS
    // ================================================================

    /**
     * Lấy số dư hiện tại của user. Trả về -1 nếu user không tồn tại / lỗi DB.
     */
    public double getBalance(String userId) {
        String sql = "SELECT balance FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            System.err.println("Lỗi lấy số dư: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Đặt lại số dư (dùng cho admin / migration / nạp tiền sau này).
     */
    public boolean setBalance(String userId, double newBalance) {
        String sql = "UPDATE users SET balance = ? WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, newBalance);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật số dư: " + e.getMessage());
            return false;
        }
    }

    /**
     * Chuyển tiền ATOMIC giữa 2 tài khoản — TRANSACTION đảm bảo all-or-nothing.
     *
     * Quy trình:
     *  1. SELECT FOR UPDATE balance của fromUser (lock row)
     *  2. Nếu balance < amount → rollback, trả false
     *  3. UPDATE trừ tiền fromUser
     *  4. UPDATE cộng tiền toUser
     *  5. COMMIT
     *
     * Nếu có lỗi ở bước nào → rollback toàn bộ → không bị "mất tiền".
     *
     * @return true nếu chuyển thành công, false nếu thiếu tiền hoặc lỗi
     */
    public boolean transferAtomic(String fromUserId, String toUserId, double amount) {
        if (amount <= 0) return false;
        if (fromUserId.equals(toUserId)) return false;

        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // 1. Khóa row của người gửi và đọc balance
            double fromBalance = -1;
            String selectForUpdate = "SELECT balance FROM users WHERE user_id = ? FOR UPDATE";
            try (PreparedStatement stmt = conn.prepareStatement(selectForUpdate)) {
                stmt.setString(1, fromUserId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) fromBalance = rs.getDouble("balance");
                else {
                    conn.rollback();
                    return false;
                }
            }

            if (fromBalance < amount) {
                conn.rollback();
                System.err.println(">>> [TRANSFER] " + fromUserId + " không đủ số dư: "
                        + fromBalance + " < " + amount);
                return false;
            }

            // 2. Trừ tiền người gửi
            String deductSql = "UPDATE users SET balance = balance - ? WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deductSql)) {
                stmt.setDouble(1, amount);
                stmt.setString(2, fromUserId);
                if (stmt.executeUpdate() != 1) {
                    conn.rollback();
                    return false;
                }
            }

            // 3. Cộng tiền người nhận
            String addSql = "UPDATE users SET balance = balance + ? WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(addSql)) {
                stmt.setDouble(1, amount);
                stmt.setString(2, toUserId);
                if (stmt.executeUpdate() != 1) {
                    conn.rollback();
                    return false;
                }
            }

            conn.commit();
            System.out.printf(">>> [TRANSFER] %s → %s: $%.2f thành công%n",
                    fromUserId, toUserId, amount);
            return true;

        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
            System.err.println("Lỗi transfer atomic: " + e.getMessage());
            return false;
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ignore) {}
        }
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        String userId   = rs.getString("user_id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password");
        String role     = rs.getString("role");

        User user = switch (role) {
            case "SELLER" -> new Seller(userId, username, email, password);
            case "ADMIN"  -> new Admin(userId, username, email, password);
            default       -> new Bidder(userId, username, email, password);
        };

        // Personal info — bọc try/catch cho tương thích DB cũ chưa migrate
        try {
            String fullName = rs.getString("full_name");
            java.sql.Date dobSql = rs.getDate("date_of_birth");
            String phone = rs.getString("phone_number");

            user.setFullName(fullName);
            if (dobSql != null) user.setDateOfBirth(dobSql.toLocalDate());
            user.setPhoneNumber(phone);
        } catch (SQLException ignore) { }

        // Balance — có thể chưa có cột nếu DB cũ
        try {
            user.setBalance(rs.getDouble("balance"));
        } catch (SQLException ignore) { }

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
