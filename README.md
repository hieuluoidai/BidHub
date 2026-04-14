```mermaid
classDiagram
%% --- ENUMS (Các hằng số trạng thái) ---
class AuctionStatus {
<<enumeration>>
PENDING
ACTIVE
COMPLETED
CANCELLED
}
class ItemCondition {
<<enumeration>>
NEW
LIKE_NEW
USED
}

    %% --- INTERFACES (Giao diện dịch vụ) ---
    class PaymentProcessor {
        <<interface>>
        +processPayment(String userId, double amount) bool
        +refundPayment(String userId, double amount) bool
    }
    class NotificationService {
        <<interface>>
        +sendEmail(String email, String message) void
        +sendPushNotification(String userId, String message) void
    }

    %% --- EXCEPTIONS (Xử lý lỗi) ---
    class InvalidBidException {
        <<Exception>>
        -String errorMessage
        +getMessage() String
    }

    %% --- KẾT NỐI VÀ QUAN HỆ ---
    User <|-- Bidder
    User <|-- Seller
    User <|-- Admin
    
    Item <|-- Electronics
    Item <|-- Art
    
    Auction "1" *-- "many" Item : contains
    Auction "1" *-- "many" BidTransaction : manages
    Auction --> AuctionStatus : has status
    Item --> ItemCondition : has condition
    
    Bidder "1" -- "many" BidTransaction : makes
    Seller "1" -- "many" Item : lists
    
    Auction ..> InvalidBidException : throws
    Auction ..> NotificationService : uses
    Auction ..> PaymentProcessor : uses

    %% --- CHI TIẾT CÁC LỚP ---
    class User {
        <<abstract>>
        #String userId
        #String username
        #String encryptedPassword
        #String email
        #DateTime createdAt
        +login(String user, String pass) bool
        +logout() void
        +resetPassword(String newPass) void
    }

    class Bidder {
        -double accountBalance
        -List~String~ watchList
        +placeBid(Auction auction, double amount) bool
        +addToWatchList(String itemId) void
    }

    class Seller {
        -double reputationScore
        -boolean isVerified
        +createAuction(Item item, double startPrice, DateTime endTime) Auction
        +shipItem(String itemId) void
    }

    class Admin {
        -String adminRole
        +suspendUser(String userId, String reason) void
        +cancelAuction(String auctionId) void
    }

    class Item {
        <<abstract>>
        #String itemId
        #String title
        #String description
        #List~String~ imageUrls
        +getDetails() String
    }

    class Electronics {
        -int warrantyMonths
        -String brand
        -String modelNumber
    }

    class Art {
        -String artistName
        -int creationYear
        -boolean certificateOfAuthenticity
    }

    class Auction {
        -String auctionId
        -double startingPrice
        -double currentHighestBid
        -String winningBidderId
        -DateTime startTime
        -DateTime endTime
        +startAuction() void
        +processBid(BidTransaction bid) void
        +closeAuction() void
    }

    class BidTransaction {
        -String transactionId
        -String bidderId
        -double bidAmount
        -DateTime timestamp
        -boolean isWinningBid
        +validateBid(double currentPrice) bool
    }