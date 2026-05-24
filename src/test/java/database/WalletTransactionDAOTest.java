package database;

import model.auction.WalletTransaction;
import model.user.Bidder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WalletTransactionDAOTest extends BaseDAOTest {
    private WalletTransactionDAO walletDAO;
    private UserDAO userDAO;
    private final String userId = "user-wallet";

    @BeforeEach
    public void setUp() {
        walletDAO = new WalletTransactionDAO();
        userDAO = new UserDAO();
        userDAO.save(new Bidder(userId, "WalletUser", "wallet@test.com", "pass"));
    }

    @Test
    public void testSaveAndFindByUserId() throws InterruptedException {
        assertTrue(walletDAO.save(userId, 100.0, WalletTransaction.TransactionType.TOPUP, "Deposit"));
        Thread.sleep(20);
        assertTrue(walletDAO.save(userId, 50.0, WalletTransaction.TransactionType.PAYMENT, "Bid"));

        List<WalletTransaction> txs = walletDAO.findByUserId(userId);
        assertEquals(2, txs.size());
        assertEquals(WalletTransaction.TransactionType.PAYMENT, txs.get(0).getType());
        assertEquals(50.0, txs.get(0).getAmount());
    }
}
