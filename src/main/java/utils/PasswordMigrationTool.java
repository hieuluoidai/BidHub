package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import database.DatabaseConnection;

/**
 * Tiện ích chạy MỘT LẦN để migrate các mật khẩu plain text trong DB sang
 * dạng BCrypt hash.
 */
public class PasswordMigrationTool {

    public static void main(String[] args) {
        int total      = 0;
        int migrated   = 0;
        int alreadyOk  = 0;
        int failed     = 0;

        String selectSql = "SELECT user_id, username, password FROM users";
        String updateSql = "UPDATE users SET password = ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            while (rs.next()) {
                total++;
                String userId   = rs.getString("user_id");
                String username = rs.getString("username");
                String stored   = rs.getString("password");

                if (PasswordUtils.isBCryptHash(stored)) {
                    System.out.printf("  [SKIP] %s — đã hash, bỏ qua%n", username);
                    alreadyOk++;
                    continue;
                }

                // Hash giá trị hiện tại (plain text) và update
                try {
                    String hashed = PasswordUtils.hash(stored);
                    updateStmt.setString(1, hashed);
                    updateStmt.setString(2, userId);
                    updateStmt.executeUpdate();
                    System.out.printf("  [HASH] %s — đã chuyển %s sang BCrypt%n",
                            username, stored);
                    migrated++;
                } catch (Exception e) {
                    System.err.printf("  [FAIL] %s — %s%n", username, e.getMessage());
                    failed++;
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi SQL khi migrate: " + e.getMessage());
        } finally {
            DatabaseConnection.closePool();
        }

        System.out.println();
        System.out.println("========== KẾT QUẢ MIGRATION ==========");
        System.out.printf("  Tổng số user      : %d%n", total);
        System.out.printf("  Đã hash mới       : %d%n", migrated);
        System.out.printf("  Đã hash trước đó  : %d%n", alreadyOk);
        System.out.printf("  Thất bại          : %d%n", failed);
        System.out.println("=======================================");
    }
}
