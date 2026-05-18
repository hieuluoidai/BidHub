package model.user;

import model.core.Entity;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.Period;

/**
 * Abstract class đại diện cho người dùng chung trong hệ thống.
 *
 * Bổ sung trường BALANCE — phục vụ chức năng thanh toán phiên đấu giá:
 *   - User mới tạo: $0
 *   - Khi phiên FINISHED và có winner → trừ balance winner, cộng cho seller → PAID
 *   - Tương lai: nạp tiền, hold/refund khi cancel
 */
public abstract class User extends Entity implements Serializable {
    private static final long serialVersionUID = 5L;     // bumped vì thêm pending_seller

    private String username;
    private String email;
    private String password;        // Đã hash bằng BCrypt

    // Thông tin cá nhân
    private String fullName;
    private LocalDate dateOfBirth;
    private String phoneNumber;

    /** Số dư ví (đơn vị USD ảo). Mặc định 0 — DB sẽ điền giá trị thật khi load. */
    private double balance;

    /** Số dư bị khóa cho Auto-Bidding (Logic mới của Hiếu) */
    private double lockedBalance;

    /** Đường dẫn ảnh đại diện (avatar_path) */
    private String avatarPath;

    /** Trạng thái chờ xét duyệt lên Seller */
    private boolean pendingSeller;

    /**
     * Constructor cũ — giữ tương thích với code hiện hành.
     * Balance mặc định 0; UserDAO sẽ load giá trị thật từ DB.
     */
    public User(String userId, String username, String email, String password) {
        super(userId);
        this.username = username;
        this.email = email;
        this.password = password;
        this.balance = 0.0;
        this.lockedBalance = 0.0;
    }

    /** Abstract method để hiển thị role */
    public abstract void displayRole();

    /** Tính tuổi dựa vào ngày sinh. Trả về 0 nếu chưa có ngày sinh. */
    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    // Setters
    public void setUserId(String userId) {
        super.setId(userId);
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassWord(String password) {
        this.password = password;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void setLockedBalance(double lockedBalance) {
        this.lockedBalance = lockedBalance;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public void setPendingSeller(boolean pendingSeller) {
        this.pendingSeller = pendingSeller;
    }

    // Getters
    public String getUserId() {
        return super.getId();
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public double getBalance() {
        return balance;
    }

    public double getLockedBalance() {
        return lockedBalance;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public boolean isPendingSeller() {
        return pendingSeller;
    }
}
