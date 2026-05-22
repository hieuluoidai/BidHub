package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageStorageServiceTest {

    @Test
    void testGetExtension() {
        assertEquals("jpg", ImageStorageService.getExtension("test.jpg"));
        assertEquals("png", ImageStorageService.getExtension("image.png"));
        assertEquals("jpg", ImageStorageService.getExtension("no_extension")); // default
        assertEquals("gif", ImageStorageService.getExtension("anim.gif"));
    }

    @Test
    void testIsValidImageExtension() {
        assertTrue(ImageStorageService.isValidImageExtension("a.jpg"));
        assertTrue(ImageStorageService.isValidImageExtension("b.PNG"));
        assertTrue(ImageStorageService.isValidImageExtension("c.jpeg"));
        assertFalse(ImageStorageService.isValidImageExtension("d.txt"));
        assertFalse(ImageStorageService.isValidImageExtension("e.pdf"));
        assertFalse(ImageStorageService.isValidImageExtension(null));
    }
}
