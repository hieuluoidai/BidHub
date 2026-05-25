# UML Class Diagram - BidHub

```mermaid
classDiagram
    direction TB

    namespace application {
        class Main {
            +start() void
            +main() void
        }

        class ClientLauncher {
            +main() void
        }
    }

    namespace controller {
        class AdminController {
            -String currentViewName
            -ChangeListener chatBadgeListener
            +initialize() void
            +openUserDetails() void
            +openItemDetails() void
            +refreshUserData() void
        }

        class AdminAuctionsController {
            -FilteredList filteredAuctions
            -String selectedCategoryFilter
            -AdminController mainController
            +initialize() void
            +handleRefresh() void
            +setCategoryFilter() void
        }

        class AdminUsersController {
            -FilteredList filteredUsers
            -ObservableList masterUsers
            -Set pendingDepositUserIds
            -AdminController mainController
            +initialize() void
            +setupUserTable() void
            +loadUserData() void
        }

        class AdminWalletController {
            -ObservableList depositList
            -DateTimeFormatter dtFmt
            -AdminController mainController
            +initialize() void
            +loadDepositData() void
            +handleRefreshDeposits() void
        }

        class AvatarCropController {
            -Image sourceImage
            -Consumer onSave
            -double scale
            -double offsetX
            -double offsetY
            +initialize() void
            +setImage() void
            -cropToFile() File
        }

        class BidController {
            -AuctionService auctionService
            -Auction currentAuction
            -Consumer onBidDoneCallback
            -Consumer errorStringListener
            +setAuctionData() void
            +setOnBidDoneCallback() void
            -handleBidResult() void
        }

        class ChangePasswordController {
            -Object controls
            -closeWindow() void
        }

        class ConfirmCancelController {
            -boolean confirmed
            +setAuctionData() void
            +isConfirmed() boolean
            -closeWindow() void
        }

        class ConfirmDeleteController {
            -boolean confirmed
            +setAuctionData() void
            +isConfirmed() boolean
            -closeWindow() void
        }

        class ConfirmPayController {
            -boolean confirmed
            +setPaymentData() void
            +isConfirmed() boolean
            -closeWindow() void
        }

        class CreateSessionController {
            -AuctionService auctionService
            -File selectedImageFile
            -String lockedItemType
            +initialize() void
            +lockItemType() void
            -finishCreation() void
        }

        class DashboardController {
            -FilteredList filteredData
            -ObservableList transactionRows
            -String selectedCategoryFilter
            -ChangeListener chatBadgeListener
            +initialize() void
            +refreshBalanceLabel() void
            +openChatWith() void
        }

        class EditSessionController {
            -AuctionService auctionService
            -Auction auction
            -Runnable onSavedCallback
            -File selectedImageFile
            -String originalImagePath
            +setAuction() void
            +setOnSavedCallback() void
            -finishEdit() void
        }

        class FriendsController {
            -Consumer bundleListener
            -Consumer searchListener
            -Consumer openChatCallback
            +initialize() void
            +detach() void
            +setOpenChatCallback() void
        }

        class ItemCardController {
            -Auction auction
            -Consumer onViewDetails
            -Consumer onQuickBid
            -SetChangeListener autoBidListener
            -SetChangeListener starListener
            +setData() void
            +getAuctionId() String
            -handleCardClick() void
        }

        class ItemDetailsController {
            -Auction auction
            -ObservableList bidRows
            -AuctionTimer auctionTimer
            -AuctionChartManager chartManager
            -double lastDisplayedPrice
            +getAuction() Auction
            +setItemData() void
            +handleSetAutoBid() void
            +handleOpenBidDialog() void
        }

        class LoginController {
            -String serverHost
            -int serverPort
            +handleLogin() void
            +handleRegister() void
        }

        class MessagesController {
            -ObservableList summaries
            -Map messageMap
            -String currentPartnerId
            -String currentPartnerName
            -Popup emojiPopup
            +initialize() void
            -onIncomingMessage() void
            -onSummaries() void
        }

        class NotificationPopupController {
            -ObservableList items
            -Runnable onMarkAll
            -Consumer onMarkOne
            -Consumer onAction
            +initialize() void
            +setData() void
            +setOnMarkAll() void
        }

        class NotificationWindowController {
            -ObservableList items
            -Runnable onMarkAll
            -Consumer onMarkOne
            -Consumer onAction
            +initialize() void
            +setData() void
            +setOnMarkOne() void
        }

        class RegisterController {
            -AuthService authService
            -int minAge
            -int passwordMin
            -Pattern namePattern
            -Pattern emailPattern
            +initialize() void
            +handleRegister() void
            +handleLogin() void
        }

        class TopUpController {
            -double pendingAmount
            -String pendingRequestId
            -Runnable onTopUpSuccess
            +initialize() void
            +setOnTopUpSuccess() void
            -handleServerResponse() void
        }

        class UserDetailsController {
            -User user
            -boolean editing
            -Runnable onAvatarChanged
            -ListChangeListener statsListener
            +setUserData() void
            +setOnAvatarChanged() void
        }
    }

    namespace database {
        class DatabaseConnection {
            -HikariDataSource dataSource
            +getConnection() Connection
            +closePool() void
        }

        class UserDAO {
            +findByUsername() User
            +findById() User
            +login() User
            +save() boolean
            +lockBalance() boolean
            +transferAtomic() boolean
        }

        class ItemDAO {
            +save() boolean
            +findAll() List
            +findById() Item
            +update() boolean
            +delete() boolean
        }

        class AuctionDAO {
            -ItemDAO itemDAO
            +save() boolean
            +findAll() List
            +findById() Auction
            +updateStatus() boolean
            +markAsPaid() boolean
        }

        class BidTransactionDAO {
            +save() boolean
            +findTransactionsByAuctionId() List
            +findWinner() StringArray
            +countBidsByBidderId() int
        }

        class AutoBidDAO {
            +save() boolean
            +delete() boolean
            +findByAuctionId() List
            +findByUserAndAuction() AutoBid
        }

        class WalletTransactionDAO {
            +save() boolean
            +findByUserId() List
        }

        class DepositRequestDAO {
            +save() boolean
            +findById() DepositRequest
            +findPending() List
            +review() boolean
        }

        class NotificationDAO {
            +insert() String
            +upsertChat() String
            +findRecent() List
            +getUnreadCount() int
            +markAsRead() boolean
        }

        class ChatMessageDAO {
            -boolean tablesReady
            +insert() ChatMessage
            +findConversation() List
            +findSummaries() List
            +markConversationRead() List
        }

        class FriendshipDAO {
            +sendRequest() boolean
            +accept() boolean
            +decline() boolean
            +search() SearchBundle
        }
    }

    namespace exception {
        class AppException {
            -ErrorCode errorCode
            -String userMessage
            -Map details
            +getErrorCode() ErrorCode
            +getUserMessage() String
            +toErrorResponse() ErrorResponse
        }

        class AuctionException {
            -String auctionId
            +getAuctionId() String
        }

        class AuctionClosedException {
            -String status
            +getStatus() String
        }

        class AuthenticationException {
            +AuthenticationException()
        }

        class DatabaseException {
            +DatabaseException()
        }

        class InvalidBidException {
            -double attemptedAmount
            -double currentPrice
            +invalidAmount() InvalidBidException
            +getAttemptedAmount() double
            +getCurrentPrice() double
        }

        class ValidationException {
            -String property
            +getProperty() String
        }

        class ErrorCode {
            <<enumeration>>
            -String code
            -String defaultMessage
            +getCode() String
            +getDefaultMessage() String
        }

        class ErrorResponse {
            -String code
            -String message
            -String technicalMessage
            -Map details
            -Instant timestamp
            +of() ErrorResponse
            +from() ErrorResponse
            +getCode() String
            +getMessage() String
        }

        class ExceptionMapper {
            +map() AppException
            +toErrorResponse() ErrorResponse
            +userMessage() String
        }
    }

    namespace model_core {
        class Entity {
            <<abstract>>
            -String id
            +getId() String
            +setId() void
        }

        class Subject {
            <<interface>>
            +attach() void
            +detach() void
            +notifyObservers() void
        }

        class Observer {
            <<interface>>
            +update() void
        }
    }

    namespace model_user {
        class User {
            <<abstract>>
            -String username
            -String email
            -String password
            -String fullName
            -LocalDate dateOfBirth
            -String phoneNumber
            -double balance
            -double lockedBalance
            -String avatarPath
            -boolean pendingSeller
            +displayRole() void
            +getUserId() String
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
    }

    namespace model_item {
        class Item {
            <<abstract>>
            -String itemName
            -String description
            -double startingPrice
            -String imagePath
            +getItemType() String
            +getItemId() String
        }

        class Electronics {
            -String brand
            +getItemType() String
            +getBrand() String
        }

        class Art {
            -String artist
            +getItemType() String
            +getArtist() String
        }

        class Vehicle {
            -String brand
            +getItemType() String
            +getBrand() String
        }

        class ItemFactory {
            +createItem() Item
        }
    }

    namespace model_auction {
        class Auction {
            -Item item
            -List bidHistory
            -BidTransaction highestBid
            -String status
            -String sellerId
            -LocalDateTime startTime
            -LocalDateTime endTime
            -int extensionCount
            +placeBid() void
            +getCurrentPrice() double
            +setStatus() void
            +restoreBidHistory() void
        }

        class BidTransaction {
            -User bidder
            -double bidAmount
            -LocalDateTime timestamp
            -BidType bidType
            -boolean anonymous
            -String anonymousDisplayName
            +getBidAmount() double
            +getBidder() User
            +getBidType() BidType
        }

        class AutoBid {
            -String autoBidId
            -String auctionId
            -String userId
            -double maxBid
            -double increment
            -LocalDateTime createdAt
            -boolean anonymous
            +getAutoBidId() String
            +getAuctionId() String
            +getUserId() String
            +getMaxBid() double
        }

        class BidResult {
            -Status status
            -String auctionId
            -double bidAmount
            -double currentPrice
            -String message
            -String winnerUsername
            -ErrorCode errorCode
            +success() BidResult
            +outbid() BidResult
            +failure() BidResult
            +isSuccess() boolean
        }

        class WalletTransaction {
            -String transactionId
            -String userId
            -double amount
            -TransactionType type
            -String description
            -LocalDateTime createdAt
            +getTransactionId() String
            +getUserId() String
            +getAmount() double
            +getType() TransactionType
        }

        class DepositRequest {
            -String requestId
            -String userId
            -String username
            -double amount
            -Status status
            -String adminNote
            -LocalDateTime createdAt
            +getRequestId() String
            +setRequestId() void
            +getAmount() double
            +setStatus() void
        }
    }

    namespace model_chat {
        class ChatMessage {
            -String messageId
            -String senderId
            -String receiverId
            -String content
            -LocalDateTime sentAt
            -LocalDateTime readAt
            -boolean liked
            -boolean recalled
            +getMessageId() String
            +getSenderId() String
            +getReceiverId() String
            +isRead() boolean
        }
    }

    namespace model_friendship {
        class Friendship {
            -String requesterId
            -String addresseeId
            -Status status
            -LocalDateTime createdAt
            -String partnerId
            -String partnerUsername
            -String partnerAvatarPath
            -String partnerRole
            +getRequesterId() String
            +setRequesterId() void
            +getStatus() Status
            +setStatus() void
        }
    }

    namespace model_notification {
        class Notification {
            -String notificationId
            -String userId
            -Type type
            -String title
            -String message
            -boolean read
            -LocalDateTime createdAt
            +getNotificationId() String
            +getUserId() String
            +getType() Type
            +isRead() boolean
        }
    }

    namespace model_manager {
        class AppState {
            -User currentUser
            -AuctionClient client
            -SceneManager sceneManager
            -ObservableList auctionList
            -ObservableSet myAutoBidIds
            -ObservableSet starredAuctionIds
            -IntegerProperty totalUnreadChat
            -IntegerProperty pendingFriendCount
            +getInstance() AppState
            +setCurrentUser() void
            +getClient() AuctionClient
        }

        class AuctionManager {
            -List auctions
            -AuctionServer server
            +getInstance() AuctionManager
            +addAuction() void
            +getAuctionById() Auction
            +startAutoClosureService() void
        }

        class AutoBidManager {
            -AutoBidDAO autoBidDAO
            -UserDAO userDAO
            -Map auctionAutoBids
            +getInstance() AutoBidManager
            +registerAutoBid() String
            +cancelAutoBid() boolean
            +executeAutoBids() void
        }

        class ConcurrentBidManager {
            -Map auctionLocks
            -Map anonymousSequences
            -AtomicLong successCount
            -AtomicLong outbidCount
            -AtomicLong failureCount
            -AtomicLong contentionCount
            +getInstance() ConcurrentBidManager
            +processBid() BidResult
            +releaseLock() void
        }
    }

    namespace network {
        class AuctionClient {
            -Socket socket
            -ObjectOutputStream out
            -ObjectInputStream in
            -boolean running
            -List bidResultListeners
            -List stringMessageListeners
            -List chatMessageListeners
            +connect() void
            +send() void
            +close() void
        }

        class AuctionServer {
            -int port
            -List clients
            -List observers
            +start() void
            +broadcast() void
            +sendToUser() void
            +broadcastToRole() void
        }

        class ClientHandler {
            -Socket socket
            -AuctionServer server
            -ObjectOutputStream out
            -ObjectInputStream in
            -String currentUserId
            -boolean active
            -Map handlers
            +run() void
            +send() void
            +getUserId() String
        }

        class ImageHttpHandler {
            -Set allowedExts
            -long maxUploadBytes
            +handle() void
            -handleGet() void
            -handlePost() void
        }
    }

    namespace network_handler {
        class RequestHandler {
            <<interface>>
            +handle() void
        }

        class AuctionHandler {
            +handle() void
            -handleConcurrentBid() void
            -handleSetAutoBid() void
            -handlePayAuction() void
        }

        class UserHandler {
            +handle() void
            -handleUpdateProfile() void
            -handleChangePassword() void
            -handleApproveSeller() void
        }

        class WalletHandler {
            +handle() void
            -handleTopUp() void
            -handleDepositRequest() void
            -handleDepositReview() void
        }

        class SocialHandler {
            +handle() void
            -handleChatSend() void
            -handleFriendRequest() void
            -handleUserSearch() void
        }

        class NotificationHandler {
            +handle() void
            -handleFetchNotifications() void
            -handleMarkNotificationRead() void
        }
    }

    namespace network_service {
        class AuctionBroadcastService {
            +broadcastBidSuccess() void
            +broadcastAuctionPaySuccess() void
            +broadcastAuctionFinish() void
            +pushBalanceUpdate() void
        }
    }

    namespace service {
        class AuthService {
            -UserDAO userDAO
            -int minAge
            -int usernameMin
            -int usernameMax
            -int passwordMin
            -Pattern namePattern
            -Pattern phonePattern
            -Pattern emailPattern
            +login() User
            +register() User
            +validatePassword() void
        }

        class AuctionService {
            +validateAuctionCreation() void
            +validateAuctionEdit() void
            +validateBid() void
            +buildBidCommand() String
        }
    }

    namespace utils {
        class AlertHelper {
            -boolean confirmResult
            +show() void
            +showConfirm() boolean
        }

        class AnimationUtils {
            -double fadeOutMs
            -double fadeInMs
            -double slideInMs
            +switchView() void
            +animateAccordion() void
            +fadeIn() void
        }

        class AuctionChartManager {
            -AreaChart chart
            -NumberAxis xAxis
            -NumberAxis yAxis
            -int maxVisibleBids
            +updateChart() void
        }

        class AuctionTimer {
            -Label lblCountdown
            -ProgressBar progressBar
            -Timeline timeline
            -Auction auction
            +start() void
            +stop() void
        }

        class ChatCenter {
            -boolean wired
            -List messageListeners
            -List bundleListeners
            -List summaryListeners
            +fetchSummariesForBadge() void
            +addMessageListener() void
            +reset() void
        }

        class FixBidder {
            +main() void
        }

        class FriendCenter {
            -boolean wired
            -List bundleListeners
            -List searchListeners
            +fetchBundle() void
            +search() void
            +sendRequest() void
            +reset() void
        }

        class ImageStorageService {
            -String baseDir
            -String storageDir
            -String avatarDir
            -Set allowedExts
            -long maxFileSize
            +saveImage() String
            +saveAvatar() String
            +uploadToServer() String
            +toImageUrl() String
        }

        class NotificationCenter {
            -List cache
            -int unreadCount
            -Label badgeRef
            -Stage currentStage
            -Consumer onAction
            +attach() void
            +fetchNow() void
            +reset() void
        }

        class NotificationService {
            -NotificationDAO dao
            +notifyUser() void
            +notifyChat() void
            +notifyAdmins() void
        }

        class PasswordMigrationTool {
            +main() void
        }

        class PasswordUtils {
            -int bcryptCost
            +hash() String
            +verify() boolean
            +isBCryptHash() boolean
        }

        class SceneManager {
            -Stage stage
            +showLogin() void
            +showRegister() void
            +showDashboard() void
            +showAdminDashboard() void
            +showModal() void
        }

        class ServerResponseService {
            +pushBalanceUpdate() void
            +sendTopUpOk() void
        }

        class SessionPermission {
            -String cachedSellerId
            -Set cachedOwnedIds
            +canEdit() boolean
            +canDelete() boolean
            +canCancel() boolean
            +canPay() boolean
        }
    }

    %% Inheritance and interfaces
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
    Observer <.. Subject

    RequestHandler <|.. AuctionHandler
    RequestHandler <|.. UserHandler
    RequestHandler <|.. WalletHandler
    RequestHandler <|.. SocialHandler
    RequestHandler <|.. NotificationHandler

    AppException <|-- AuctionException
    AppException <|-- AuthenticationException
    AppException <|-- DatabaseException
    AppException <|-- ValidationException
    AuctionException <|-- AuctionClosedException
    AuctionException <|-- InvalidBidException

    %% Main domain relations
    Auction "1" *-- "1" Item : item
    Auction "1" *-- "0..*" BidTransaction : history
    Auction "1" --> "0..*" AutoBid : autoBid
    BidTransaction "0..*" --> "1" User : bidder
    AutoBid "0..*" --> "1" User : owner
    ItemFactory ..> Item : creates
    WalletTransaction --> User
    DepositRequest --> User
    Notification --> User
    ChatMessage --> User
    Friendship --> User
    BidResult --> Auction

    %% Manager and service relations
    AppState *-- AuctionClient
    AppState --> User
    AppState --> SceneManager
    AppState --> Auction

    AuctionManager --> Auction
    AuctionManager --> AuctionDAO
    AuctionManager --> AuctionServer
    AuctionManager --> AutoBidManager
    AuctionManager --> ConcurrentBidManager

    ConcurrentBidManager --> AuctionManager
    ConcurrentBidManager --> BidTransactionDAO
    ConcurrentBidManager --> UserDAO
    ConcurrentBidManager --> AutoBidManager

    AutoBidManager --> AutoBidDAO
    AutoBidManager --> UserDAO
    AutoBidManager --> BidTransactionDAO

    AuthService --> UserDAO
    AuthService --> PasswordUtils
    AuctionService --> Auction
    AuctionService --> User

    %% Database relations
    UserDAO ..> DatabaseConnection
    ItemDAO ..> DatabaseConnection
    AuctionDAO ..> DatabaseConnection
    BidTransactionDAO ..> DatabaseConnection
    AutoBidDAO ..> DatabaseConnection
    WalletTransactionDAO ..> DatabaseConnection
    DepositRequestDAO ..> DatabaseConnection
    NotificationDAO ..> DatabaseConnection
    ChatMessageDAO ..> DatabaseConnection
    FriendshipDAO ..> DatabaseConnection

    %% Network relations
    AuctionClient ..> AuctionServer : socket
    AuctionServer "1" *-- "0..*" ClientHandler
    AuctionServer --> ImageHttpHandler
    ClientHandler --> RequestHandler
    ClientHandler --> AuctionManager
    ClientHandler --> UserDAO

    AuctionHandler --> ConcurrentBidManager
    AuctionHandler --> AutoBidManager
    AuctionHandler --> AuctionBroadcastService
    UserHandler --> UserDAO
    WalletHandler --> UserDAO
    WalletHandler --> WalletTransactionDAO
    WalletHandler --> DepositRequestDAO
    SocialHandler --> ChatMessageDAO
    SocialHandler --> FriendshipDAO
    NotificationHandler --> NotificationDAO
    AuctionBroadcastService --> AuctionServer
    NotificationService --> NotificationDAO
    NotificationService --> AuctionServer
    ServerResponseService --> ClientHandler

    %% Controller and utility relations
    ClientLauncher --> Main
    Main --> SceneManager
    Main --> AppState

    LoginController --> AuthService
    LoginController --> AppState
    RegisterController --> AuthService
    RegisterController --> AppState
    DashboardController --> AppState
    DashboardController --> AuctionClient
    AdminController --> AppState
    AdminAuctionsController --> Auction
    AdminUsersController --> User
    AdminWalletController --> DepositRequest
    BidController --> AuctionService
    BidController --> AuctionClient
    CreateSessionController --> AuctionService
    CreateSessionController --> ItemFactory
    EditSessionController --> AuctionService
    ItemCardController --> Auction
    ItemDetailsController --> Auction
    ItemDetailsController --> AuctionChartManager
    FriendsController --> FriendCenter
    MessagesController --> ChatCenter
    NotificationPopupController --> Notification
    NotificationWindowController --> Notification
    TopUpController --> AuctionClient
    UserDetailsController --> User
    AvatarCropController --> ImageStorageService
    ConfirmCancelController --> Auction
    ConfirmDeleteController --> Auction
    ConfirmPayController --> WalletTransaction
    ChangePasswordController --> AppState

    AuctionChartManager --> Auction
    AuctionTimer --> Auction
    ChatCenter --> AuctionClient
    FriendCenter --> AuctionClient
    NotificationCenter --> AuctionClient
    ImageStorageService --> ImageHttpHandler
    SessionPermission --> Auction
    AlertHelper --> ErrorResponse
    ExceptionMapper --> ErrorResponse
    ExceptionMapper --> AppException
```
