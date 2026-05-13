package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * Service chuyên xử lý file ảnh sản phẩm trong hệ thống.
 *
 * Quy ước:
 *   - Thư mục gốc:      ./uploads/items/                (relative tới project root)
 *   - Tên file:          <itemId>.<extension>           (vd: u-abc123.jpg)
 *   - DB lưu path dạng:  items/<itemId>.<extension>     (relative path để dễ portability)
 *
 * Ảnh được lưu local và đường dẫn được lưu vào DB. Khi load để hiển thị,
 * client convert path thành URI tuyệt đối qua {@link #toFileUri(String)}.
 *
 * Định dạng được phép: JPG, JPEG, PNG, GIF.
 */
public final class ImageStorageService {

    /** Thư mục chứa ảnh, nằm ở root của project. */
    public static final String STORAGE_DIR = "uploads/items";

    /** Prefix dùng trong DB image_path — sau "items/" là filename. */
    public static final String DB_PATH_PREFIX = "items/";

    /** Định dạng file ảnh được phép upload. */
    private static final Set<String> ALLOWED_EXTS = Set.of("jpg", "jpeg", "png", "gif");

    /** Giới hạn dung lượng tối đa 10MB — tránh user upload file khổng lồ. */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private ImageStorageService() { /* utility class */ }

    /**
     * Kiểm tra extension có hợp lệ không.
     */
    public static boolean isValidImageExtension(String filename) {
        if (filename == null) return false;
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) return false;
        String ext = filename.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTS.contains(ext);
    }

    /**
     * Lấy extension từ tên file (không có dấu '.'). Trả về "jpg" nếu không xác định.
     */
    public static String getExtension(String filename) {
        if (filename == null) return "jpg";
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) return "jpg";
        return filename.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Copy file ảnh từ source path vào thư mục uploads với tên chuẩn = itemId.ext.
     * Tự tạo thư mục nếu chưa tồn tại. Ghi đè nếu file cùng tên đã tồn tại
     * (case: user update ảnh cho cùng 1 item).
     *
     * @param sourceFile file ảnh gốc user chọn
     * @param itemId     ID của item, dùng làm tên file
     * @return relative path để lưu vào DB (vd: "items/u-abc123.jpg")
     * @throws IOException nếu lỗi I/O
     * @throws IllegalArgumentException nếu file không hợp lệ
     */
    public static String saveImage(File sourceFile, String itemId) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("File không tồn tại");
        }
        if (!sourceFile.isFile()) {
            throw new IllegalArgumentException("Đường dẫn không phải file");
        }
        if (sourceFile.length() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File quá lớn (>10MB). Vui lòng chọn ảnh nhỏ hơn.");
        }
        if (!isValidImageExtension(sourceFile.getName())) {
            throw new IllegalArgumentException(
                    "Định dạng không hỗ trợ. Chỉ chấp nhận JPG, PNG, GIF.");
        }

        // Tạo thư mục đích nếu chưa có
        Path storageDir = Paths.get(STORAGE_DIR);
        Files.createDirectories(storageDir);

        // Đặt tên: <itemId>.<ext>
        String ext = getExtension(sourceFile.getName());
        String filename = itemId + "." + ext;
        Path target = storageDir.resolve(filename);

        // Copy (ghi đè nếu trùng — case update ảnh)
        Files.copy(sourceFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

        // Trả về relative path để lưu DB
        String dbPath = DB_PATH_PREFIX + filename;
        System.out.println(">>> [IMAGE] Đã lưu ảnh: " + sourceFile.getAbsolutePath()
                + " → " + target.toAbsolutePath() + " (DB path: " + dbPath + ")");
        return dbPath;
    }

    /**
     * Xóa file ảnh khỏi disk khi item bị xóa hoặc đổi ảnh.
     * Không throw nếu file không tồn tại — chỉ log warning.
     *
     * @param dbPath path lưu trong DB (vd: "items/u-abc123.jpg")
     */
    public static void deleteImage(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) return;

        // Build absolute path từ relative path
        // DB path: "items/u-abc123.jpg" → file thật: "./uploads/items/u-abc123.jpg"
        String filename = dbPath.startsWith(DB_PATH_PREFIX)
                ? dbPath.substring(DB_PATH_PREFIX.length())
                : dbPath;
        Path target = Paths.get(STORAGE_DIR, filename);

        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                System.out.println(">>> [IMAGE] Đã xóa file: " + target.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println(">>> [IMAGE] Không thể xóa file " + target + ": " + e.getMessage());
        }
    }

    /**
     * Convert DB path thành URI dạng "file:./uploads/items/abc.jpg" để JavaFX
     * Image load được. Trả về null nếu input null/blank.
     */
    public static String toFileUri(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) return null;

        String filename = dbPath.startsWith(DB_PATH_PREFIX)
                ? dbPath.substring(DB_PATH_PREFIX.length())
                : dbPath;
        Path target = Paths.get(STORAGE_DIR, filename).toAbsolutePath();

        if (!Files.exists(target)) {
            System.err.println(">>> [IMAGE] File không tồn tại: " + target);
            return null;
        }
        return target.toUri().toString();
    }

    /**
     * Kiểm tra ảnh có tồn tại trên disk không.
     */
    public static boolean imageExists(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) return false;
        String filename = dbPath.startsWith(DB_PATH_PREFIX)
                ? dbPath.substring(DB_PATH_PREFIX.length())
                : dbPath;
        return Files.exists(Paths.get(STORAGE_DIR, filename));
    }
}