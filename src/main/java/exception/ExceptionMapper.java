package exception;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Bộ chuyển đổi exception cấp thấp thành AppException có mã lỗi ổn định.
 *
 * <p>Các lớp hệ thống như NumberFormatException, SQLException hoặc IOException thường không phù hợp
 * để gửi thẳng cho client. Mapper này giúp gom chúng về các nhóm lỗi dễ hiểu như VALIDATION_ERROR,
 * DATABASE_ERROR hoặc NETWORK_ERROR, đồng thời giữ cause gốc để log vẫn đủ thông tin kỹ thuật.
 */
public final class ExceptionMapper {

    private ExceptionMapper() {}

    /**
     * Hàm trung tâm để chuẩn hóa Throwable. Mọi nơi cần đưa lỗi về chuẩn chung nên gọi hàm này
     * thay vì tự if/else hoặc tự tạo message khác nhau.
     */
    public static AppException map(Throwable throwable) {
        if (throwable instanceof AppException appException) {
            return appException;
        }
        if (throwable instanceof NumberFormatException) {
            return new ValidationException("number", "Số không hợp lệ.", throwable);
        }
        if (throwable instanceof IllegalArgumentException) {
            return new ValidationException(null, throwable.getMessage(), throwable);
        }
        if (throwable instanceof SQLException) {
            return new DatabaseException(ErrorCode.DATABASE_ERROR.getDefaultMessage(), throwable);
        }
        if (throwable instanceof IOException) {
            return new AppException(ErrorCode.NETWORK_ERROR, ErrorCode.NETWORK_ERROR.getDefaultMessage(), throwable);
        }
        return new AppException(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), throwable);
    }

    public static ErrorResponse toErrorResponse(Throwable throwable) {
        return ErrorResponse.from(map(throwable));
    }

    /**
     * Lấy message an toàn cho người dùng cuối, không lộ stack trace hoặc chi tiết database.
     */
    public static String userMessage(Throwable throwable) {
        return map(throwable).getUserMessage();
    }

    public static ErrorCode code(Throwable throwable) {
        return map(throwable).getErrorCode();
    }

    /**
     * Tạo chuỗi theo protocol cũ dạng PREFIX:CODE:MESSAGE để tương thích với các listener String.
     */
    public static String protocolMessage(String prefix, Throwable throwable) {
        AppException mapped = map(throwable);
        return prefix + ":" + mapped.getErrorCode().getCode() + ":" + mapped.getUserMessage();
    }

    /**
     * Format log ngắn gọn: có code cho dễ lọc, message cho dễ đọc và cause gốc cho debug.
     */
    public static String logMessage(Throwable throwable) {
        AppException mapped = map(throwable);
        Throwable cause = mapped.getCause() != null ? mapped.getCause() : throwable;
        String causeMessage = cause == null ? "" : cause.getMessage();
        return String.format("[%s] %s | cause=%s: %s",
                mapped.getErrorCode().getCode(),
                mapped.getUserMessage(),
                cause == null ? "unknown" : cause.getClass().getSimpleName(),
                causeMessage);
    }
}
