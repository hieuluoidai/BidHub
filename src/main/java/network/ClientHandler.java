package network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import database.AuctionDAO;
import database.BidTransactionDAO;
import database.ItemDAO;
import database.UserDAO;
import exception.ErrorCode;
import exception.ErrorResponse;
import exception.ExceptionMapper;
import model.auction.Auction;
import model.manager.AuctionManager;
import model.notification.Notification;
import model.user.Admin;
import model.user.User;
import network.handler.AuctionHandler;
import network.handler.NotificationHandler;
import network.handler.RequestHandler;
import network.handler.SocialHandler;
import network.handler.UserHandler;
import network.handler.WalletHandler;
import utils.NotificationService;

/**
 * Xử lý từng Client kết nối tới Server.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String currentUserId; // Lưu ID người dùng của connection này
    private boolean active = true;

    private static final Map<String, RequestHandler> HANDLERS = new HashMap<>();
    static {
        AuctionHandler auctionHandler = new AuctionHandler();
        UserHandler userHandler = new UserHandler();
        WalletHandler walletHandler = new WalletHandler();
        SocialHandler socialHandler = new SocialHandler();
        NotificationHandler notificationHandler = new NotificationHandler();

        // Auction commands
        HANDLERS.put("BID:", auctionHandler);
        HANDLERS.put("DELETE_AUCTION:", auctionHandler);
        HANDLERS.put("CANCEL_AUCTION:", auctionHandler);
        HANDLERS.put("PAY_AUCTION:", auctionHandler);
        HANDLERS.put("SET_AUTOBID:", auctionHandler);
        HANDLERS.put("CANCEL_AUTOBID:", auctionHandler);
        HANDLERS.put("GET_MY_AUTOBID:", auctionHandler);
        HANDLERS.put("RELOAD_AUCTION:", auctionHandler);
        HANDLERS.put("REFRESH_DATA", auctionHandler);

        // User commands
        HANDLERS.put("IDENTIFY:", userHandler);
        HANDLERS.put("UPDATE_PROFILE:", userHandler);
        HANDLERS.put("UPDATE_AVATAR:", userHandler);
        HANDLERS.put("CHANGE_PASSWORD:", userHandler);
        HANDLERS.put("NEW_USER_REGISTERED:", userHandler);
        HANDLERS.put("REQUEST_SELLER:", userHandler);
        HANDLERS.put("APPROVE_SELLER:", userHandler);
        HANDLERS.put("REVOKE_SELLER:", userHandler);

        // Wallet commands
        HANDLERS.put("TOPUP:", walletHandler);
        HANDLERS.put("DEPOSIT_REQUEST:", walletHandler);
        HANDLERS.put("DEPOSIT_REVIEW:", walletHandler);

        // Social commands
        HANDLERS.put("CHAT_SEND:", socialHandler);
        HANDLERS.put("CHAT_FETCH:", socialHandler);
        HANDLERS.put("CHAT_FETCH_LIST:", socialHandler);
        HANDLERS.put("CHAT_MARK_READ:", socialHandler);
        HANDLERS.put("CHAT_LIKE:", socialHandler);
        HANDLERS.put("CHAT_RECALL:", socialHandler);
        HANDLERS.put("FRIEND_REQUEST:", socialHandler);
        HANDLERS.put("FRIEND_ACCEPT:", socialHandler);
        HANDLERS.put("FRIEND_DECLINE:", socialHandler);
        HANDLERS.put("FRIEND_LIST:", socialHandler);
        HANDLERS.put("FRIEND_STATUS:", socialHandler);
        HANDLERS.put("USER_SEARCH:", socialHandler);

        // Notification commands
        HANDLERS.put("FETCH_NOTIFICATIONS:", notificationHandler);
        HANDLERS.put("MARK_NOTIFICATION_READ:", notificationHandler);
        HANDLERS.put("MARK_ALL_NOTIFICATIONS_READ:", notificationHandler);
    }

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
    }

    public boolean isAlive() {
        return active && !socket.isClosed();
    }

    public String getUserId() {
        return currentUserId;
    }

    public void setUserId(String userId) {
        this.currentUserId = userId;
    }

    public AuctionServer getServer() {
        return server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (active) {
                Object request = in.readObject();
                try {
                    handleRequest(request);
                } catch (Exception e) {
                    System.err.println(">>> Lỗi xử lý request: " + ExceptionMapper.logMessage(e));
                    send(ErrorResponse.from(e));
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println(">>> Thông báo: Một Client đã thoát.");
        } catch (Exception e) {
            System.err.println(">>> Lỗi ClientHandler: " + ExceptionMapper.logMessage(e));
        } finally {
            close();
        }
    }

    private void handleRequest(Object request) {
        if (request instanceof Auction incomingAuction) {
            if (incomingAuction.getSellerId() != null) {
                this.currentUserId = incomingAuction.getSellerId();
            }
            
            Auction existing = AuctionManager.getInstance().getAuctionById(incomingAuction.getAuctionId());

            if (existing != null) {
                AuctionManager.getInstance().updateAuction(incomingAuction);
                if (incomingAuction.getHighestBid() != null) {
                    saveBidToDatabase(incomingAuction);
                }
            } else {
                new ItemDAO().save(incomingAuction.getItem(), incomingAuction.getSellerId());
                new AuctionDAO().save(incomingAuction);
                
                AuctionManager.getInstance().addAuction(incomingAuction);
                System.out.println(">>> [SERVER] Đã tiếp nhận và lưu phiên mới: " + incomingAuction.getAuctionId());

                NotificationService.notifyUser(server, incomingAuction.getSellerId(),
                    Notification.Type.ITEM_POSTED,
                    "Đăng bán thành công",
                    String.format("Sản phẩm \"%s\" của bạn đã được đăng lên hệ thống.", 
                        incomingAuction.getItem().getItemName()));

                NotificationService.notifyAdmins(server,
                    Notification.Type.ADMIN_NEW_AUCTION,
                    "Phiên đấu giá mới",
                    String.format("Phiên đấu giá cho \"%s\" vừa được tạo bởi %s.", 
                        incomingAuction.getItem().getItemName(), incomingAuction.getSellerId()));
            }
            server.broadcast(incomingAuction);
        } else if (request instanceof String msg) {
            handleStringRequest(msg);
        } else {
            send(ErrorResponse.of(ErrorCode.COMMAND_FORMAT_INVALID,
                    "Server không hỗ trợ kiểu dữ liệu request này."));
        }
    }

    private void handleStringRequest(String msg) {
        try {
            boolean handled = false;
            for (Map.Entry<String, RequestHandler> entry : HANDLERS.entrySet()) {
                String commandPrefix = entry.getKey();
                if (msg.startsWith(commandPrefix)) {
                    entry.getValue().handle(this, msg);
                    handled = true;
                    break;
                }
            }

            if (!handled) {
                System.err.println(">>> [SERVER] Unhandled command: " + msg);
            }
        } catch (Exception e) {
            System.err.println(">>> Lỗi xử lý lệnh: " + ExceptionMapper.logMessage(e));
            send(ExceptionMapper.protocolMessage("ERROR", e));
        }
    }

    private void saveBidToDatabase(Auction auction) {
        BidTransactionDAO bidDao = new BidTransactionDAO();
        String auctionId = auction.getAuctionId();
        model.auction.BidTransaction highest = auction.getHighestBid();
        
        if (highest == null) return;
        
        String bidderId = highest.getBidder().getUserId();
        double amount = highest.getBidAmount();
        java.time.LocalDateTime time = highest.getTimestamp();
        model.auction.BidTransaction.BidType type = highest.getBidType();

        if (bidDao.save(auctionId, bidderId, amount, type, time)) {
            System.out.println(">>> Backup thành công giá $" + amount + " cho phiên " + auctionId);
        }
    }

    public void send(Object data) {
        try {
            if (out != null) {
                Object safeData = sanitizeData(data);
                out.writeObject(safeData);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            active = false;
        }
    }

    private Object sanitizeData(Object data) {
        boolean isAdmin = false;
        if (currentUserId != null) {
            User receiver = new UserDAO().findById(currentUserId);
            isAdmin = (receiver instanceof Admin);
        }

        if (isAdmin) {
            return data;
        }

        if (data instanceof Auction auction) {
            boolean hasAnon = false;
            for (model.auction.BidTransaction tx : auction.getBidHistory()) {
                if (tx.isAnonymous()) {
                    hasAnon = true;
                    break;
                }
            }

            if (hasAnon) {
                Auction safeAuction = new Auction(auction.getAuctionId(), auction.getItem(), 
                                                 auction.getStartTime(), auction.getEndTime());
                safeAuction.setSellerId(auction.getSellerId());
                safeAuction.setStatus(auction.getStatus());
                
                java.util.List<model.auction.BidTransaction> safeHistory = new java.util.ArrayList<>();
                for (model.auction.BidTransaction tx : auction.getBidHistory()) {
                    if (tx.isAnonymous()) {
                        User fakeUser = new model.user.Bidder(
                                "anon_id", 
                                tx.getAnonymousDisplayName(), 
                                "anon@hidden.com", 
                                ""
                        );
                        fakeUser.setAvatarPath("https://cdn-icons-png.flaticon.com/512/3208/3208903.png");
                        
                        model.auction.BidTransaction safeTx = new model.auction.BidTransaction(
                                fakeUser, tx.getBidAmount(), tx.getTimestamp(), tx.getBidType(), true
                        );
                        safeTx.setAnonymousDisplayName(tx.getAnonymousDisplayName());
                        safeHistory.add(safeTx);
                    } else {
                        safeHistory.add(tx);
                    }
                }
                safeAuction.restoreBidHistory(safeHistory);
                return safeAuction;
            }
        } else if (data instanceof java.util.List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof Auction) {
                java.util.List<Auction> safeList = new java.util.ArrayList<>();
                for (Object item : list) {
                    safeList.add((Auction) sanitizeData(item));
                }
                return safeList;
            }
        }

        return data;
    }

    private void close() {
        active = false;
        try {
            if (socket != null) socket.close();
            server.removeObserver(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
