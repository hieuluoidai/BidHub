package model.manager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.auction.Auction;
import model.user.User;
import network.AuctionClient;
import utils.SceneManager;

public class AppState {
    private static AppState instance;
    private User currentUser;
    private final AuctionClient client;
    private SceneManager sceneManager; // Biến để quản lý chuyển cảnh
    private final ObservableList<Auction> auctionList = FXCollections.observableArrayList();

    private AppState() {
        this.client = new AuctionClient();
    }

    public static synchronized AppState getInstance() {
        if (instance == null) {
            instance = new AppState();
        }
        return instance;
    }

    // Getter và Setter cho SceneManager
    public SceneManager getSceneManager() {
        return sceneManager;
    }

    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    // Các hàm khác giữ nguyên
    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public AuctionClient getClient() {
        return client;
    }

    public ObservableList<Auction> getAuctionList() {
        return auctionList;
    }
}