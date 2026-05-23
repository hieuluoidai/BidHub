package utils;

import database.DatabaseConnection;
import database.UserDAO;

public class FixBidder {
    public static void main(String[] args) {
        UserDAO dao = new UserDAO();
        boolean success = dao.recalculateLockedBalance("u-003");
        if (success) {
            double locked = dao.getLockedBalance("u-003");
            double balance = dao.getBalance("u-003");
            System.out.println("RECALCULATE_SUCCESS");
            System.out.println("NEW_LOCKED: " + locked);
            System.out.println("NEW_BALANCE: " + balance);
        } else {
            System.out.println("RECALCULATE_FAILED");
        }
        DatabaseConnection.closePool();
    }
}
