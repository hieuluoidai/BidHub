package com.example.auction.client;

import com.example.auction.common.net.CommandType;
import com.example.auction.common.net.Envelope;
import com.example.auction.common.net.EnvelopeKind;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionClient implements Closeable {
    private final CopyOnWriteArrayList<ClientEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CompletableFuture<Envelope>> pendingRequests = new ConcurrentHashMap<>();

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Thread readerThread;

    public void connect(String host, int port) throws IOException {
        if (isConnected()) {
            return;
        }
        socket = new Socket(host, port);
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        inputStream = new ObjectInputStream(socket.getInputStream());
        readerThread = new Thread(this::readLoop, "auction-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public Envelope sendRequest(CommandType command, Map<String, String> data) throws Exception {
        Envelope request = Envelope.request(command, data);
        CompletableFuture<Envelope> future = new CompletableFuture<>();
        pendingRequests.put(request.getRequestId(), future);

        synchronized (this) {
            outputStream.writeObject(request);
            outputStream.flush();
            outputStream.reset();
        }

        return future.get(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void addListener(ClientEventListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(ClientEventListener listener) {
        listeners.remove(listener);
    }

    private void readLoop() {
        try {
            while (isConnected()) {
                Object raw = inputStream.readObject();
                if (!(raw instanceof Envelope envelope)) {
                    continue;
                }

                if (envelope.getKind() == EnvelopeKind.RESPONSE) {
                    CompletableFuture<Envelope> future = pendingRequests.remove(envelope.getRequestId());
                    if (future != null) {
                        future.complete(envelope);
                    }
                } else if (envelope.getKind() == EnvelopeKind.EVENT) {
                    for (ClientEventListener listener : listeners) {
                        listener.onEvent(envelope);
                    }
                }
            }
        } catch (Exception ignored) {
            // connection closed or interrupted
        }
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}
