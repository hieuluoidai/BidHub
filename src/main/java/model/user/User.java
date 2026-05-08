package model.user;

import model.core.Entity;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.Period;

/**
 * Abstract class đại diện cho người dùng chung trong hệ thống.
 */
public abstract class User extends Entity implements Serializable {
    private static final long serialVersionUID = 2L;

    private String username;
    private String email;
    private String password;        // Đã hash bằng BCrypt

    // Thông tin cá nhân (bổ sung khi đăng ký)
    private String fullName;
    private LocalDate dateOfBirth;
    private String phoneNumber;

    /**
     * Constructor cũ
     */
    public User(String userId, String username, String email, String password) {
        super(userId);
        this.username = username;
        this.email = email;
        this.password = password;
    }

    /** Abstract method để hiển thị role */
    public abstract void displayRole();

    /**
     * Tính tuổi dựa vào ngày sinh. Trả về 0 nếu chưa có ngày sinh.
     */
    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    // Setters
    public void setUserId(String userId)              { super.setId(userId); }
    public void setUsername(String username)          { this.username = username; }
    public void setEmail(String email)                { this.email = email; }
    public void setPassWord(String password)          { this.password = password; }
    public void setFullName(String fullName)          { this.fullName = fullName; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setPhoneNumber(String phoneNumber)    { this.phoneNumber = phoneNumber; }

    // Getters
    public String    getUserId()      { return super.getId(); }
    public String    getUsername()    { return username; }
    public String    getEmail()       { return email; }
    public String    getPassword()    { return password; }
    public String    getFullName()    { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String    getPhoneNumber() { return phoneNumber; }
}
