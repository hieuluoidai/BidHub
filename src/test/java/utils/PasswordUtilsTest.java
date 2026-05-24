package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PasswordUtilsTest {

    @Test
    public void testHashAndVerify() {
        String pass = "mySecurePassword123";
        String hash = PasswordUtils.hash(pass);
        
        assertNotNull(hash);
        assertNotEquals(pass, hash);
        assertTrue(PasswordUtils.isBCryptHash(hash));
        
        assertTrue(PasswordUtils.verify(pass, hash));
        assertFalse(PasswordUtils.verify("wrongPassword", hash));
        assertFalse(PasswordUtils.verify(null, hash));
        assertFalse(PasswordUtils.verify(pass, null));
    }

    @Test
    public void testIsBCryptHash() {
        // Valid BCrypt hash (example format)
        assertTrue(PasswordUtils.isBCryptHash("$2a$12$R9h/lIPzHZfvBpyMvEBYue.p0G/P3UaZ8P0VpD95fW.tXp.9B1yHi"));
        
        // Invalid hashes
        assertFalse(PasswordUtils.isBCryptHash("too-short"));
        assertFalse(PasswordUtils.isBCryptHash(null));
        assertFalse(PasswordUtils.isBCryptHash("plain-text-password-that-is-long-enough-but-does-not-start-right"));
    }

    @Test
    public void testHashEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.hash(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtils.hash(null));
    }
}
