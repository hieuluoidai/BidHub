package model.user;

import model.core.Entity;
import java.io.Serializable;

/**
 * Abstract class đại diện cho người dùng chung trong hệ thống.
 */
public abstract class User extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String email;
    private String password;

    public User(String userId, String username, String email, String password) {
        super(userId);
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // Abstract method để hiển thị role
    public abstract void displayRole();

    // Setters
    public void setUserId(String userId)     { super.setId(userId); }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email)       { this.email = email; }
    public void setPassWord(String password) { this.password = password; }

    // Getters
    public String getUserId()   { return super.getId(); }
    public String getUsername() { return username; }
    public String getEmail()    { return email; }
    public String getPassword() { return password; }
}