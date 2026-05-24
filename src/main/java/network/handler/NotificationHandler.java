package network.handler;

import database.NotificationDAO;
import model.notification.Notification;
import network.ClientHandler;

import java.util.List;

/**
 * Handles notification-related commands.
 */
public class NotificationHandler implements RequestHandler {

    @Override
    public void handle(ClientHandler context, String msg) {
        if (msg.startsWith("FETCH_NOTIFICATIONS:")) {
            handleFetchNotifications(context, msg);
        } else if (msg.startsWith("MARK_NOTIFICATION_READ:")) {
            handleMarkNotificationRead(context, msg);
        } else if (msg.startsWith("MARK_ALL_NOTIFICATIONS_READ:")) {
            handleMarkAllNotificationsRead(context, msg);
        }
    }

    private void handleFetchNotifications(ClientHandler context, String msg) {
        String userId = msg.split(":")[1];
        NotificationDAO notifDao = new NotificationDAO();
        List<Notification> list = notifDao.findRecent(userId, 20);
        context.send(new Notification.Bundle(list));
    }

    private void handleMarkNotificationRead(ClientHandler context, String msg) {
        String notifId = msg.split(":")[1];
        new NotificationDAO().markAsRead(notifId);
    }

    private void handleMarkAllNotificationsRead(ClientHandler context, String msg) {
        String userId = msg.split(":")[1];
        new NotificationDAO().markAllAsRead(userId);
    }
}
