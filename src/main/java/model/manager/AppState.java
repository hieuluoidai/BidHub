package model.manager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.auction.Auction;
import model.user.User;
import network.AuctionClient;
import utils.SceneManager;

/**
 * Quản lý trạng thái toàn cục của ứng dụng phía Client (áp dụng Singleton Pattern).
 * Cầu nối giữa UI và Network.
 */
public class AppState {
    private static AppState instance;
    
    private User currentUser;                    // Người dùng hiện đang đăng nhập
    private final AuctionClient client;          // Cổng kết nối Socket tới Server
    private SceneManager sceneManager;           // Bộ điều hướng chuyển đổi màn hình
    
    // Danh sách đấu giá tự động update lên giao diện JavaFX
    private final ObservableList<Auction> auctionList = FXCollections.observableArrayList();

    private AppState() {
        this.client = new AuctionClient();
    }

    // Singleton
    public static synchronized AppState getInstance() {
        if (instance == null) {
            instance = new AppState();
        }
        return instance;
    }

    // Quản lý giao diện
    public SceneManager getSceneManager() { return sceneManager; }
    
    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    // Quản lý người dùng
    public User getCurrentUser() { return currentUser; }
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    // Quản lí kết nối
    public AuctionClient getClient() { return client; }

    public ObservableList<Auction> getAuctionList() {
        return auctionList;
    }
}