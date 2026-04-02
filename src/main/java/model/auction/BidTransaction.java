package model.auction;

import model.user.User;
import java.time.LocalDateTime;

public class BidTransaction {
    private User bidder;
    private double bidAmount;
    private LocalDateTime timestamp;
    
    // Constructor
    public BidTransaction(User bidder, double bidAmount) {
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now(); 
    }
    
    // Getters
    public User getBidder() 			{ return bidder; 	}
    public double getBidAmount() 		{ return bidAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }
}