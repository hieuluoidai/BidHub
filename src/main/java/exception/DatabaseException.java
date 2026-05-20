package exception;

/**
 * Exception đại diện cho lỗi cơ sở dữ liệu ở tầng nghiệp vụ.
 *
 * <p>DAO hiện tại phần lớn trả boolean/null, nhưng khi cần ném lỗi ra ngoài thì nên dùng class này.
 * Client sẽ chỉ thấy mã DATABASE_ERROR và message thân thiện, còn SQLException gốc vẫn nằm trong cause.
 */
public class DatabaseException extends AppException {
    private static final long serialVersionUID = 1L;

    public DatabaseException(String message) {
        super(ErrorCode.DATABASE_ERROR, message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(ErrorCode.DATABASE_ERROR, message, cause);
    }
}
