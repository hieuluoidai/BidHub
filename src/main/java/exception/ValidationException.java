package exception;

import java.util.Map;

/**
 * Exception dùng cho dữ liệu đầu vào hoặc dữ liệu domain không hợp lệ.
 *
 * <p>property cho biết field nào bị lỗi, ví dụ {@code bidder}, {@code number} hoặc {@code amount}.
 * Khi property không có ý nghĩa cụ thể, có thể truyền null để chỉ trả message chung.
 */
public class ValidationException extends AppException {
    private static final long serialVersionUID = 1L;

    private final String property;

    public ValidationException(String property, String message) {
        this(property, message, null);
    }

    public ValidationException(String property, String message, Throwable cause) {
        super(ErrorCode.VALIDATION_ERROR, message, details(property), cause);
        this.property = property;
    }

    public String getProperty() {
        return property;
    }

    /**
     * Đưa tên field lỗi vào details để UI hoặc log có thể chỉ ra đúng vị trí sai dữ liệu.
     */
    private static Map<String, String> details(String property) {
        return property == null || property.isBlank() ? Map.of() : Map.of("property", property);
    }
}
