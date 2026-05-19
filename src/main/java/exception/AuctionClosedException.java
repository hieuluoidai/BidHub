package exception;

public class AuctionClosedException extends RuntimeException {
    private final String auctionId;
    private final String status;

    public AuctionClosedException(String auctionId, String status) {
        super(String.format("Phiên đấu giá '%s' không thể đặt giá (Trạng thái: %s)",
                auctionId, status));
        this.auctionId = auctionId;
        this.status = status;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getStatus() {
        return status;
    }
}
