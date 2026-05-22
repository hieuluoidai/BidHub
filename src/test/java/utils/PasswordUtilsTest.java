package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTest {

    @Test
    void testHashAndCheck() {
        String password = "mySecurePassword";
        String hashed = PasswordUtils.hash(password);
        
        assertNotNull(hashed);
        assertNotEquals(password, hashed);
        assertTrue(PasswordUtils.isBCryptHash(hashed));
        
        assertTrue(PasswordUtils.verify(password, hashed));
        assertFalse(PasswordUtils.verify("wrongPassword", hashed));
    }

    @Test
    void testIsBCryptHash() {
        assertTrue(PasswordUtils.isBCryptHash("$2a$12$R9h/lSu6yokiTuBWix.ULuQ8reG7XTp85p/hU4h.4.4.4.4.4.4.4"));
        assertFalse(PasswordUtils.isBCryptHash("plainText"));
        assertFalse(PasswordUtils.isBCryptHash(null));
    }
}
