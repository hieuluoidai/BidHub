package model.manager;

import model.auction.Auction;
import java.util.ArrayList;
import java.util.List;

public class AuctionManager {
    private static AuctionManager instance;
    private List<Auction> auctions;
    private AuctionManager() {
        auctions = new ArrayList<>();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    public void addAuction(Auction auction) {
        if (auction != null) {
            auctions.add(auction);
            System.out.println("Auction " + auction.getAuctionId() + " has been added to the manager successfully.");
        }
    }

    public Auction getAuctionById(String auctionId) {
        for (Auction auction : auctions) {
            if (auction.getAuctionId().equals(auctionId)) {
                return auction;
            }
        }
        return null; 
    }
    
    // All auction getter
    public List<Auction> getAllAuctions() {
        return new ArrayList<>(this.auctions);
    }
}