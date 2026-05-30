package model.auth;

import java.io.Serial;
import java.io.Serializable;
import model.user.User;

public class AuthResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final User user;

    private AuthResponse(boolean success, String message, User user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public static AuthResponse success(User user, String message) {
        return new AuthResponse(true, message, user);
    }

    public static AuthResponse failure(String message) {
        return new AuthResponse(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public User getUser() {
        return user;
    }
}
