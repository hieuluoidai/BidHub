package model.manager;

import model.user.User;
import network.AuctionClient;

public class AppState {
    private static AppState instance;
    private User currentUser;
    private final AuctionClient client;

    private AppState() {
        this.client = new AuctionClient();
    }

    public static synchronized AppState getInstance() {
        if (instance == null) {
            instance = new AppState();
        }
        return instance;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public AuctionClient getClient() {
        return client;
    }
}