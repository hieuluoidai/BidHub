package model.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

public class AuthRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        LOGIN,
        REGISTER
    }

    private final Type type;
    private final String username;
    private final String password;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final String phone;
    private final String email;

    private AuthRequest(Type type, String username, String password,
                        String fullName, LocalDate dateOfBirth,
                        String phone, String email) {
        this.type = type;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.email = email;
    }

    public static AuthRequest login(String username, String password) {
        return new AuthRequest(Type.LOGIN, username, password, null, null, null, null);
    }

    public static AuthRequest register(String fullName, LocalDate dateOfBirth, String phone,
                                       String email, String username, String password) {
        return new AuthRequest(Type.REGISTER, username, password, fullName, dateOfBirth, phone, email);
    }

    public Type getType() {
        return type;
    }

    public String getUsername() {
        return username;
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

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }
}
