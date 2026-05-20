package model.manager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import model.auction.Auction;
import model.user.User;
import network.AuctionClient;
import utils.SceneManager;

import java.util.HashSet;

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
    private final ObservableSet<String> myAutoBidIds = FXCollections.observableSet(new HashSet<>());

    // Theo dõi các phiên mà user đã đánh dấu sao (watchlist)
    private final ObservableSet<String> starredAuctionIds = FXCollections.observableSet(new HashSet<>());

    // Tổng số tin nhắn chưa đọc (badge sidebar "Tin nhắn")
    private final javafx.beans.property.IntegerProperty totalUnreadChat =
            new javafx.beans.property.SimpleIntegerProperty(0);

    // Số lời mời kết bạn chưa xử lý (badge tab "Bạn bè")
    private final javafx.beans.property.IntegerProperty pendingFriendCount =
            new javafx.beans.property.SimpleIntegerProperty(0);

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
        if (user == null) {
            myAutoBidIds.clear();
            starredAuctionIds.clear();
            totalUnreadChat.set(0);
        }
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

    public ObservableSet<String> getMyAutoBidIds() {
        return myAutoBidIds;
    }

    public ObservableSet<String> getStarredAuctionIds() {
        return starredAuctionIds;
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

    /** Kiểm tra phiên có được đánh dấu sao không. */
    public boolean isStarred(String auctionId) {
        return starredAuctionIds.contains(auctionId);
    }

    /** Đánh dấu/Bỏ đánh dấu sao. */
    public void setStarred(String auctionId, boolean starred) {
        if (starred) {
            starredAuctionIds.add(auctionId);
        } else {
            starredAuctionIds.remove(auctionId);
        }
    }

    public javafx.beans.property.IntegerProperty totalUnreadChatProperty() {
        return totalUnreadChat;
    }

    public int getTotalUnreadChat() {
        return totalUnreadChat.get();
    }

    public void setTotalUnreadChat(int v) {
        totalUnreadChat.set(v);
    }

    public javafx.beans.property.IntegerProperty pendingFriendCountProperty() {
        return pendingFriendCount;
    }
    public int getPendingFriendCount() { return pendingFriendCount.get(); }
    public void setPendingFriendCount(int v) { pendingFriendCount.set(v); }

    /** Hook để mở chat từ bất kỳ controller nào (vd: item_details → mở tab Tin nhắn của dashboard). */
    private java.util.function.Consumer<String[]> openChatHook;

    public void setOpenChatHook(java.util.function.Consumer<String[]> hook) {
        this.openChatHook = hook;
    }

    public void requestOpenChat(String partnerId, String partnerUsername, String partnerAvatarPath) {
        if (openChatHook != null) {
            openChatHook.accept(new String[]{partnerId, partnerUsername, partnerAvatarPath});
        }
    }
}
