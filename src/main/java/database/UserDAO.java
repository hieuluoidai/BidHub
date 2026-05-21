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

    public UserDAO() {
    }

    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
     * Lưu user mới (Đăng ký). Balance mặc định $0 (set qua DEFAULT trong schema).
     * Caller phải HASH password trước.
     */
    public boolean save(User user) {
        String sql = "INSERT INTO users " +
                     "(user_id, username, email, password, role, full_name, date_of_birth, phone_number) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            System.err.println("Lỗi lấy số dư: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Lấy số dư bị khóa (đang dùng cho Auto-Bid).
     */
    public double getLockedBalance(String userId) {
        String sql = "SELECT locked_balance FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("locked_balance");
        } catch (SQLException e) {
            System.err.println("Lỗi lấy số dư bị khóa: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Đặt lại số dư (dùng cho admin / migration / nạp tiền sau này).
     */
    public boolean setBalance(String userId, double newBalance) {
        String sql = "UPDATE users SET balance = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, newBalance);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật số dư: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật số dư bị khóa.
     */
    public boolean setLockedBalance(String userId, double lockedBalance) {
        String sql = "UPDATE users SET locked_balance = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, lockedBalance);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật số dư bị khóa: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật thông tin hồ sơ (Email, SĐT, Ngày sinh).
     */
    public boolean updateProfile(String userId, String email, String phone, String dobStr) {
        String sql = "UPDATE users SET email = ?, phone_number = ?, date_of_birth = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, (phone != null && !phone.isBlank()) ? phone : null);
            
            if (dobStr == null || dobStr.equals("NULL") || dobStr.isBlank()) {
                stmt.setNull(3, java.sql.Types.DATE);
            } else {
                stmt.setDate(3, java.sql.Date.valueOf(dobStr));
            }
            
            stmt.setString(4, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật hồ sơ: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật mật khẩu mới (đã hash).
     */
    public boolean updatePassword(String userId, String hashedPassword) {
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hashedPassword);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật mật khẩu: " + e.getMessage());
            return false;
        }
    }

    /**
     * Khóa một khoản tiền từ balance sang locked_balance.
     */
    public boolean lockBalance(String userId, double amount) {
        if (amount <= 0) return false;
        String sql = "UPDATE users SET balance = balance - ?, locked_balance = locked_balance + ? " +
                     "WHERE user_id = ? AND balance >= ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setDouble(2, amount);
            stmt.setString(3, userId);
            stmt.setDouble(4, amount);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khóa tiền: " + e.getMessage());
            return false;
        }
    }

    /**
     * Giải phóng một khoản tiền từ locked_balance về balance.
     */
    public boolean unlockBalance(String userId, double amount) {
        if (amount <= 0) return false;
        // Dùng LEAST/GREATEST để tránh silent fail khi floating-point làm amount
        // lớn hơn locked_balance một chút (ví dụ 24.1 > 24.0 → WHERE >= thất bại).
        // Luôn hoàn trả tối đa locked_balance, không để locked_balance âm.
        String sql = "UPDATE users SET "
                + "balance = balance + LEAST(?, locked_balance), "
                + "locked_balance = GREATEST(0, locked_balance - ?) "
                + "WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setDouble(2, amount);
            stmt.setString(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi giải phóng tiền: " + e.getMessage());
            return false;
        }
    }

    /**
     * Chuyển tiền ATOMIC giữa 2 tài khoản — TRANSACTION đảm bảo all-or-nothing.
     */
    public boolean transferAtomic(String fromUserId, String toUserId, double amount) {
        if (amount <= 0) return false;
        if (fromUserId.equals(toUserId)) return false;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
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
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi transfer atomic: " + e.getMessage());
            return false;
        }
    }

    /**
     * Chuyển tiền ATOMIC từ locked_balance của người gửi sang balance của người nhận.
     */
    public boolean transferFromLockedAtomic(String fromUserId, String toUserId, double amount) {
        if (amount <= 0) return false;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Khóa row của người gửi và đọc locked_balance
                double fromLocked = -1;
                String selectForUpdate = "SELECT locked_balance FROM users WHERE user_id = ? FOR UPDATE";
                try (PreparedStatement stmt = conn.prepareStatement(selectForUpdate)) {
                    stmt.setString(1, fromUserId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) fromLocked = rs.getDouble("locked_balance");
                    else {
                        conn.rollback();
                        return false;
                    }
                }

                if (fromLocked < amount) {
                    conn.rollback();
                    System.err.println(">>> [TRANSFER_LOCKED] " + fromUserId + " không đủ tiền bị khóa: "
                            + fromLocked + " < " + amount);
                    return false;
                }

                // 2. Trừ tiền locked của người gửi
                String deductSql = "UPDATE users SET locked_balance = locked_balance - ? WHERE user_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deductSql)) {
                    stmt.setDouble(1, amount);
                    stmt.setString(2, fromUserId);
                    if (stmt.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }

                // 3. Cộng tiền vào balance người nhận
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
                System.out.printf(">>> [TRANSFER_LOCKED] %s → %s: $%.2f (từ locked_balance) thành công%n",
                        fromUserId, toUserId, amount);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi transfer locked atomic: " + e.getMessage());
            return false;
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

        // Balance & Locked Balance & Avatar & Pending Seller — có thể chưa có cột nếu DB cũ
        try {
            user.setBalance(rs.getDouble("balance"));
            user.setLockedBalance(rs.getDouble("locked_balance"));
            user.setAvatarPath(rs.getString("avatar_path"));
            user.setPendingSeller(rs.getBoolean("pending_seller"));
        } catch (SQLException ignore) { }

        return user;
    }

    private String getRoleString(User user) {
        if (user instanceof Admin)  return "ADMIN";
        if (user instanceof Seller) return "SELLER";
        return "BIDDER";
    }

    public boolean updateAvatar(String userId, String avatarPath) {
        String sql = "UPDATE users SET avatar_path = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, avatarPath);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật avatar: " + e.getMessage());
            return false;
        }
    }

    public boolean updatePendingSeller(String userId, boolean pending) {
        String sql = "UPDATE users SET pending_seller = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, pending);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái yêu cầu seller: " + e.getMessage());
            return false;
        }
    }

    public boolean approveSeller(String userId) {
        String sql = "UPDATE users SET role = 'SELLER', pending_seller = 0 WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi phê duyệt seller: " + e.getMessage());
            return false;
        }
    }

    /** Hạ quyền SELLER → BIDDER. Không xóa items/auctions cũ (giữ lịch sử). */
    public boolean revokeSeller(String userId) {
        String sql = "UPDATE users SET role = 'BIDDER', pending_seller = 0 WHERE user_id = ? AND role = 'SELLER'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi hủy quyền seller: " + e.getMessage());
            return false;
        }
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách users: " + e.getMessage());
        }
        return users;
    }

    /** Tìm user theo username hoặc user_id (LIKE, tối đa 20 kết quả, bỏ qua excludeId). */
    public List<User> searchByUsername(String query, String excludeId) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users "
                + "WHERE (username LIKE ? OR user_id LIKE ?) AND user_id != ? LIMIT 20";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, excludeId == null ? "" : excludeId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm user: " + e.getMessage());
        }
        return users;
    }
}
