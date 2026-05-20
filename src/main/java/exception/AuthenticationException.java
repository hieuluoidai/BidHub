package exception;

/**
 * Lỗi xác thực khi đăng nhập hoặc kiểm tra thông tin tài khoản thất bại.
 *
 * <p>Class này kế thừa AppException để màn hình đăng nhập có thể lấy message thân thiện,
 * còn các lớp xử lý chung vẫn nhận diện được mã AUTHENTICATION_FAILED.
 */
public class AuthenticationException extends AppException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(ErrorCode.AUTHENTICATION_FAILED, message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(ErrorCode.AUTHENTICATION_FAILED, message, cause);
    }
}
