```mermaid
classDiagram
    %% ==========================================
    %% CORE LAYER
    %% ==========================================
    class Entity {
        <<Abstract>>
        -id: String
        +getId() String
        +setId(id: String) void
    }

    class Subject {
        <<Interface>>
        +attach(Observer) void
        +detach(Observer) void
        +notifyObservers(String) void
    }

    class Observer {
        <<Interface>>
        +update(message: String) void
    }

    %% ==========================================
    %% USER LAYER
    %% ==========================================
    class User {
        <<Abstract>>
        -username: String
        -email: String
        -password: String
        +displayRole()* void
        +getUsername() String
        +getEmail() String
    }
    class Admin { +displayRole() void }
    class Bidder { +displayRole() void }
    class Seller { +displayRole() void }

    %% ==========================================
    %% ITEM LAYER
    %% ==========================================
    class Item {
        <<Abstract>>
        -itemName: String
        -description: String
        -startingPrice: double
        +getItemType()* String
        +getItemName() String
        +getStartingPrice() double
    }
    class Electronics { -brand: String }
    class Art { -author: String }
    class Vehicle { -brand: String }

    class ItemFactory {
        +createItem(type, id, name, desc, price, info)$ Item
    }

    %% ==========================================
    %% AUCTION LOGIC LAYER
    %% ==========================================
    class Auction {
        -item: Item
        -bidHistory: List~BidTransaction~
        -highestBid: BidTransaction
        -status: String
        -startTime: LocalDateTime
        -endTime: LocalDateTime
        -observers: List~Observer~
        +placeBid(User, amount) void
        +getCurrentPrice() double
        +setStatus(status: String) void
        +notifyObservers(message: String) void
    }

    class BidTransaction {
        -bidder: User
        -bidAmount: double
        -timestamp: LocalDateTime
        +getBidder() User
        +getBidAmount() double
    }

    %% ==========================================
    %% DATABASE LAYER (DAO PATTERN) - [BỔ SUNG MỚI]
    %% ==========================================
    class DatabaseConnection {
        <<Singleton>>
        -instance: DatabaseConnection$
        -connection: Connection
        +getInstance()$ DatabaseConnection
        +getConnection() Connection
    }

    class UserDAO {
        +findByUsername(username: String) User
        +login(username, password) User
        +save(user: User) boolean
    }

    class AuctionDAO {
        +save(auction: Auction) boolean
        +findAll() List~Auction~
        +updateStatus(id, status) boolean
    }

    class ItemDAO {
        +save(item: Item, sellerId: String) boolean
        +findById(id: String) Item
    }

    class BidTransactionDAO {
        +save(auctionId, bidderId, amount) boolean
        +findWinner(auctionId: String) String[]
    }

    %% ==========================================
    %% NETWORK LAYER (SOCKET) - [BỔ SUNG MỚI]
    %% ==========================================
    class AuctionServer {
        -port: int
        -clients: List~ClientHandler~
        +start() void
        +broadcast(data: Object) void
    }

    class ClientHandler {
        <<Runnable>>
        -socket: Socket
        -server: AuctionServer
        +run() void
        +send(data: Object) void
        -handleRequest(request: Object) void
    }

    class AuctionClient {
        -socket: Socket
        -isRunning: boolean
        +connect(host: String, port: int) void
        +send(data: Object) void
        -listen() void
    }

    %% ==========================================
    %% MANAGEMENT & APP LAYER
    %% ==========================================
    class Main {
        +start(primaryStage: Stage) void
        +main(args: String[])$ void
    }

    class SceneManager {
        -primaryStage: Stage
        +SceneManager(primaryStage: Stage)
        +showLogin() void
        +showDashboard() void
    }

    class AppState {
        <<Singleton>>
        -instance: AppState$
        -currentUser: User
        -sceneManager: SceneManager
        -client: AuctionClient
        -auctionList: ObservableList~Auction~
        +getInstance()$ AppState
    }

    class AuctionManager {
        <<Singleton>>
        -instance: AuctionManager$
        -auctions: List~Auction~
        +getInstance()$ AuctionManager
        +addAuction(Auction) void
        +processBid(auctionId, newPrice, bidder) boolean
    }

    %% ==========================================
    %% RELATIONSHIPS (LIÊN KẾT)
    %% ==========================================
    
    %% Kế thừa (Inheritance)
    Entity <|-- User
    Entity <|-- Item
    Entity <|-- Auction
    
    User <|-- Admin
    User <|-- Bidder
    User <|-- Seller
    
    Item <|-- Electronics
    Item <|-- Art
    Item <|-- Vehicle
    
    Subject <|.. Auction
    
    %% Thành phần & Hiệp tác (Composition & Association)
    Auction "1" *-- "1" Item : contains
    Auction "1" *-- "0..*" BidTransaction : tracks
    BidTransaction "0..*" --> "1" User : placed by
    
    ItemFactory ..> Item : creates
    
    AuctionManager "1" o-- "0..*" Auction : manages all
    AppState --> User : holds session
    AppState --> SceneManager : controls UI
    AppState "1" *-- "1" AuctionClient : uses
    
    Main ..> SceneManager : initializes
    Main ..> AppState : configures
    
    %% Network Relationships (Server Side)
    AuctionServer "1" *-- "0..*" ClientHandler : maintains
    ClientHandler ..> AuctionManager : calls logic
    
    %% Network Relationships (Client Side)
    AuctionClient ..> AppState : updates UI list
    
    %% Database Relationships
    UserDAO ..> DatabaseConnection : uses
    AuctionDAO ..> DatabaseConnection : uses
    ItemDAO ..> DatabaseConnection : uses
    BidTransactionDAO ..> DatabaseConnection : uses
    
    AuctionManager ..> AuctionDAO : syncs with
    ClientHandler ..> BidTransactionDAO : backups
