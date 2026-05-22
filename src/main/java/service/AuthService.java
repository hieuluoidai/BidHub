package service;

import database.UserDAO;
import exception.AuthenticationException;
import exception.ValidationException;
import model.user.Bidder;
import model.user.User;
import utils.PasswordUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service handling authentication and user-related operations.
 * Decoupled from UI for testability.
 */
public class AuthService {

    private final UserDAO userDAO;

    private static final int MIN_AGE = 18;
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 20;
    private static final int PASSWORD_MIN = 8;

    private static final Pattern P_NAME = Pattern.compile("^[\\p{L}\\s]{2,}$");
    private static final Pattern P_PHONE = Pattern.compile("^(0\\d{9,10}|\\+84\\d{9,10})$");
    private static final Pattern P_EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern P_USERNAME = Pattern.compile(
            "^[A-Za-z0-9_]{" + USERNAME_MIN + "," + USERNAME_MAX + "}$");

    private static final Pattern P_PWD_HAS_LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern P_PWD_HAS_DIGIT = Pattern.compile(".*\\d.*");

    public AuthService() {
        this(new UserDAO());
    }

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Authenticates a user.
     *
     * @param username the username
     * @param password the raw password
     * @return the logged-in User
     * @throws AuthenticationException if login fails
     */
    public User login(String username, String password) throws AuthenticationException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AuthenticationException("Tên đăng nhập và mật khẩu không được để trống!");
        }

        User user = userDAO.login(username, password);
        if (user == null) {
            throw new AuthenticationException("Tên đăng nhập hoặc mật khẩu không chính xác!");
        }
        return user;
    }

    /**
     * Registers a new user with validation.
     */
    public User register(String fullName, LocalDate dob, String phone, String email, 
                        String username, String password) throws ValidationException {
        
        validateFullName(fullName);
        validateDob(dob);
        validatePhone(phone);
        validateEmail(email);
        validateUsername(username);
        validatePassword(password);

        if (userDAO.existsByUsername(username)) {
            throw new ValidationException("username", "Tên đăng nhập đã được sử dụng!");
        }
        if (userDAO.existsByEmail(email)) {
            throw new ValidationException("email", "Email đã được đăng ký!");
        }

        String hashedPassword = PasswordUtils.hash(password);
        String userId = "u-" + UUID.randomUUID().toString().substring(0, 8);
        User newUser = new Bidder(userId, username, email, hashedPassword);

        newUser.setFullName(fullName);
        newUser.setDateOfBirth(dob);
        newUser.setPhoneNumber(phone);

        if (!userDAO.save(newUser)) {
            throw new RuntimeException("Không thể lưu người dùng vào cơ sở dữ liệu.");
        }

        return newUser;
    }

    public void validateFullName(String name) throws ValidationException {
        if (name == null || name.isBlank()) {
            throw new ValidationException("fullName", "Họ tên không được để trống");
        }
        if (!P_NAME.matcher(name).matches()) {
            throw new ValidationException("fullName", "Họ tên chỉ chứa chữ cái và dấu cách, ít nhất 2 ký tự");
        }
    }

    public void validateDob(LocalDate dob) throws ValidationException {
        if (dob == null) throw new ValidationException("dob", "Ngày sinh không được để trống");
        LocalDate today = LocalDate.now();
        if (dob.isAfter(today)) {
            throw new ValidationException("dob", "Ngày sinh không thể ở tương lai");
        }
        int age = Period.between(dob, today).getYears();
        if (age < MIN_AGE) {
            throw new ValidationException("dob", "Bạn cần đủ " + MIN_AGE + " tuổi (hiện: " + age + ")");
        }
    }

    public void validatePhone(String phone) throws ValidationException {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException("phone", "Số điện thoại không được để trống");
        }
        if (!P_PHONE.matcher(phone).matches()) {
            throw new ValidationException("phone", "Số điện thoại không hợp lệ");
        }
    }

    public void validateEmail(String email) throws ValidationException {
        if (email == null || email.isBlank()) {
            throw new ValidationException("email", "Email không được để trống");
        }
        if (!P_EMAIL.matcher(email).matches()) {
            throw new ValidationException("email", "Định dạng email không hợp lệ");
        }
    }

    public void validateUsername(String username) throws ValidationException {
        if (username == null || username.isBlank()) {
            throw new ValidationException("username", "Tên đăng nhập không được để trống");
        }
        if (!P_USERNAME.matcher(username).matches()) {
            throw new ValidationException("username", "Tên đăng nhập 3–20 ký tự, chỉ chữ cái, số và dấu _");
        }
    }

    public void validatePassword(String password) throws ValidationException {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("password", "Mật khẩu không được để trống");
        }
        if (password.length() < PASSWORD_MIN) {
            throw new ValidationException("password", "Mật khẩu cần ít nhất " + PASSWORD_MIN + " ký tự");
        }
        if (!P_PWD_HAS_LOWER.matcher(password).matches() || !P_PWD_HAS_DIGIT.matcher(password).matches()) {
            throw new ValidationException("password", "Mật khẩu cần ít nhất 1 chữ thường và 1 chữ số");
        }
    }
}
