package database;

import model.auction.WalletTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WalletTransactionDAO {

    public boolean save(String userId, double amount, WalletTransaction.TransactionType type, String description) {
        String sql = "INSERT INTO wallet_transactions "
                + "(wallet_tx_id, user_id, amount, type, description, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, userId);
            stmt.setDouble(3, amount);
            stmt.setString(4, type.name());
            stmt.setString(5, description);
            stmt.setObject(6, LocalDateTime.now());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu giao dịch ví: " + e.getMessage());
            return false;
        }
    }

    public List<WalletTransaction> findByUserId(String userId) {
        List<WalletTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM wallet_transactions WHERE user_id = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new WalletTransaction(
                    rs.getString("wallet_tx_id"),
                    rs.getString("user_id"),
                    rs.getDouble("amount"),
                    WalletTransaction.TransactionType.valueOf(rs.getString("type")),
                    rs.getString("description"),
                    rs.getObject("created_at", LocalDateTime.class)
                ));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tải giao dịch ví: " + e.getMessage());
        }
        return list;
    }
}
