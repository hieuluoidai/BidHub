package network;

import com.sun.net.httpserver.HttpServer;
import model.auction.Auction;
import model.manager.AuctionManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server chinh dieu phoi ket noi socket va HTTP image service.
 */
public class AuctionServer {
    private final int port;
    private final int imagePort;
    private final ExecutorService clientExecutor;
    private HttpServer httpServer;

    private final List<ClientHandler> clients = new ArrayList<>();
    private final List<ClientHandler> observers = new ArrayList<>();

    public AuctionServer(int port, int imagePort, ExecutorService clientExecutor) {
        this.port = port;
        this.imagePort = imagePort;
        this.clientExecutor = clientExecutor;
    }

    public void start() {
        startImageServer();

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
            System.out.println(">>> Server dau gia dang chay tren port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                synchronized (clients) {
                    clients.add(handler);
                }
                clientExecutor.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Loi Server: " + e.getMessage());
        }
    }

    public void sendToUser(String userId, Object data) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (userId.equals(client.getUserId()) && client.isAlive()) {
                    client.send(data);
                }
            }
        }
    }

    public void broadcast(Object data) {
        synchronized (clients) {
            clients.removeIf(client -> !client.isAlive());
            for (ClientHandler client : clients) {
                client.send(data);
            }
        }
    }

    public void broadcastToRole(String role, Object data) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getUserId() != null && client.isAlive()) {
                    String userRole = client.isAdmin() ? "ADMIN" : "BIDDER";
                    if (role.equals(userRole)) {
                        client.send(data);
                    }
                }
            }
        }
    }

    public synchronized void addObserver(ClientHandler observer) {
        observers.add(observer);
    }

    public synchronized void removeObserver(ClientHandler observer) {
        observers.remove(observer);
    }

    public void removeClient(ClientHandler client) {
        synchronized (clients) {
            clients.remove(client);
        }
        removeObserver(client);
    }

    public synchronized void notifyObservers(Object data) {
        for (ClientHandler observer : observers) {
            observer.send(data);
        }
    }

    public void shutdown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        clientExecutor.shutdownNow();
        database.DatabaseConnection.closePool();
    }

    private void startImageServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(imagePort), 0);
            httpServer.createContext("/", new ImageHttpHandler());
            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.start();
            System.out.println(">>> HTTP image server dang chay tren port " + imagePort + "...");
        } catch (IOException e) {
            System.err.println(">>> Khong the khoi dong HTTP image server: " + e.getMessage());
        }
    }

    private static Properties loadServerProperties() {
        Properties props = new Properties();
        try (InputStream in = AuctionServer.class.getResourceAsStream("/server.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println(">>> Khong the doc server.properties, dung gia tri mac dinh.");
        }
        return props;
    }

    public static void main(String[] args) {
        System.out.println(">>> Dang khoi dong he thong...");

        Properties props = loadServerProperties();
        int serverPort = Integer.parseInt(props.getProperty("server.port", "1234"));
        int imagePort = Integer.parseInt(props.getProperty("server.image.port", "8080"));

        List<Auction> savedAuctions = new database.AuctionDAO().findAll();
        for (Auction auction : savedAuctions) {
            AuctionManager.getInstance().addAuction(auction);
        }
        System.out.println(">>> Da nap " + savedAuctions.size() + " phien dau gia.");

        ExecutorService clientExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("bidhub-client-handler");
            thread.setDaemon(true);
            return thread;
        });

        AuctionServer server = new AuctionServer(serverPort, imagePort, clientExecutor);
        AuctionManager.getInstance().startAutoClosureService(server);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(">>> Dang tat Server...");
            server.shutdown();
        }));

        server.start();
    }
}
