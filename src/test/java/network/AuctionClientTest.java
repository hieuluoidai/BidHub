package network;

import javafx.application.Platform;
import model.auction.Auction;
import model.auction.BidResult;
import model.item.Electronics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuctionClientTest {

    private Socket socketMock;
    private PipedOutputStream serverOut;
    private PipedInputStream clientIn;
    private PipedOutputStream clientOut;
    private PipedInputStream serverIn;
    private AuctionClient client;
    private MockedStatic<Platform> platformMock;

    @BeforeEach
    void setUp() throws IOException {
        socketMock = mock(Socket.class);
        serverOut = new PipedOutputStream();
        clientIn = new PipedInputStream(serverOut);
        clientOut = new PipedOutputStream();
        serverIn = new PipedInputStream(clientOut);

        when(socketMock.getInputStream()).thenReturn(clientIn);
        when(socketMock.getOutputStream()).thenReturn(clientOut);

        client = new AuctionClient();

        // Mock Platform.runLater to execute immediately in the same thread
        platformMock = mockStatic(Platform.class);
        platformMock.when(() -> Platform.runLater(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        client.close();
        platformMock.close();
    }

    @Test
    void testListenAndHandleBidResult() throws Exception {
        AtomicReference<BidResult> receivedResult = new AtomicReference<>();
        client.addBidResultListener(receivedResult::set);

        BidResult result = BidResult.success("auc1", 200.0, "winner1");
        
        // Use reflection to call handleIncomingData directly in the test thread
        var handleMethod = AuctionClient.class.getDeclaredMethod("handleIncomingData", Object.class);
        handleMethod.setAccessible(true);
        handleMethod.invoke(client, result);

        assertNotNull(receivedResult.get());
        assertTrue(receivedResult.get().isSuccess());
        assertEquals("winner1", receivedResult.get().getWinnerUsername());
    }

    @Test
    void testUpdateSingleAuction() throws Exception {
        Auction auction = new Auction("a1", new Electronics("i1", "Item", "D", 100.0, "B"), LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        
        var list = model.manager.AppState.getInstance().getAuctionList();
        list.clear();
        list.add(auction);

        Auction updated = new Auction("a1", new Electronics("i1", "Item New", "D", 150.0, "B"), auction.getStartTime(), auction.getEndTime());
        updated.setStatus("RUNNING");

        // handleIncomingData is private, use reflection or send via stream
        var handleMethod = AuctionClient.class.getDeclaredMethod("handleIncomingData", Object.class);
        handleMethod.setAccessible(true);
        handleMethod.invoke(client, updated);

        assertEquals(1, list.size());
        assertEquals(150.0, list.get(0).getCurrentPrice());
        assertEquals("RUNNING", list.get(0).getStatus());
    }
}
