```mermaid
classDiagram
%% Lớp cơ sở
class Entity {
<<abstract>>
-String id
}

    %% Hệ thống người dùng
    class User {
        <<abstract>>
        -String username
        -String password
        -String email
        +login() void
        +register() void
    }

    class Bidder {
        -double maxBid
        -double increment
        +placeBid(Auction auction, double amount) void
        +setAutoBid(double maxBid, double increment) void
    }

    class Seller {
        +addItem(Item item) void
        +updateItem(Item item) void
        +deleteItem(Item item) void
    }

    class Admin {
        +manageSystem() void
    }

    %% Hệ thống sản phẩm
    class Item {
        <<abstract>>
        -String name
        -String description
        -double startingPrice
        -double currentHighestPrice
        -LocalDateTime startTime
        -LocalDateTime endTime
        +printInfo() void
    }

    class Electronics {
        -String brand
        -int warrantyMonths
        +printInfo() void
    }

    class Art {
        -String artist
        -String material
        +printInfo() void
    }

    class Vehicle {
        -String model
        -int year
        +printInfo() void
    }

    %% Nghiệp vụ đấu giá
    class Auction {
        -Item item
        -AuctionStatus status
        -List~BidTransaction~ bidHistory
        -List~Bidder~ observers
        +startAuction() void
        +endAuction() void
        +determineWinner() Bidder
        +extendTime(int seconds) void
        +notifyObservers() void
    }

    class BidTransaction {
        -Bidder bidder
        -double amount
        -LocalDateTime timestamp
        +isValid() boolean
    }

    class AuctionStatus {
        <<enumeration>>
        OPEN
        RUNNING
        FINISHED
        PAID
        CANCELED
    }

    %% Quan hệ kế thừa
    Entity <|-- User
    Entity <|-- Item
    
    User <|-- Bidder
    User <|-- Seller
    User <|-- Admin
    
    Item <|-- Electronics
    Item <|-- Art
    Item <|-- Vehicle

    %% Quan hệ liên kết
    Auction "1" *-- "1" Item : manages
    Auction "1" *-- "*" BidTransaction : contains
    BidTransaction "*" o-- "1" Bidder : made by
    Seller "1" -- "*" Item : owns
    Auction ..> AuctionStatus : uses