# BidHub — Hệ thống đấu giá trực tuyến

Dự án môn Lập trình Hướng đối tượng Nâng cao (LTNC), UET-VNU, Nhóm 6.

## Yêu cầu hệ thống

- **Java 21+** (khuyến nghị JDK 21 LTS)
- **MySQL 8.0+**
- Hệ điều hành: Windows 10/11

## Cài đặt Database

Chạy file tổng hợp:

```bash
mysql -u root -p auction_db < sql/all_in_one.sql
```

Hoặc chạy từng file trong `sql/` theo thứ tự: `schema.sql` → các file `migration_*.sql`.

Mặc định kết nối: `root / password` tại `localhost:3306`.
Thay đổi trong `src/main/java/database/DatabaseConnection.java` nếu cần.

## Cấu hình địa chỉ Server

Chỉnh file `src/main/resources/server.properties`:

```properties
server.host=localhost   # Thay bằng IP server nếu chạy từ xa
server.port=1234
```

## Build từ source

```bash
mvn clean package -DskipTests
```

Sau khi build, 2 file JAR xuất hiện trong `target/`:

| File | Mô tả |
|------|-------|
| `bidhub-server.jar` | Server — không cần JavaFX, chạy trên mọi OS |
| `bidhub-client.jar` | Client — đã đóng gói JavaFX native Windows |

## Chạy ứng dụng

### Bước 1 — Khởi động Server

```bash
java -jar bidhub-server.jar
```

Server lắng nghe tại port **1234**. Giữ cửa sổ này mở trong suốt phiên làm việc.

### Bước 2 — Khởi động Client

```bash
java -jar bidhub-client.jar
```

Có thể mở nhiều cửa sổ client để test đấu giá đồng thời.

## Chạy từ source (dành cho phát triển)

```bash
# Terminal 1 — Server
mvn exec:java -Dexec.mainClass="network.AuctionServer"

# Terminal 2 — Client
mvn javafx:run
```

## Tính năng chính

| Tính năng | Mô tả |
|-----------|-------|
| Đăng ký / Đăng nhập | 3 vai trò: Bidder, Seller, Admin |
| Quản lý sản phẩm | Electronics, Art, Vehicle; CRUD đầy đủ |
| Đấu giá realtime | Cập nhật giá tức thì qua Socket + Observer Pattern |
| Concurrent Bidding | ReentrantLock per-auction, tránh lost update & race condition |
| Anti-Sniping | Bid trong 10s cuối → tự động gia hạn thêm 60s |
| Auto-Bidding | Đặt maxBid + increment, hệ thống tự đấu thay người dùng |
| Hệ thống ví | Nạp tiền, khóa số dư, thanh toán atomic |
| Trung tâm thông báo | Push realtime, badge, đánh dấu đã đọc |

## Kiến trúc hệ thống

```
Client (JavaFX + FXML)          Server (Java)
──────────────────────          ─────────────────────
Controller Layer    ──Socket──▶ ClientHandler (per thread)
  LoginController               AuctionManager (Singleton)
  DashboardController           ConcurrentBidManager
  BidController                 AutoBidManager
  AdminController               AuctionServer :1234
                                     │
                                MySQL :3306
```

## Design Patterns sử dụng

- **Singleton**: AuctionManager, ConcurrentBidManager, AutoBidManager, AppState
- **Factory Method**: ItemFactory (tạo Electronics / Art / Vehicle)
- **Observer**: Auction → AuctionClient, cập nhật realtime không polling
- **DAO Pattern**: UserDAO, AuctionDAO, ItemDAO, BidTransactionDAO, ...

---

## Thành viên nhóm

| STT | Họ và Tên | Vai trò & Nhiệm vụ |
| :---: | :--- | :--- |
| **1** | **Nguyễn Trung Hiếu** | ** Logic nghiệp vụ & OOP Design**<br>• Thiết kế cây kế thừa (User, Item, Entity)<br>• Triển khai Design Patterns (Singleton, Factory, Observer)<br>• Logic đấu giá, xử lý bid, xử lý đồng thời<br>• Viết Unit Test (JUnit) |
| **2** | **Đinh Hoàng Bách** | ** Network – Client & Server**<br>• Xây dựng AuctionServer, AuctionClient, ClientHandler<br>• Giao thức giao tiếp qua Socket + ObjectStream<br>• Xử lý đa luồng server (multi-client)<br>• Realtime broadcast & update giữa các client |
| **3** | **Trần Mạnh Dũng** | ** JavaFX UI & Controllers**<br>• Thiết kế FXML (Login, Dashboard, Admin, Bid Dialog)<br>• CSS styling cho ứng dụng<br>• Viết Controller cho từng màn hình<br>• SceneManager & AlertHelper |
| **4** | **Nguyễn Văn Khánh Duy** | ** Database & SQL**<br>• Thiết kế schema MySQL (users, items, auctions, bid_transactions)<br>• Viết các lớp DAO (UserDAO, AuctionDAO, ItemDAO, BidTransactionDAO)<br>• Quản lý kết nối database (DatabaseConnection Singleton)<br>• Truy vấn phức tạp (tìm người thắng, đếm bid, thống kê) |

<br>
<div align="center">Made with ❤️ by Nhóm 6 — UET VNU — 2026</div>
