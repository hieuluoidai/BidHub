package network;

import model.auction.Auction;
import model.item.Electronics;
import model.manager.AuctionManager;
import model.user.Bidder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientHandlerTest extends database.BaseDAOTest {

    private Socket socketMock;
    private AuctionServer serverMock;
    private PipedOutputStream clientOut;
    private PipedInputStream serverIn;
    private PipedOutputStream serverOut;
    private PipedInputStream clientIn;
    private ClientHandler handler;
    private ObjectOutputStream oos;
@BeforeEach
void setUp() throws IOException {
    socketMock = mock(Socket.class);
    serverMock = mock(AuctionServer.class);

    clientOut = new PipedOutputStream();
    serverIn = new PipedInputStream(clientOut);

    serverOut = new PipedOutputStream();
    clientIn = new PipedInputStream(serverOut);

    when(socketMock.getInputStream()).thenReturn(serverIn);
    when(socketMock.getOutputStream()).thenReturn(serverOut);

    handler = new ClientHandler(socketMock, serverMock);
    // We need to write the ObjectStream header first
    oos = new ObjectOutputStream(clientOut);
}

@AfterEach
void tearDown() throws IOException, java.sql.SQLException {
    // Closing the socket should trigger EOFException and stop the handler thread
    if (clientOut != null) clientOut.close();
    if (serverOut != null) serverOut.close();
    if (oos != null) oos.close();
}
    @Test
    void testIdentify() throws Exception {
        // Start handler in a separate thread
        Thread handlerThread = new Thread(handler);
        handlerThread.start();

        // Send IDENTIFY command
        oos.writeObject("IDENTIFY:user-123");
        oos.flush();

        // Wait a bit for processing
        Thread.sleep(100);

        assertEquals("user-123", handler.getUserId());
    }

    @Test
    void testHandleBid() throws Exception {
        // Pre-create seller, bidder, item, and auction
        new database.UserDAO().save(new model.user.Seller("s1", "seller1", "s1@ex.com", "p"));
        new database.UserDAO().save(new model.user.Bidder("b1", "bidder1", "b1@ex.com", "p"));
        new database.UserDAO().setBalance("b1", 10000.0);
        
        model.item.Item item = new model.item.Electronics("i1", "Item", "D", 100.0, "B");
        new database.ItemDAO().save(item, "s1");
        
        Auction auction = new Auction("auc-bid-1", item, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(1));
        auction.setStatus("RUNNING");
        new database.AuctionDAO().save(auction);
        model.manager.AuctionManager.getInstance().addAuction(auction);

        Thread handlerThread = new Thread(handler);
        handlerThread.start();

        // Send BID command
        // Format: BID:auctionId:amount:userId
        oos.writeObject("BID:auc-bid-1:200.0:b1");
        oos.flush();

        Thread.sleep(500);

        // Verify broadcast was called (for new highest bid)
        verify(serverMock, atLeastOnce()).broadcast(any());
        
        Auction updated = model.manager.AuctionManager.getInstance().getAuctionById("auc-bid-1");
        assertNotNull(updated.getHighestBid());
        assertEquals(200.0, updated.getCurrentPrice());
    }
}
