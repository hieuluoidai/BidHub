package exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception gốc cho các lỗi có ý nghĩa nghiệp vụ trong ứng dụng.
 *
 * <p>Class này gom 3 phần quan trọng của một lỗi: mã lỗi ổn định ({@link ErrorCode}),
 * thông báo an toàn để hiển thị cho người dùng, và details dạng key-value để debug
 * hoặc trả về client. Các exception cụ thể như {@link AuctionException} và
 * {@link ValidationException} nên kế thừa class này để toàn hệ thống xử lý lỗi cùng một chuẩn.
 */
public class AppException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String userMessage;
    private final Map<String, String> details;

    public AppException(ErrorCode errorCode) {
        this(errorCode, null, null, null);
    }

    public AppException(ErrorCode errorCode, String userMessage) {
        this(errorCode, userMessage, null, null);
    }

    public AppException(ErrorCode errorCode, String userMessage, Throwable cause) {
        this(errorCode, userMessage, null, cause);
    }

    public AppException(ErrorCode errorCode, String userMessage, Map<String, String> details) {
        this(errorCode, userMessage, details, null);
    }

    public AppException(ErrorCode errorCode, String userMessage, Map<String, String> details, Throwable cause) {
        super(resolveMessage(errorCode, userMessage), cause);
        this.errorCode = resolveErrorCode(errorCode);
        this.userMessage = resolveMessage(this.errorCode, userMessage);
        this.details = normalizeDetails(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public ErrorResponse toErrorResponse() {
        return ErrorResponse.from(this);
    }

    /**
     * Nếu lập trình viên truyền thiếu mã lỗi, hệ thống vẫn trả về INTERNAL_ERROR thay vì null.
     */
    private static ErrorCode resolveErrorCode(ErrorCode errorCode) {
        return errorCode != null ? errorCode : ErrorCode.INTERNAL_ERROR;
    }

    /**
     * Ưu tiên message cụ thể của từng tình huống; nếu không có thì dùng message mặc định của ErrorCode.
     */
    private static String resolveMessage(ErrorCode errorCode, String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return resolveErrorCode(errorCode).getDefaultMessage();
    }

    /**
     * Chuẩn hóa details để caller không thể sửa map lỗi sau khi exception đã được tạo.
     * Các entry null cũng bị loại bỏ để payload gửi qua socket luôn sạch và dễ đọc.
     */
    private static Map<String, String> normalizeDetails(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(normalized);
    }
}
