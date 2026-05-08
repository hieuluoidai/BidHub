package network;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;
import model.auction.Auction;
import model.auction.BidResult;

/**
 * Lớp điều phối liên lạc với Server và xử lý dữ liệu phía Client.
 */
public class AuctionClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isRunning = false;

    /** Callback nhận BidResult. */
    private volatile Consumer<BidResult> onBidResult;

    public void connect(String host, int port) throws IOException {
        if (socket != null && !socket.isClosed()) return;

        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        isRunning = true;

        Thread listenerThread = new Thread(this::listen);
        listenerThread.setDaemon(true);
        listenerThread.start();

        System.out.println(">>> Đã kết nối tới Server tại " + host + ":" + port);
    }

    /**
     * Đăng ký callback nhận BidResult. Truyền null để hủy đăng ký.
     */
    public void setOnBidResult(Consumer<BidResult> handler) {
        this.onBidResult = handler;
    }

    private void listen() {
        try {
            while (isRunning) {
                Object data = in.readObject();
                if (data != null) {
                    handleIncomingData(data);
                }
            }
        } catch (Exception e) {
            System.err.println(">>> Mất kết nối tới Server: " + e.getMessage());
            close();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleIncomingData(Object data) {
        System.out.println(">>> [CLIENT] Nhận data kiểu: " + data.getClass().getSimpleName());

        Platform.runLater(() -> {
            if (data instanceof BidResult result) {
                System.out.println(">>> [CLIENT] BidResult: " + result);
                Consumer<BidResult> handler = this.onBidResult;
                if (handler != null) handler.accept(result);

            } else if (data instanceof List<?>) {
                List<Auction> auctions = (List<Auction>) data;
                System.out.println(">>> [CLIENT] Danh sách " + auctions.size() + " phiên");
                model.manager.AppState.getInstance().getAuctionList().setAll(auctions);

            } else if (data instanceof Auction updatedAuction) {
                System.out.println(">>> [CLIENT] 1 auction: " + updatedAuction.getAuctionId()
                        + " status=" + updatedAuction.getStatus());
                updateSingleAuction(updatedAuction);
            }
        });
    }

    public void send(Object data) {
        try {
            if (out != null) {
                out.writeObject(data);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            System.err.println(">>> Lỗi khi gửi dữ liệu: " + e.getMessage());
        }
    }

    public void close() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateSingleAuction(model.auction.Auction updated) {
        var list = model.manager.AppState.getInstance().getAuctionList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getAuctionId().equals(updated.getAuctionId())) {
                list.set(i, updated);
                return;
            }
        }
        list.add(updated);
    }
}
