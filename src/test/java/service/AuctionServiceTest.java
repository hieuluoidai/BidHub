package service;

import exception.ValidationException;
import model.auction.Auction;
import model.item.Item;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import model.item.Art;
import java.time.LocalDate;
import java.time.LocalDateTime;

class AuctionServiceTest {

    private AuctionService auctionService;
    private Auction testAuction;
    private User testBidder;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService();
        Item item = new Art("item-1", "Test Item", "Desc", 100.0, "Artist");
        testAuction = new Auction("auc-1", item, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        testBidder = new Bidder("bidder-1", "bidder", "bidder@test.com", "hash");
        testBidder.setBalance(1000.0);
    }

    @Test
    void testValidateBidSuccess() throws ValidationException {
        auctionService.validateBid(testAuction, 150.0, testBidder);
    }

    @Test
    void testValidateBidLowerThanCurrent() {
        assertThrows(ValidationException.class, () -> auctionService.validateBid(testAuction, 50.0, testBidder));
        assertThrows(ValidationException.class, () -> auctionService.validateBid(testAuction, 100.0, testBidder));
    }

    @Test
    void testValidateBidSelfBid() {
        User seller = new Bidder("seller-1", "seller", "seller@test.com", "hash");
        assertThrows(ValidationException.class, () -> auctionService.validateBid(testAuction, 150.0, seller));
    }

    @Test
    void testValidateBidInsufficientBalance() {
        testBidder.setBalance(100.0);
        assertThrows(ValidationException.class, () -> auctionService.validateBid(testAuction, 150.0, testBidder));
    }

    @Test
    void testBuildBidCommand() {
        String cmd = auctionService.buildBidCommand("auc-1", 150.0, "user-1");
        assertEquals("BID:auc-1:150.00:user-1", cmd);
    }

    @Test
    void testValidateAuctionCreationSuccess() throws ValidationException {
        auctionService.validateAuctionCreation(
                "Art", "Item Name", "100.0", "Extra Info",
                LocalDate.now().plusDays(7), "23", "55", 15
        );
    }

    @Test
    void testValidateAuctionCreationInvalidPrice() {
        assertThrows(ValidationException.class, () -> 
            auctionService.validateAuctionCreation("Art", "Item", "abc", "Extra", 
                LocalDate.now().plusDays(1), "12", "00", 10));
    }

    @Test
    void testValidateAuctionCreationPastEndTime() {
        assertThrows(ValidationException.class, () -> 
            auctionService.validateAuctionCreation("Art", "Item", "100", "Extra", 
                LocalDate.now().minusDays(1), "12", "00", 10));
    }

    @Test
    void testValidateAuctionEditSuccess() throws ValidationException {
        auctionService.validateAuctionEdit(
                "New Name", "200.0", "New Extra",
                LocalDate.now().plusDays(2), LocalDateTime.now()
        );
    }

    @Test
    void testValidateAuctionEditInvalidPrice() {
        assertThrows(ValidationException.class, () ->
            auctionService.validateAuctionEdit("Name", "0", "Extra", 
                LocalDate.now().plusDays(1), LocalDateTime.now()));
    }
}
