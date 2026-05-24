package database;

import model.auction.DepositRequest;
import model.user.Bidder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DepositRequestDAOTest extends BaseDAOTest {
    private DepositRequestDAO depositDAO;
    private UserDAO userDAO;
    private final String userId = "user-dep";

    @BeforeEach
    public void setUp() {
        depositDAO = new DepositRequestDAO();
        userDAO = new UserDAO();
        userDAO.save(new Bidder(userId, "DepUser", "dep@test.com", "pass"));
    }

    @Test
    public void testLifecycle() {
        String rid = "REQ-001";
        assertTrue(depositDAO.save(rid, userId, 500.0));

        DepositRequest dr = depositDAO.findById(rid);
        assertNotNull(dr);
        assertEquals(userId, dr.getUserId());
        assertEquals(500.0, dr.getAmount());
        assertEquals(DepositRequest.Status.PENDING, dr.getStatus());

        List<DepositRequest> pending = depositDAO.findPending();
        assertTrue(pending.stream().anyMatch(p -> p.getRequestId().equals(rid)));

        List<String> userIds = depositDAO.getPendingUserIds();
        assertTrue(userIds.contains(userId));

        assertTrue(depositDAO.review(rid, DepositRequest.Status.APPROVED, "Ok"));
        DepositRequest approved = depositDAO.findById(rid);
        assertEquals(DepositRequest.Status.APPROVED, approved.getStatus());
        assertEquals("Ok", approved.getAdminNote());
    }
}
