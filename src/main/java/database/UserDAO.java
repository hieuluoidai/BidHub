package database;

import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

import java.sql.*;

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
            System.err.println("Lỗi findByUsername: " + e.getMessage());
        }
        return null;
    }

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
            System.err.println("Lỗi login: " + e.getMessage());
        }
        return null;
    }

    public boolean save(User user) {
        String sql = "INSERT INTO users (user_id, username, email, password, role) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUserId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPassword());
            stmt.setString(5, getRoleString(user));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {

            if (e.getErrorCode() == 1062) {
                System.err.println("Username hoặc email đã tồn tại!");
            } else {
                System.err.println("Lỗi save user: " + e.getMessage());
            }
            return false;
        }
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        String userId   = rs.getString("user_id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password");
        String role     = rs.getString("role");

        return switch (role) {
            case "SELLER" -> new Seller(userId, username, email, password);
            case "ADMIN"  -> new Admin(userId, username, email, password);
            default       -> new Bidder(userId, username, email, password);
        };
    }

    private String getRoleString(User user) {
        if (user instanceof Admin)  return "ADMIN";
        if (user instanceof Seller) return "SELLER";
        return "BIDDER";
    }
}
