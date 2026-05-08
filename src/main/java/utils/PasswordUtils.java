package utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Tiện ích Hash và xác thực mật khẩu bằng BCrypt.
 */
public final class PasswordUtils {

    /** Cost factor: 2^12 = 4096 vòng lặp băm. */
    private static final int BCRYPT_COST = 12;

    private PasswordUtils() {} // không cho khởi tạo

    /**
     * Hash mật khẩu mới (dùng khi đăng ký hoặc đổi mật khẩu).
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Mật khẩu không được rỗng!");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    /**
     * Xác thực mật khẩu khi đăng nhập.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            // Hash trong DB không đúng định dạng BCrypt
            System.err.println(">>> [SECURITY] Hash trong DB không hợp lệ: " + e.getMessage());
            return false;
        }
    }

    /**
     * Heuristic: nhận biết một chuỗi đã là hash BCrypt hay chưa.
     */
    public static boolean isBCryptHash(String s) {
        return s != null && s.length() == 60
                && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$"));
    }
}
