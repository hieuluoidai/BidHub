package model.manager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.auction.Auction;
import model.user.User;
import network.AuctionClient;
import utils.SceneManager;

import java.util.HashSet;
import java.util.Set;

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

    // Theo dõi các phiên mà user hiện tại đang có Auto-Bid hoạt động
    private final Set<String> myAutoBidIds = new HashSet<>();

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
    public SceneManager getSceneManager() {
        return sceneManager;
    }
    
    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    // Quản lý người dùng
    public User getCurrentUser() {
        return currentUser;
    }
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
        // Thông báo cho Server biết User nào đang ở connection này (để push real-time)
        if (user != null && client != null) {
            client.send("IDENTIFY:" + user.getUserId());
        }
    }

    // Quản lí kết nối
    public AuctionClient getClient() {
        return client;
    }

    public ObservableList<Auction> getAuctionList() {
        return auctionList;
    }

    /** Kiểm tra user hiện tại có Auto-Bid đang hoạt động cho phiên này không. */
    public boolean hasMyAutoBid(String auctionId) {
        return myAutoBidIds.contains(auctionId);
    }

    /** Cập nhật trạng thái Auto-Bid của user hiện tại cho một phiên cụ thể. */
    public void setMyAutoBid(String auctionId, boolean active) {
        if (active) {
            myAutoBidIds.add(auctionId);
        } else {
            myAutoBidIds.remove(auctionId);
        }
    }
}