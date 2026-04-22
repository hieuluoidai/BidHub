```mermaid
classDiagram
%% --- CORE LAYER ---
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
        +update(String message) void
    }

    %% --- USER LAYER ---
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

    %% --- ITEM LAYER ---
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

    %% --- AUCTION LOGIC ---
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

    %% --- MANAGEMENT & APP LAYER ---
    class Main {
        +start(primaryStage: Stage) void
        +main(args: String[])$ void
    }

    class SceneManager {
        -primaryStage: Stage
        +SceneManager(primaryStage: Stage)
        +showLogin() void
    }

    class AppState {
        <<Singleton>>
        -instance: AppState$
        -currentUser: User
        -sceneManager: SceneManager
        -auctionList: ObservableList~Auction~
        +getInstance()$ AppState
        +setCurrentUser(user: User) void
        +getCurrentUser() User
    }

    class AuctionManager {
        <<Singleton>>
        -instance: AuctionManager$
        -auctions: List~Auction~
        +getInstance()$ AuctionManager
        +addAuction(Auction) void
        +getAuctionById(id: String) Auction
    }

    %% --- RELATIONSHIPS ---
    
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
    
    %% Thành phần và Hiệp tác (Composition & Association)
    Auction "1" *-- "1" Item : contains
    Auction "1" *-- "0..*" BidTransaction : tracks
    BidTransaction "0..*" --> "1" User : placed by
    
    ItemFactory ..> Item : creates
    
    AuctionManager "1" o-- "0..*" Auction : manages all
    AppState --> User : session user
    AppState --> SceneManager : controls UI
    
    Main ..> SceneManager : initializes
    Main ..> AppState : configures
    
    %% Dependency
    Auction ..> Observer : notifies changes