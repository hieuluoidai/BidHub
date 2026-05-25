# UML BidHub - Online Auction System

## 1. Domain Class Diagram

```mermaid
classDiagram
    direction TB

    class Entity {
        <<abstract>>
        -String id
        +getId() String
        +setId(String) void
    }

    class User {
        <<abstract>>
        -String username
        -String email
        -String password
        -double balance
        -double lockedBalance
        -boolean pendingSeller
        +displayRole() void
        +getAge() int
    }

    class Admin {
        +displayRole() void
    }

    class Bidder {
        +displayRole() void
    }

    class Seller {
        +displayRole() void
    }

    class Item {
        <<abstract>>
        -String itemName
        -String description
        -double startingPrice
        -String imagePath
        +getItemType() String
    }

    class Electronics {
        -String brand
        +getItemType() String
    }

    class Art {
        -String artist
        +getItemType() String
    }

    class Vehicle {
        -String brand
        +getItemType() String
    }

    class ItemFactory {
        +createItem(String, String, String, String, double, String) Item
    }

    class Subject {
        <<interface>>
        +attach(Observer) void
        +detach(Observer) void
        +notifyObservers(String) void
    }

    class Observer {
        <<interface>>
        +update(String) void
    }

    class Auction {
        -Item item
        -BidTransaction[] bidHistory
        -BidTransaction highestBid
        -String status
        -String sellerId
        -LocalDateTime startTime
        -LocalDateTime endTime
        +placeBid(User, double) void
        +getCurrentPrice() double
        +setStatus(String) void
        +restoreBidHistory(BidTransaction[]) void
    }

    class BidTransaction {
        -User bidder
        -double bidAmount
        -LocalDateTime timestamp
        -BidType bidType
        -boolean anonymous
        +getBidder() User
        +getBidAmount() double
    }

    class AutoBid {
        -String autoBidId
        -String auctionId
        -String userId
        -double maxBid
        -double increment
        -boolean anonymous
    }

    class BidResult {
        -boolean success
        -String auctionId
        -double attemptedAmount
        -double currentPrice
        +success() BidResult
        +outbid() BidResult
        +failure() BidResult
    }

    class WalletTransaction {
        -String transactionId
        -String userId
        -double amount
        -TransactionType type
        -LocalDateTime createdAt
    }

    class DepositRequest {
        -String requestId
        -String userId
        -double amount
        -Status status
        -String adminNote
    }

    class Notification {
        -String notificationId
        -String userId
        -Type type
        -String title
        -String message
        -boolean read
    }

    class ChatMessage {
        -String messageId
        -String senderId
        -String receiverId
        -String content
        -LocalDateTime sentAt
        -boolean liked
        -boolean recalled
    }

    class Friendship {
        -String requesterId
        -String addresseeId
        -Status status
        -LocalDateTime createdAt
    }

    Entity <|-- User
    User <|-- Admin
    User <|-- Bidder
    Bidder <|-- Seller

    Entity <|-- Item
    Item <|-- Electronics
    Item <|-- Art
    Item <|-- Vehicle

    Entity <|-- Auction
    Subject <|.. Auction
    Subject ..> Observer

    Auction "1" *-- "1" Item : item
    Auction "1" *-- "0..*" BidTransaction : bidHistory
    Auction "1" --> "0..*" AutoBid : autoBid
    BidTransaction "0..*" --> "1" User : bidder
    AutoBid "0..*" --> "1" User : owner
    AutoBid "0..*" --> "1" Auction : auction
    BidResult --> Auction : resultFor

    ItemFactory ..> Item : creates
    WalletTransaction --> User : userId
    DepositRequest --> User : userId
    Notification --> User : userId
    ChatMessage --> User : sender/receiver
    Friendship --> User : requester/addressee
```

## 2. Component Diagram

```mermaid
flowchart TB
    subgraph Client["Client JavaFX"]
        UI["controller/*"]
        State["AppState<br/>SceneManager"]
        ClientSocket["AuctionClient"]
        ClientUtils["ChatCenter<br/>FriendCenter<br/>NotificationCenter"]
    end

    subgraph Server["Server"]
        ServerCore["AuctionServer"]
        ClientHandler["ClientHandler"]
        Handlers["AuctionHandler<br/>UserHandler<br/>WalletHandler<br/>SocialHandler<br/>NotificationHandler"]
        Managers["AuctionManager<br/>ConcurrentBidManager<br/>AutoBidManager"]
        Services["AuthService<br/>AuctionService<br/>NotificationService<br/>AuctionBroadcastService"]
        ImageHandler["ImageHttpHandler"]
    end

    subgraph Persistence["Persistence"]
        DAOs["UserDAO<br/>ItemDAO<br/>AuctionDAO<br/>BidTransactionDAO<br/>AutoBidDAO<br/>WalletTransactionDAO<br/>NotificationDAO<br/>ChatMessageDAO<br/>FriendshipDAO"]
        DB[("MySQL")]
        Uploads[("uploads/")]
    end

    UI --> State
    UI --> ClientSocket
    UI --> ClientUtils
    ClientUtils --> ClientSocket

    ClientSocket <-->|Socket TCP / Serializable object| ServerCore
    ServerCore --> ClientHandler
    ClientHandler --> Handlers
    ClientHandler --> Managers

    Handlers --> Managers
    Handlers --> Services
    Managers --> DAOs
    Services --> DAOs
    DAOs --> DB

    UI -->|upload / load image| ImageHandler
    ImageHandler --> Uploads
```

## 3. Auction State Diagram

```mermaid
stateDiagram-v2
    [*] --> OPEN: tạo phiên
    OPEN --> RUNNING: đến startTime
    OPEN --> CANCELED: seller/admin hủy
    RUNNING --> RUNNING: bid phút cuối / anti-sniping gia hạn
    RUNNING --> FINISHED: hết endTime
    RUNNING --> CANCELED: seller/admin hủy
    FINISHED --> PAID: winner thanh toán
    FINISHED --> [*]: không có bid / đóng phiên
    PAID --> [*]
    CANCELED --> [*]
```

## 4. Sequence Diagram - Đăng nhập

```mermaid
sequenceDiagram
    actor User
    participant LoginController
    participant AuthService
    participant UserDAO
    participant PasswordUtils
    participant AppState
    participant SceneManager

    User->>LoginController: nhập username/password
    LoginController->>AuthService: login(username, password)
    AuthService->>UserDAO: login(username, password)
    UserDAO->>PasswordUtils: verify(rawPassword, hashedPassword)
    PasswordUtils-->>UserDAO: valid/invalid
    UserDAO-->>AuthService: User hoặc null

    alt đăng nhập thành công
        AuthService-->>LoginController: User
        LoginController->>AppState: setCurrentUser(user)
        LoginController->>SceneManager: chuyển sang dashboard/admin
    else đăng nhập thất bại
        AuthService-->>LoginController: AuthenticationException
        LoginController-->>User: hiển thị lỗi
    end
```

## 5. Sequence Diagram - Tạo phiên đấu giá

```mermaid
sequenceDiagram
    actor Seller
    participant CreateSessionController
    participant AuctionService
    participant ItemFactory
    participant ImageStorageService
    participant AuctionClient
    participant ClientHandler
    participant ItemDAO
    participant AuctionDAO
    participant AuctionManager
    participant NotificationService
    participant AuctionServer

    Seller->>CreateSessionController: nhập thông tin sản phẩm và thời gian
    CreateSessionController->>AuctionService: validateAuctionCreation(...)
    AuctionService-->>CreateSessionController: hợp lệ
    CreateSessionController->>ItemFactory: createItem(type, ...)
    ItemFactory-->>CreateSessionController: Item
    CreateSessionController->>CreateSessionController: tạo Auction và gán sellerId

    opt có ảnh sản phẩm
        CreateSessionController->>ImageStorageService: uploadItemImage(file)
        ImageStorageService-->>CreateSessionController: imagePath
        CreateSessionController->>CreateSessionController: item.setImagePath(imagePath)
    end

    CreateSessionController->>AuctionClient: send(auction)
    AuctionClient->>ClientHandler: Auction object
    ClientHandler->>ItemDAO: save(item, sellerId)
    ClientHandler->>AuctionDAO: save(auction)
    ClientHandler->>AuctionManager: addAuction(auction)
    ClientHandler->>NotificationService: notify seller/admin
    ClientHandler->>AuctionServer: broadcast(auction)
    AuctionServer-->>Seller: cập nhật danh sách phiên
```

## 6. Sequence Diagram - Đặt giá đấu giá

```mermaid
sequenceDiagram
    actor Bidder
    participant BidController
    participant AuctionService
    participant AuctionClient
    participant AuctionServer
    participant AuctionHandler
    participant UserDAO
    participant ConcurrentBidManager
    participant AuctionManager
    participant Auction
    participant BidTransactionDAO
    participant AuctionBroadcastService

    Bidder->>BidController: nhập giá mới
    BidController->>AuctionService: validateBid(auction, amount, currentUser)
    AuctionService-->>BidController: hợp lệ
    BidController->>AuctionClient: send BID:auctionId:amount:userId
    AuctionClient->>AuctionServer: socket message
    AuctionServer->>AuctionHandler: route BID
    AuctionHandler->>UserDAO: findById(userId)
    UserDAO-->>AuctionHandler: User
    AuctionHandler->>ConcurrentBidManager: processBid(auctionId, amount, user, bidDAO)
    ConcurrentBidManager->>AuctionManager: getAuctionById(auctionId)
    AuctionManager-->>ConcurrentBidManager: Auction
    ConcurrentBidManager->>ConcurrentBidManager: lock theo auctionId
    ConcurrentBidManager->>Auction: placeBid(user, amount)
    Auction->>Auction: cập nhật highestBid và bidHistory
    Auction-->>ConcurrentBidManager: thành công
    ConcurrentBidManager->>BidTransactionDAO: save(...)
    BidTransactionDAO-->>ConcurrentBidManager: saved
    ConcurrentBidManager-->>AuctionHandler: BidResult.success
    AuctionHandler-->>AuctionClient: BidResult
    AuctionHandler->>AuctionBroadcastService: broadcastBidSuccess(...)
    AuctionBroadcastService-->>AuctionServer: gửi Auction/Notification cho client liên quan
```

## 7. Sequence Diagram - Auto-bid

```mermaid
sequenceDiagram
    actor Bidder
    participant ItemDetailsController
    participant AuctionClient
    participant AuctionServer
    participant AuctionHandler
    participant AutoBidManager
    participant UserDAO
    participant AutoBidDAO
    participant ConcurrentBidManager
    participant Auction
    participant BidTransactionDAO

    Bidder->>ItemDetailsController: nhập maxBid và increment
    ItemDetailsController->>AuctionClient: send SET_AUTOBID:userId:auctionId:maxBid:increment
    AuctionClient->>AuctionServer: socket message
    AuctionServer->>AuctionHandler: route SET_AUTOBID
    AuctionHandler->>AutoBidManager: registerAutoBid(userId, auctionId, maxBid, increment)
    AutoBidManager->>UserDAO: lockBalance(userId, maxBid)
    UserDAO-->>AutoBidManager: locked
    AutoBidManager->>AutoBidDAO: save(autoBid)
    AutoBidDAO-->>AutoBidManager: saved
    AutoBidManager-->>AuctionHandler: SUCCESS
    AuctionHandler-->>AuctionClient: SET_AUTOBID_OK

    Note over ConcurrentBidManager,AutoBidManager: Khi có bid mới trong cùng phiên
    ConcurrentBidManager->>AutoBidManager: executeAutoBids(auctionId, bidDAO)
    AutoBidManager->>Auction: placeBid(autoBidUser, nextAmount)
    Auction->>Auction: cập nhật giá tự động
    AutoBidManager->>BidTransactionDAO: save(auto bid transaction)
```

## 8. Deployment Diagram

```mermaid
flowchart LR
    subgraph UserPC["Máy người dùng"]
        JavaFX["BidHub JavaFX Client"]
    end

    subgraph VPS["VPS / Server"]
        AuctionServerNode["AuctionServer<br/>ClientHandler<br/>RequestHandler"]
        ImageHttp["ImageHttpHandler"]
        UploadFolder[("uploads/items<br/>uploads/avatars")]
    end

    subgraph DatabaseServer["Database"]
        MySQL[("MySQL 8.0")]
    end

    JavaFX <-->|TCP Socket| AuctionServerNode
    JavaFX -->|HTTP image request/upload| ImageHttp
    AuctionServerNode <-->|JDBC| MySQL
    ImageHttp --> UploadFolder
    AuctionServerNode --> UploadFolder
```
