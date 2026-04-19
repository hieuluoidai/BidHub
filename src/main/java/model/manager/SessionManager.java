package model.manager;

import model.user.User;

/**
 * Quản lý phiên đăng nhập và người dùng hiện tại	.
 */
public class SessionManager {
    private static SessionManager instance;
    private User currentUser;

    private SessionManager() {}

    // Singleton
    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    // Quản lý dữ liệu người dùng hiện tại
    public User getCurrentUser() { return currentUser; }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    // Đăng xuất và xóa thông tin người dùng hiện tại
    public void logout() {
        this.currentUser = null;
    }
}