package com.example.auction.client;

import com.example.auction.common.model.AuctionSnapshot;
import com.example.auction.common.model.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppState implements AutoCloseable {
    private final AuctionClient client = new AuctionClient();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ObservableList<AuctionSnapshot> auctions = FXCollections.observableArrayList();

    private User currentUser;

    public AuctionClient getClient() {
        return client;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public ObservableList<AuctionSnapshot> getAuctions() {
        return auctions;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public void connect(String host, int port) throws IOException {
        client.connect(host, port);
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public void resetSession() {
        currentUser = null;
        auctions.clear();
    }

    @Override
    public void close() throws IOException {
        executorService.shutdownNow();
        client.close();
    }
}
