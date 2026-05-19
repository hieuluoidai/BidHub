package database;

import model.auction.DepositRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class DepositRequestDAO {

    public boolean save(String requestId, String userId, double amount) {
        String sql = "INSERT INTO deposit_requests (request_id, user_id, amount, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, userId);
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DepositRequestDAO.save] " + e.getMessage());
            return false;
        }
    }

    public DepositRequest findById(String requestId) {
        String sql = """
                SELECT dr.request_id, dr.user_id, u.username, dr.amount, dr.status,
                       dr.admin_note, dr.created_at
                FROM deposit_requests dr
                JOIN users u ON dr.user_id = u.user_id
                WHERE dr.request_id = ?
                """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("[DepositRequestDAO.findById] " + e.getMessage());
        }
        return null;
    }

    public List<DepositRequest> findPending() {
        String sql = """
                SELECT dr.request_id, dr.user_id, u.username, dr.amount, dr.status,
                       dr.admin_note, dr.created_at
                FROM deposit_requests dr
                JOIN users u ON dr.user_id = u.user_id
                WHERE dr.status = 'PENDING'
                ORDER BY dr.created_at ASC
                """;
        List<DepositRequest> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[DepositRequestDAO.findPending] " + e.getMessage());
        }
        return list;
    }

    public boolean review(String requestId, DepositRequest.Status status, String adminNote) {
        String sql = "UPDATE deposit_requests SET status = ?, admin_note = ?, reviewed_at = NOW() WHERE request_id = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, adminNote);
            ps.setString(3, requestId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DepositRequestDAO.review] " + e.getMessage());
            return false;
        }
    }

    public List<String> getPendingUserIds() {
        String sql = "SELECT DISTINCT user_id FROM deposit_requests WHERE status = 'PENDING'";
        List<String> ids = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("user_id"));
        } catch (SQLException e) {
            System.err.println("[DepositRequestDAO.getPendingUserIds] " + e.getMessage());
        }
        return ids;
    }

    private DepositRequest mapRow(ResultSet rs) throws SQLException {
        DepositRequest dr = new DepositRequest();
        dr.setRequestId(rs.getString("request_id"));
        dr.setUserId(rs.getString("user_id"));
        dr.setUsername(rs.getString("username"));
        dr.setAmount(rs.getDouble("amount"));
        dr.setStatus(DepositRequest.Status.valueOf(rs.getString("status")));
        dr.setAdminNote(rs.getString("admin_note"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) dr.setCreatedAt(ca.toLocalDateTime());
        return dr;
    }
}
