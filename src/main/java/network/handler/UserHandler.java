package network.handler;

import database.UserDAO;
import model.notification.Notification;
import model.user.User;
import network.ClientHandler;
import utils.NotificationService;
import model.manager.AuctionManager;

/**
 * Handles user-related commands.
 */
public class UserHandler implements RequestHandler {

    @Override
    public void handle(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (msg.startsWith("IDENTIFY:")) {
            if (parts.length >= 2) {
                context.setUserId(parts[1]);
                System.out.println(">>> [SERVER] Connection tagged as user: " + context.getUserId());
            }
        } else if (msg.startsWith("UPDATE_PROFILE:")) {
            handleUpdateProfile(context, msg);
        } else if (msg.startsWith("UPDATE_AVATAR:")) {
            handleUpdateAvatar(context, msg);
        } else if (msg.startsWith("CHANGE_PASSWORD:")) {
            handleChangePassword(context, msg);
        } else if (msg.startsWith("NEW_USER_REGISTERED:")) {
            handleNewUserRegistered(context, msg);
        } else if (msg.startsWith("REQUEST_SELLER:")) {
            handleRequestSeller(context, msg);
        } else if (msg.startsWith("APPROVE_SELLER:")) {
            handleApproveSeller(context, msg);
        } else if (msg.startsWith("REVOKE_SELLER:")) {
            handleRevokeSeller(context, msg);
        }
    }

    private void handleUpdateProfile(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 5) return;
        
        String userId = parts[1];
        String email  = parts[2];
        String phone  = parts[3];
        String dobStr = parts[4];

        UserDAO dao = new UserDAO();
        boolean ok = dao.updateProfile(userId, email, phone, dobStr);
        
        if (ok) {
            context.send("UPDATE_PROFILE_OK");
        } else {
            context.send("UPDATE_PROFILE_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    private void handleUpdateAvatar(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 3) return;
        
        String userId = parts[1];
        String avatarPath = parts[2];

        if (parts.length > 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                sb.append(parts[i]);
                if (i < parts.length - 1) sb.append(":");
            }
            avatarPath = sb.toString();
        }

        UserDAO dao = new UserDAO();
        boolean ok = dao.updateAvatar(userId, avatarPath);
        
        if (ok) {
            context.send("UPDATE_AVATAR_OK");
        } else {
            context.send("UPDATE_AVATAR_FAILED: Lỗi cơ sở dữ liệu");
        }
    }

    private void handleChangePassword(ClientHandler context, String msg) {
        String[] parts = msg.split(":");
        if (parts.length < 4) return;

        String userId  = parts[1];
        String oldPass = parts[2];
        String newPass = parts[3];

        if (context.getUserId() == null || !context.getUserId().equals(userId)) {
            context.send("CHANGE_PASSWORD_FAILED: Bạn không có quyền đổi mật khẩu của người khác!");
            return;
        }

        UserDAO dao = new UserDAO();
        User user = dao.findById(userId);

        if (user == null) {
            context.send("CHANGE_PASSWORD_FAILED: User không tồn tại");
            return;
        }

        if (!utils.PasswordUtils.verify(oldPass, user.getPassword())) {
            context.send("CHANGE_PASSWORD_FAILED: Mật khẩu cũ không chính xác");
            return;
        }

        String hashedNew = utils.PasswordUtils.hash(newPass);
        boolean ok = dao.updatePassword(userId, hashedNew);

        if (ok) {
            context.send("CHANGE_PASSWORD_OK");
        } else {
            context.send("CHANGE_PASSWORD_FAILED: Lỗi DB");
        }
    }

    private void handleNewUserRegistered(ClientHandler context, String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        User u = userDao.findById(userId);
        String username = (u != null) ? u.getUsername() : userId;

        System.out.println(">>> [NEW_USER] User " + username + " registered.");
        
        NotificationService.notifyAdmins(context.getServer(), 
            Notification.Type.ADMIN_NEW_USER, 
            "Người dùng mới", 
            "Người dùng " + username + " vừa đăng ký tài khoản mới.");

        context.getServer().broadcastToRole("ADMIN", "USERS_UPDATED");
    }

    private void handleRequestSeller(ClientHandler context, String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        if (userDao.updatePendingSeller(userId, true)) {
            System.out.println(">>> [SELLER_REQ] User " + userId + " requested to become Seller.");
            context.getServer().broadcastToRole("ADMIN", "NEW_SELLER_REQUEST:" + userId);
            
            User u = userDao.findById(userId);
            String username = (u != null) ? u.getUsername() : userId;
            NotificationService.notifyAdmins(context.getServer(), 
                Notification.Type.ADMIN_NEW_SELLER_REQUEST, 
                "Yêu cầu nâng cấp Seller", 
                "Người dùng " + username + " đang chờ bạn phê duyệt quyền Seller.");
        }
    }

    private void handleApproveSeller(ClientHandler context, String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        if (userDao.approveSeller(userId)) {
            System.out.println(">>> [SELLER_APP] User " + userId + " approved as Seller.");
            
            context.getServer().sendToUser(userId, "SELLER_APPROVED");

            NotificationService.notifyUser(context.getServer(), userId, 
                Notification.Type.SELLER_APPROVED, 
                "Chúc mừng! Bạn đã là Seller", 
                "Tài khoản của bạn đã được Admin phê duyệt quyền Seller. "
                + "Bây giờ bạn có thể đăng bán sản phẩm của riêng mình.");

            context.getServer().broadcastToRole("ADMIN", "USERS_UPDATED");
            context.getServer().broadcast(AuctionManager.getInstance().getAllAuctions());
        }
    }

    private void handleRevokeSeller(ClientHandler context, String msg) {
        String userId = msg.split(":")[1];
        UserDAO userDao = new UserDAO();
        if (userDao.revokeSeller(userId)) {
            System.err.println(">>> [SELLER_REVOKE] User " + userId + " revoked from Seller.");
            
            context.getServer().sendToUser(userId, "SELLER_REVOKED");

            NotificationService.notifyUser(context.getServer(), userId, 
                Notification.Type.SELLER_REVOKED, 
                "Thông báo thay đổi quyền hạn", 
                "Admin đã hủy quyền Seller của bạn. Tài khoản đã được chuyển về vai trò BIDDER.");

            context.getServer().broadcastToRole("ADMIN", "USERS_UPDATED");
            context.getServer().broadcast(AuctionManager.getInstance().getAllAuctions());
        }
    }
}
