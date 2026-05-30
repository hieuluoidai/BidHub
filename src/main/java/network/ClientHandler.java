package network;

import database.AuctionDAO;
import database.BidTransactionDAO;
import database.ItemDAO;
import database.UserDAO;
import exception.ErrorCode;
import exception.ErrorResponse;
import exception.ExceptionMapper;
import model.auth.AuthRequest;
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

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

/**
 * Xu ly tung client ket noi toi server.
 */
public class ClientHandler implements Runnable {
    private static final byte[] OBJECT_STREAM_HEADER =
            new byte[]{(byte) 0xAC, (byte) 0xED, 0x00, 0x05};

    private final Socket socket;
    private final AuctionServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String currentUserId;
    private boolean isAdminCached = false;
    private boolean active = true;

    private static final Map<String, RequestHandler> HANDLERS = new HashMap<>();
    private static final UserHandler USER_HANDLER = new UserHandler();

    static {
        AuctionHandler auctionHandler = new AuctionHandler();
        WalletHandler walletHandler = new WalletHandler();
        SocialHandler socialHandler = new SocialHandler();
        NotificationHandler notificationHandler = new NotificationHandler();

        HANDLERS.put("BID:", auctionHandler);
        HANDLERS.put("DELETE_AUCTION:", auctionHandler);
        HANDLERS.put("CANCEL_AUCTION:", auctionHandler);
        HANDLERS.put("PAY_AUCTION:", auctionHandler);
        HANDLERS.put("SET_AUTOBID:", auctionHandler);
        HANDLERS.put("CANCEL_AUTOBID:", auctionHandler);
        HANDLERS.put("GET_MY_AUTOBID:", auctionHandler);
        HANDLERS.put("RELOAD_AUCTION:", auctionHandler);
        HANDLERS.put("REFRESH_DATA", auctionHandler);

        HANDLERS.put("IDENTIFY:", USER_HANDLER);
        HANDLERS.put("UPDATE_PROFILE:", USER_HANDLER);
        HANDLERS.put("UPDATE_AVATAR:", USER_HANDLER);
        HANDLERS.put("CHANGE_PASSWORD:", USER_HANDLER);
        HANDLERS.put("NEW_USER_REGISTERED:", USER_HANDLER);
        HANDLERS.put("REQUEST_SELLER:", USER_HANDLER);
        HANDLERS.put("APPROVE_SELLER:", USER_HANDLER);
        HANDLERS.put("REVOKE_SELLER:", USER_HANDLER);

        HANDLERS.put("TOPUP:", walletHandler);
        HANDLERS.put("DEPOSIT_REQUEST:", walletHandler);
        HANDLERS.put("DEPOSIT_REVIEW:", walletHandler);

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
        refreshAdminCache();
    }

    public void refreshAdminCache() {
        if (currentUserId != null) {
            User receiver = new UserDAO().findById(currentUserId);
            this.isAdminCached = receiver instanceof Admin;
        }
    }

    public boolean isAdmin() {
        return isAdminCached;
    }

    public AuctionServer getServer() {
        return server;
    }

    @Override
    public void run() {
        try {
            PushbackInputStream rawIn = new PushbackInputStream(
                    socket.getInputStream(), OBJECT_STREAM_HEADER.length);
            if (!hasValidStreamHeader(rawIn)) {
                active = false;
                return;
            }

            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(rawIn);

            while (active) {
                Object request = in.readObject();
                try {
                    handleRequest(request);
                } catch (Exception e) {
                    System.err.println(">>> Loi xu ly request: " + ExceptionMapper.logMessage(e));
                    send(ErrorResponse.from(e));
                }
            }
        } catch (EOFException | SocketException e) {
            if (currentUserId != null) {
                System.out.println(">>> Thong bao: Client " + currentUserId + " da thoat.");
            }
        } catch (Exception e) {
            System.err.println(">>> Loi ClientHandler: " + ExceptionMapper.logMessage(e));
        } finally {
            close();
        }
    }

    private void handleRequest(Object request) {
        if (request instanceof AuthRequest authRequest) {
            USER_HANDLER.handleAuth(this, authRequest);
        } else if (request instanceof Auction incomingAuction) {
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
                System.out.println(">>> [SERVER] Da tiep nhan va luu phien moi: "
                        + incomingAuction.getAuctionId());

                NotificationService.notifyUser(server, incomingAuction.getSellerId(),
                        Notification.Type.ITEM_POSTED,
                        "Dang ban thanh cong",
                        String.format("San pham \"%s\" cua ban da duoc dang len he thong.",
                                incomingAuction.getItem().getItemName()));

                NotificationService.notifyAdmins(server,
                        Notification.Type.ADMIN_NEW_AUCTION,
                        "Phien dau gia moi",
                        String.format("Phien dau gia cho \"%s\" vua duoc tao boi %s.",
                                incomingAuction.getItem().getItemName(), incomingAuction.getSellerId()));
            }
            server.broadcast(incomingAuction);
        } else if (request instanceof String msg) {
            handleStringRequest(msg);
        } else {
            send(ErrorResponse.of(ErrorCode.COMMAND_FORMAT_INVALID,
                    "Server khong ho tro kieu du lieu request nay."));
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
            System.err.println(">>> Loi xu ly lenh: " + ExceptionMapper.logMessage(e));
            send(ExceptionMapper.protocolMessage("ERROR", e));
        }
    }

    private void saveBidToDatabase(Auction auction) {
        BidTransactionDAO bidDao = new BidTransactionDAO();
        String auctionId = auction.getAuctionId();
        model.auction.BidTransaction highest = auction.getHighestBid();

        if (highest == null) {
            return;
        }

        String bidderId = highest.getBidder().getUserId();
        double amount = highest.getBidAmount();
        java.time.LocalDateTime time = highest.getTimestamp();
        model.auction.BidTransaction.BidType type = highest.getBidType();

        if (bidDao.save(auctionId, bidderId, amount, type, time,
                highest.isAnonymous(), highest.getAnonymousDisplayName())) {
            System.out.println(">>> Backup thanh cong gia $" + amount + " cho phien " + auctionId);
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
        if (isAdminCached) {
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
                Auction safeAuction = new Auction(
                        auction.getAuctionId(), auction.getItem(), auction.getStartTime(), auction.getEndTime());
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
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.removeClient(this);
        }
    }

    private boolean hasValidStreamHeader(PushbackInputStream rawIn) throws IOException {
        byte[] header = new byte[OBJECT_STREAM_HEADER.length];
        int read = rawIn.read(header);
        if (read < OBJECT_STREAM_HEADER.length) {
            return false;
        }
        rawIn.unread(header, 0, read);

        for (int i = 0; i < OBJECT_STREAM_HEADER.length; i++) {
            if (header[i] != OBJECT_STREAM_HEADER[i]) {
                return false;
            }
        }
        return true;
    }
}
