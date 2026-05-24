package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ImageStorageServiceTest {

    @Test
    public void testIsValidImageExtension() {
        assertTrue(ImageStorageService.isValidImageExtension("test.jpg"));
        assertTrue(ImageStorageService.isValidImageExtension("image.PNG"));
        assertTrue(ImageStorageService.isValidImageExtension("photo.jpeg"));
        assertTrue(ImageStorageService.isValidImageExtension("animation.gif"));
        
        assertFalse(ImageStorageService.isValidImageExtension("document.pdf"));
        assertFalse(ImageStorageService.isValidImageExtension("archive.zip"));
        assertFalse(ImageStorageService.isValidImageExtension("no-extension"));
        assertFalse(ImageStorageService.isValidImageExtension(null));
    }

    @Test
    public void testGetExtension() {
        assertEquals("jpg", ImageStorageService.getExtension("test.jpg"));
        assertEquals("png", ImageStorageService.getExtension("image.png"));
        assertEquals("jpg", ImageStorageService.getExtension("no-extension"));
        assertEquals("jpg", ImageStorageService.getExtension(null));
    }

    @Test
    public void testToImageUrl() {
        String dbPath = "items/item123.jpg";
        String url = ImageStorageService.toImageUrl(dbPath);
        
        assertNotNull(url);
        assertTrue(url.startsWith("http://"));
        assertTrue(url.endsWith(dbPath));
        
        assertNull(ImageStorageService.toImageUrl(null));
        assertNull(ImageStorageService.toImageUrl(""));
    }
}
