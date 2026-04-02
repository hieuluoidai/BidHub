package model.auction;

import model.core.Entity;
import model.item.Item;
import model.user.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    private Item item;
    private List<BidTransaction> bidHistory;
    private BidTransaction highestBid;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; 

    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item = item;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "OPEN"; 
        this.bidHistory = new ArrayList<>();
    }

    public void placeBid(User bidder, double amount) throws IllegalStateException, IllegalArgumentException {
        // Exception: Bidding while the auction is not running.
        if (!"RUNNING".equals(this.status)) {
            throw new IllegalStateException("Auction is closed or not started yet.");
        }

        // Validation: Bid amount must be higher than current price
        double currentPrice = (highestBid != null) ? highestBid.getBidAmount() : item.getStartingPrice();
        if (amount <= currentPrice) {
            throw new IllegalArgumentException("Bid amount must be strictly higher than $" + currentPrice);
        }

        // Update the leading bidder
        BidTransaction newBid = new BidTransaction(bidder, amount);
        this.highestBid = newBid;
        this.bidHistory.add(newBid);
    }

    public String getAuctionId() {
        return super.getId();
    }

    // Getters and Setters
    public Item getItem() 						{ return item; 			}
    public BidTransaction getHighestBid()   	{ return highestBid; 	}
    public String getStatus() 					{ return status; 		}
    public void setStatus(String status) 		{ this.status = status; }
    public List<BidTransaction> getBidHistory() { return bidHistory; 	}
}