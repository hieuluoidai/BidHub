package model.auction;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Class represents an auto-bid configuration for a user in an auction.
 */
public class AutoBid implements Serializable {
    private static final long serialVersionUID = 1L;

    private String autoBidId;
    private String auctionId;
    private String userId;
    private double maxBid;
    private double increment;
    private LocalDateTime createdAt;

    public AutoBid(String autoBidId, String auctionId, String userId, double maxBid, double increment) {
        this.autoBidId = autoBidId;
        this.auctionId = auctionId;
        this.userId = userId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters & Setters ---
    public String getAutoBidId()   { return autoBidId; }
    public String getAuctionId()   { return auctionId; }
    public String getUserId()      { return userId; }
    public double getMaxBid()      { return maxBid; }
    public double getIncrement()   { return increment; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setMaxBid(double maxBid)       { this.maxBid = maxBid; }
    public void setIncrement(double increment) { this.increment = increment; }
    public void setCreatedAt(LocalDateTime t)  { this.createdAt = t; }

    @Override
    public String toString() {
        return String.format("AutoBid[ID=%s, User=%s, Max=$%.2f, Inc=$%.2f]", 
            autoBidId, userId, maxBid, increment);
    }
}
