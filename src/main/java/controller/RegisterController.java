package controller;

import database.UserDAO;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import model.manager.AppState;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import utils.AlertHelper;
import utils.PasswordUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Điều khiển màn hình đăng ký tài khoản mới.
 */
public class RegisterController {

    // ===== FXML bindings (input) =====
    @FXML private TextField     fullNameField;
    @FXML private DatePicker    dobPicker;
    @FXML private TextField     phoneField;
    @FXML private TextField     emailField;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         messageLabel;

    // ===== FXML bindings (per-field hint labels) =====
    @FXML private Label hintFullName;
    @FXML private Label hintDob;
    @FXML private Label hintPhone;
    @FXML private Label hintEmail;
    @FXML private Label hintUsername;
    @FXML private Label hintPassword;
    @FXML private Label hintConfirm;

    // ===== Hằng số =====
    private static final int MIN_AGE      = 18;
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 20;
    private static final int PASSWORD_MIN = 8;

    private static final String LABEL_BIDDER = "Bidder (Người đấu giá)";
    private static final String LABEL_SELLER = "Seller (Người bán)";

    private static final Pattern P_NAME     = Pattern.compile("^[\\p{L}\\s]{2,}$");
    private static final Pattern P_PHONE    = Pattern.compile("^(0\\d{9,10}|\\+84\\d{9,10})$");
    private static final Pattern P_EMAIL    = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern P_USERNAME = Pattern.compile(
            "^[A-Za-z0-9_]{" + USERNAME_MIN + "," + USERNAME_MAX + "}$");

    // Password: phải có ít nhất 1 chữ thường + 1 chữ số
    private static final Pattern P_PWD_HAS_LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern P_PWD_HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern P_DATE_SHAPE =
            Pattern.compile("^\\s*(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s*$");

    /**
     * STRICT formatter: ResolverStyle.STRICT khiến LocalDate KHÔNG tự "smart adjust"
     */
    private static final DateTimeFormatter DATE_FMT_STRICT = DateTimeFormatter
            .ofPattern("d/M/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    // Formatter dùng để HIỂN THỊ ngày trong DatePicker
    private static final DateTimeFormatter DATE_FMT_DISPLAY =
            DateTimeFormatter.ofPattern("d/M/yyyy");

    // CSS classes cho hint state
    private static final String CSS_HINT_OK   = "hint-ok";
    private static final String CSS_HINT_ERR  = "hint-error";
    private static final String CSS_INPUT_ERR = "input-error";

    @FXML
    public void initialize() {
        configureDobPicker();
        clearAllHints();
        attachRealtimeListeners();
    }

    /**
     * Cấu hình DatePicker:
     *   - Hiển thị: format d/M/yyyy
     *   - Parse: cho phép user gõ tự do, parse strict (báo lỗi cụ thể tới mức ngày
     *     không tồn tại như 30/2). KHÔNG ném exception ra ngoài — chỉ trả null
     *     để JavaFX không crash, sẽ validate sau.
     *   - Disable ngày tương lai trên popup lịch
     */
    private void configureDobPicker() {
        dobPicker.setPromptText("d/M/yyyy");

        dobPicker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : DATE_FMT_DISPLAY.format(date);
            }
            @Override
            public LocalDate fromString(String text) {
                return parseDateOrNull(text);
            }
        });

        dobPicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
        
        dobPicker.getEditor().textProperty().addListener((obs, o, n) -> validateDob());
    }

    /**
     * Parse mềm: trả null nếu không parse được. 
     */
    private LocalDate parseDateOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        // DateTimeParseException là subclass của DateTimeException nên
        // chỉ cần catch DateTimeException là đủ (không multi-catch được).
        try {
            return LocalDate.parse(text.trim(), DATE_FMT_STRICT);
        } catch (DateTimeException e) {
            // thử dạng có pad 0: "03/01/2007" cho linh hoạt khi user copy-paste
            try {
                return LocalDate.parse(text.trim(),
                        DateTimeFormatter.ofPattern("dd/MM/uuuu")
                                .withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeException ex) {
                return null;
            }
        }
    }

    /**
     * Đăng ký listener realtime cho từng field.
     */
    private void attachRealtimeListeners() {
        fullNameField.textProperty().addListener(onChange(this::validateFullName));
        phoneField.textProperty().addListener(onChange(this::validatePhone));
        emailField.textProperty().addListener(onChange(this::validateEmail));
        usernameField.textProperty().addListener(onChange(this::validateUsername));
        passwordField.textProperty().addListener(onChange(this::validatePassword));
        confirmPasswordField.textProperty().addListener(onChange(this::validateConfirm));
        passwordField.textProperty().addListener(onChange(this::validateConfirm));
        // Note: dob editor listener đã add trong configureDobPicker()
        dobPicker.valueProperty().addListener((obs, o, n) -> validateDob());
    }

    private <T> ChangeListener<T> onChange(Runnable action) {
        return (obs, oldVal, newVal) -> action.run();
    }


    /**
     * Validation từng field
     */
    private boolean validateFullName() {
        String v = safeTrim(fullNameField.getText());
        if (v.isEmpty()) return clearHint(fullNameField, hintFullName);
        if (!P_NAME.matcher(v).matches()) {
            return showHintError(fullNameField, hintFullName,
                    "Chỉ chứa chữ cái và dấu cách, ít nhất 2 ký tự");
        }
        return showHintOk(fullNameField, hintFullName);
    }

    /**
     * Validate ngày sinh — phân biệt 5 trường hợp lỗi:
     *   1. Field rỗng              → ẩn hint
     *   2. Có text nhưng sai dạng  → "Định dạng không hợp lệ."
     *   3. Đúng dạng nhưng ngày không tồn tại (30/2, 31/4...) → "Ngày DD/MM không tồn tại"
     *   4. Hợp lệ nhưng tương lai  → "Ngày sinh không thể ở tương lai"
     *   5. Hợp lệ nhưng < 18 tuổi  → "Bạn cần đủ 18 tuổi"
     */
    private boolean validateDob() {
        // Lấy text trực tiếp từ editor
        String text = dobPicker.getEditor().getText();

        // Case 1: rỗng
        if (text == null || text.isBlank()) {
            return clearHint(dobPicker, hintDob);
        }

        // Case 2: kiểm tra "shape" trước — phải có dạng "số/số/số 4 chữ số"
        Matcher m = P_DATE_SHAPE.matcher(text);
        if (!m.matches()) {
            return showHintError(dobPicker, hintDob,
                    "Định dạng không hợp lệ.");
        }

        // Case 3: shape đúng nhưng ngày không tồn tại (30/2/2000, 31/4/2020...)
        int day = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int year = Integer.parseInt(m.group(3));
        LocalDate dob;
        try {
            dob = LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return showHintError(dobPicker, hintDob,
                    "Ngày " + day + "/" + month + " không tồn tại trong năm " + year);
        }

        // Case 4: tương lai
        LocalDate today = LocalDate.now();
        if (dob.isAfter(today)) {
            return showHintError(dobPicker, hintDob, "Ngày sinh không hợp lệ");
        }

        // Case 5: tuổi
        int age = Period.between(dob, today).getYears();
        if (age < MIN_AGE) {
            return showHintError(dobPicker, hintDob,
                    "Bạn cần đủ " + MIN_AGE + " tuổi (hiện: " + age + ")");
        }

        return showHintOk(dobPicker, hintDob);
    }

    private boolean validatePhone() {
        String v = safeTrim(phoneField.getText());
        if (v.isEmpty()) return clearHint(phoneField, hintPhone);
        if (!P_PHONE.matcher(v).matches()) {
            return showHintError(phoneField, hintPhone,
                    "Số điện thoại không hợp lệ");
        }
        return showHintOk(phoneField, hintPhone);
    }

    private boolean validateEmail() {
        String v = safeTrim(emailField.getText());
        if (v.isEmpty()) return clearHint(emailField, hintEmail);
        if (!P_EMAIL.matcher(v).matches()) {
            return showHintError(emailField, hintEmail, "Định dạng email không hợp lệ");
        }
        return showHintOk(emailField, hintEmail);
    }

    private boolean validateUsername() {
        String v = safeTrim(usernameField.getText());
        if (v.isEmpty()) return clearHint(usernameField, hintUsername);
        if (!P_USERNAME.matcher(v).matches()) {
            return showHintError(usernameField, hintUsername,
                    "3–20 ký tự, chỉ chữ cái, số và dấu _");
        }
        return showHintOk(usernameField, hintUsername);
    }

    private boolean validatePassword() {
        String v = passwordField.getText();
        if (v == null || v.isEmpty()) return clearHint(passwordField, hintPassword);
        if (v.length() < PASSWORD_MIN) {
            return showHintError(passwordField, hintPassword,
                    "Cần ít nhất " + PASSWORD_MIN + " ký tự (hiện: " + v.length() + ")");
        }
        boolean hasLower = P_PWD_HAS_LOWER.matcher(v).matches();
        boolean hasDigit = P_PWD_HAS_DIGIT.matcher(v).matches();
        if (!hasLower && !hasDigit) {
            return showHintError(passwordField, hintPassword,
                    "Cần thêm chữ thường và chữ số");
        }
        if (!hasLower) {
            return showHintError(passwordField, hintPassword,
                    "Cần thêm ít nhất 1 chữ thường");
        }
        if (!hasDigit) {
            return showHintError(passwordField, hintPassword,
                    "Cần thêm ít nhất 1 chữ số");
        }
        return showHintOk(passwordField, hintPassword);
    }

    private boolean validateConfirm() {
        String pw = passwordField.getText();
        String cf = confirmPasswordField.getText();
        if (cf == null || cf.isEmpty()) return clearHint(confirmPasswordField, hintConfirm);
        if (!cf.equals(pw)) {
            return showHintError(confirmPasswordField, hintConfirm, "Mật khẩu không khớp");
        }
        return showHintOk(confirmPasswordField, hintConfirm);
    }

    // =====================================================================
    //                       HELPER UI: hint + viền
    // =====================================================================

    /** Hiển thị hint đỏ + viền ô đỏ. */
    private boolean showHintError(javafx.scene.control.Control input, Label hint, String msg) {
        if (hint == null) return false;
        hint.getStyleClass().removeAll(CSS_HINT_OK, CSS_HINT_ERR);
        hint.getStyleClass().add(CSS_HINT_ERR);
        hint.setText(msg);
        if (!input.getStyleClass().contains(CSS_INPUT_ERR)) {
            input.getStyleClass().add(CSS_INPUT_ERR);
        }
        return false;
    }

    /**
     * Field hợp lệ
     */
    private boolean showHintOk(javafx.scene.control.Control input, Label hint) {
        if (hint != null) {
            hint.getStyleClass().removeAll(CSS_HINT_OK, CSS_HINT_ERR);
            hint.setText("");
        }
        input.getStyleClass().remove(CSS_INPUT_ERR);
        return true;
    }

    // Field rỗng
    private boolean clearHint(javafx.scene.control.Control input, Label hint) {
        if (hint != null) {
            hint.getStyleClass().removeAll(CSS_HINT_OK, CSS_HINT_ERR);
            hint.setText("");
        }
        input.getStyleClass().remove(CSS_INPUT_ERR);
        return false;
    }

    private void clearAllHints() {
        Label[] hints = {hintFullName, hintDob, hintPhone, hintEmail,
                         hintUsername, hintPassword, hintConfirm};
        for (Label l : hints) {
            if (l != null) {
                l.setText("");
                l.getStyleClass().removeAll(CSS_HINT_OK, CSS_HINT_ERR);
            }
        }
    }

    // =====================================================================
    //                          SUBMIT (Đăng ký)
    // =====================================================================

    @FXML
    void handleRegister() {
        clearError();

        // Validate lại tất cả field để hiện hint nếu còn trống/sai
        boolean ok = true;
        ok &= validateFullName();
        ok &= validateDob();
        ok &= validatePhone();
        ok &= validateEmail();
        ok &= validateUsername();
        ok &= validatePassword();
        ok &= validateConfirm();

        if (!ok) {
            // Có ít nhất 1 field rỗng hoặc sai
            String fullName  = safeTrim(fullNameField.getText());
            String phone     = safeTrim(phoneField.getText());
            String email     = safeTrim(emailField.getText());
            String username  = safeTrim(usernameField.getText());
            String password  = passwordField.getText();
            String confirm   = confirmPasswordField.getText();
            LocalDate dob    = dobPicker.getValue();

            if (fullName.isEmpty() || phone.isEmpty() || email.isEmpty()
                    || username.isEmpty() || password.isEmpty() || confirm.isEmpty()
                    || dob == null) {
                showError("Vui lòng điền đầy đủ tất cả các trường thông tin!");
            } else {
                showError("Vui lòng sửa các trường còn lỗi!");
            }
            return;
        }

        // Lấy giá trị đã được validate
        String fullName = safeTrim(fullNameField.getText());
        LocalDate dob   = dobPicker.getValue();
        String phone    = safeTrim(phoneField.getText());
        String email    = safeTrim(emailField.getText());
        String username = safeTrim(usernameField.getText());
        String password = passwordField.getText();

        // Check trùng với DB
        UserDAO userDAO = new UserDAO();
        if (userDAO.existsByUsername(username)) {
            showHintError(usernameField, hintUsername, "Tên đăng nhập đã được sử dụng");
            showError("Tên đăng nhập \"" + username + "\" đã được sử dụng. Vui lòng chọn tên khác!");
            return;
        }
        if (userDAO.existsByEmail(email)) {
            showHintError(emailField, hintEmail, "Email đã được đăng ký");
            showError("Email \"" + email + "\" đã được đăng ký. Vui lòng dùng email khác!");
            return;
        }

        // Hash password (BCrypt) trước khi lưu
        String hashedPassword = PasswordUtils.hash(password);

        String userId = "u-" + UUID.randomUUID().toString().substring(0, 8);
        User newUser = new Bidder(userId, username, email, hashedPassword);

        newUser.setFullName(fullName);
        newUser.setDateOfBirth(dob);
        newUser.setPhoneNumber(phone);

        if (!userDAO.save(newUser)) {
            showError("Không thể tạo tài khoản. Vui lòng thử lại sau!");
            return;
        }

        AlertHelper.show(
                AlertHelper.Type.SUCCESS,
                "Đăng ký thành công",
                "Tài khoản \"" + username + "\" đã được tạo. Bạn có thể đăng nhập ngay bây giờ."
        );
        AppState.getInstance().getSceneManager().showLogin();
    }

    @FXML
    void handleBackToLogin() {
        AppState.getInstance().getSceneManager().showLogin();
    }

    // =====================================================================
    //                          UTILITIES
    // =====================================================================

    private String safeTrim(String s) { return s == null ? "" : s.trim(); }

    private void showError(String msg) {
        messageLabel.setText(msg);
        messageLabel.setTextFill(Color.web("#EF4444"));
    }

    private void clearError() { messageLabel.setText(""); }
}