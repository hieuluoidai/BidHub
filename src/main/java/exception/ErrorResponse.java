package exception;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Payload lỗi có thể gửi qua ObjectOutputStream từ server về client.
 *
 * <p>Exception không nên được gửi trực tiếp qua socket vì có thể chứa stack trace hoặc thông tin
 * kỹ thuật quá chi tiết. Class này chỉ giữ phần client cần: mã lỗi, thông báo hiển thị,
 * một ít thông tin kỹ thuật phục vụ log/debug và thời điểm phát sinh lỗi.
 */
public class ErrorResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String message;
    private final String technicalMessage;
    private final Map<String, String> details;
    private final Instant timestamp;

    private ErrorResponse(String code, String message, String technicalMessage,
                          Map<String, String> details, Instant timestamp) {
        this.code = code;
        this.message = message;
        this.technicalMessage = technicalMessage;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.timestamp = timestamp;
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        AppException exception = new AppException(errorCode, message);
        return from(exception);
    }

    /**
     * Chuyển mọi Throwable thành ErrorResponse. Nếu Throwable chưa phải AppException,
     * ExceptionMapper sẽ đưa nó về nhóm lỗi ổn định trước khi gửi cho client.
     */
    public static ErrorResponse from(Throwable throwable) {
        if (throwable instanceof AppException appException) {
            return from(appException);
        }
        return from(ExceptionMapper.map(throwable));
    }

    /**
     * Tạo response từ AppException đã chuẩn hóa. Message dùng để hiển thị cho user;
     * technicalMessage chỉ dùng cho log/debug, không nên phụ thuộc vào nó trong UI.
     */
    public static ErrorResponse from(AppException exception) {
        String technical = exception.getCause() == null
                ? exception.getMessage()
                : exception.getCause().getClass().getSimpleName() + ": " + exception.getCause().getMessage();
        return new ErrorResponse(
                exception.getErrorCode().getCode(),
                exception.getUserMessage(),
                technical,
                exception.getDetails(),
                Instant.now());
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTechnicalMessage() {
        return technicalMessage;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ErrorResponse{"
                + "code='" + code + '\''
                + ", message='" + message + '\''
                + ", details=" + details
                + ", timestamp=" + timestamp
                + '}';
    }
}
