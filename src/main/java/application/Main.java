package application;

import model.user.*;
import model.item.*;
import model.auction.*;
import model.manager.AuctionManager;
import java.time.LocalDateTime;

public class Main {

    public static void main(String[] args) {
        System.out.println("=== 1. SYSTEM INITIALIZATION ===");
        User seller = new Seller("U01", "BobTheBuilder", "bob@email.com", "pass123");
        User bidder1 = new Bidder("U02", "Alice", "alice@email.com", "pass123");
        User bidder2 = new Bidder("U03", "Jack", "jack@email.com", "pass123");
        
        System.out.println("Users created successfully.");

        System.out.println("\n=== 2. CREATING ITEMS (FACTORY METHOD) ===");
        Item laptop = ItemFactory.createItem("Electronics", "ITM01", "MacBook Pro M3", "Latest model", 1200.0, "24");
        Item car = ItemFactory.createItem("Vehicle", "ITM02", "Tesla Model 3", "Electric Car", 45000.0, "Electric");
        
        System.out.println("Created: " + laptop.getItemName() + " (" + laptop.getItemType() + ")");
        System.out.println("Created: " + car.getItemName() + " (" + car.getItemType() + ")");

        System.out.println("\n=== 3. MANAGING AUCTIONS (SINGLETON) ===");
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusHours(2);
        Auction laptopAuction = new Auction("AUC01", laptop, startTime, endTime);

        AuctionManager manager = AuctionManager.getInstance();
        manager.addAuction(laptopAuction);

        System.out.println("\n=== 4. SIMULATING THE BIDDING PROCESS ===");
        laptopAuction.setStatus("RUNNING");
        System.out.println("Auction for " + laptop.getItemName() + " is now " + laptopAuction.getStatus() + "!");
        System.out.println("Starting Price: $" + laptop.getStartingPrice());

        try {
            laptopAuction.placeBid(bidder1, 1300.0);

            laptopAuction.placeBid(bidder2, 1500.0);

            // Alice tries to bid 1400
            laptopAuction.placeBid(bidder1, 1400.0); 

        } catch (IllegalArgumentException e) {
            System.out.println("BID ERROR: " + e.getMessage());
        } catch (IllegalStateException e) {
            System.out.println("STATUS ERROR: " + e.getMessage());
        }

        System.out.println("\n=== 5. AUCTION SUMMARY ===");
        System.out.println("Total Bids Placed: " + laptopAuction.getBidHistory().size());
        BidTransaction highest = laptopAuction.getHighestBid();
        
        if (highest != null) {
            System.out.println("Current Leading Bidder: " + highest.getBidder().getFullName());
            System.out.println("Winning Amount: $" + highest.getBidAmount());
        }
    }
}