package exception;

/**
 * Danh sách mã lỗi chuẩn của toàn bộ ứng dụng.
 *
 * <p>Mỗi lỗi nghiệp vụ nên đi qua enum này thay vì tự viết chuỗi lỗi rời rạc ở từng nơi.
 * Cách này giúp server, client, log và unit test cùng hiểu một lỗi theo cùng một mã ổn định
 * như {@code BID_TOO_LOW}, {@code AUCTION_NOT_FOUND} hoặc {@code VALIDATION_ERROR}.
 */
public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", "Dữ liệu không hợp lệ."),
    COMMAND_FORMAT_INVALID("COMMAND_FORMAT_INVALID", "Lệnh gửi lên server sai định dạng."),
    AUTHENTICATION_FAILED("AUTHENTICATION_FAILED", "Thông tin đăng nhập không chính xác."),
    USER_NOT_FOUND("USER_NOT_FOUND", "Người dùng không tồn tại."),
    AUCTION_NOT_FOUND("AUCTION_NOT_FOUND", "Phiên đấu giá không tồn tại."),
    AUCTION_NOT_RUNNING("AUCTION_NOT_RUNNING", "Phiên đấu giá không đang diễn ra."),
    BID_TOO_LOW("BID_TOO_LOW", "Giá đặt phải cao hơn giá hiện tại."),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "Số dư không đủ để thực hiện giao dịch."),
    DATABASE_ERROR("DATABASE_ERROR", "Lỗi cơ sở dữ liệu."),
    NETWORK_ERROR("NETWORK_ERROR", "Lỗi kết nối mạng."),
    INTERNAL_ERROR("INTERNAL_ERROR", "Đã xảy ra lỗi không mong muốn.");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
