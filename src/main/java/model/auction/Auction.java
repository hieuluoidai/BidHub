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
    private String status; 
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // Constructor
    public Auction(String id, Item item, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item = item;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "OPEN"; 
        this.bidHistory = new ArrayList<>();
    }
    
    // Placing bid method
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
    
    // Setters
    public void setStatus(String status) { this.status = status; }

    // Getters
    public String getAuctionId() { return super.getId(); }
    public Item getItem() 						{ return item; 					}
    public String getItemName()					{ return item.getItemName(); 	}
    public List<BidTransaction> getBidHistory() { return bidHistory; 			}
    public BidTransaction getHighestBid()   	{ return highestBid; 			}
    public String getStatus() 					{ return status; 				}
    
    public String getDuration() {
    	String start = String.valueOf(startTime.getDayOfMonth()) + " / " + String.valueOf(startTime.getMonth()) + " / " + String.valueOf(startTime.getYear());
    	String end = String.valueOf(endTime.getDayOfMonth()) + " / " + String.valueOf(endTime.getMonth()) + " / " + String.valueOf(endTime.getYear());
    	return "Duration: From " + start + " to " + end;
    }
    
    public double getCurrentPrice() {
        if (highestBid != null) {
            return highestBid.getBidAmount(); 
        } else {
            return item.getStartingPrice(); 
        }
    }
}