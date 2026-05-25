# KỊCH BẢN VIDEO DEMO DỰ ÁN BIDHUB
**Thời lượng ước tính:** 3:00 - 5:00 phút (Có thể cắt ghép)
**Mục tiêu:** Showcase kiến trúc Client-Server, Real-time update, Concurrent handling và UX/UI.

---

### PHẦN I: KHỞI ĐẦU & CÁ NHÂN HÓA (1 Client)
*   **Mở đầu:** Mở ứng dụng từ file .exe. Mở liên tiếp 4 cửa sổ (clients) và sắp xếp chúng trên màn hình (Letterboxing/Scaling showcase).
*   **Đăng ký & Hồ sơ:**
    *   Thực hiện đăng ký 1 tài khoản mới.
    *   Vào **User Profile**: Thay đổi thông tin, đổi mật khẩu.
    *   **Showcase ảnh đại diện:** Sử dụng tính năng **Avatar Crop** (cắt ảnh) để thay đổi profile picture.
    *   *Lưu ý:* Chỉ ra sự thay đổi tức thì trên Sidebar.

### PHẦN II: TRẢI NGHIỆM NGƯỜI DÙNG & TƯƠNG TÁC (2 Clients)
*   **Duyệt danh mục:** Duyệt qua các Tab: Vehicle, Art, Electronics. Sử dụng thanh tìm kiếm để lọc sản phẩm.
*   **Yêu thích (Favorites):** Nhấn icon "Ngôi sao" trên một sản phẩm -> Quay lại Home để thấy sản phẩm đó tự động nhảy lên đầu danh sách (Logic Priority).
*   **Kết bạn & Real-time Chat:**
    *   **Client A (Bidder)** gửi lời mời kết bạn cho **Client B (Seller)** từ trang chi tiết sản phẩm.
    *   **Client B** nhận thông báo đẩy (Toast Notification) ngay lập tức.
    *   Chấp nhận kết bạn -> Mở cửa sổ Chat và gửi tin nhắn qua lại (Showcase **Socket Real-time** và **Unread Badge**).

### PHẦN III: LOGIC ĐẤU GIÁ BIẾN ĐỘNG (3-4 Clients)
*   **Khởi tạo:** Seller tạo 1 phiên đấu giá mới (Thời gian ngắn: 45s).
*   **Trạng thái Real-time:** Showcase cảnh phiên chuyển từ 'OPEN' (Chờ) sang 'LIVE' (Đang diễn ra).
*   **Cuộc chiến đặt giá:**
    *   **QuickBid:** Bidder 1 nhấn +100k, +200k. Giá nhảy tức thì trên máy Seller và các Bidder khác.
    *   **Anonymous Bid (Ẩn danh):** Bidder 2 đặt giá ẩn danh. Showcase bảng **Bid History** hiện "AnonymousBidder_XXX".
    *   **Dueling Auto-Bid:** 
        *   Bidder 1 cài Auto-bid (Max 3tr, bước giá 100k).
        *   Bidder 2 cài Auto-bid (Max 2.5tr, bước giá 150k).
        *   **Visual:** Showcase cảnh giá tự động "nhảy" liên tục (Recursive bidding) cho đến khi Bidder 1 dẫn đầu ở mức ~2.6tr.
*   **Anti-sniping (Chống bắn tỉa):** Đợi đến 10 giây cuối, Bidder đặt giá mới. Showcase thông báo: **"Phiên được gia hạn thêm 60 giây"** để đảm bảo công bằng.

### PHẦN IV: KẾT THÚC & THANH TOÁN (2 Clients)
*   **Winner:** Đồng hồ về 0. Cửa sổ **Winner Dialog** hiện lên chúc mừng người thắng cuộc.
*   **Thanh toán:**
    *   Người thắng vào Dashboard -> Nhấn "Pay Now".
    *   **Showcase Ví (Wallet):** Cả hai máy cùng mở tab Wallet. Khi bấm thanh toán, tiền từ 'Locked Balance' của Bidder trừ đi và 'Available Balance' của Seller cộng vào ngay lập tức kèm theo **Lịch sử giao dịch (Transaction History)**.
    *   Trạng thái phiên chuyển sang 'PAID'.

### PHẦN V: QUẢN TRỊ & HỆ THỐNG (2-3 Clients)
*   **Nạp tiền (Top-up):** Bidder yêu cầu nạp tiền -> Admin duyệt yêu cầu -> Tiền cộng vào tài khoản Bidder (Real-time update).
*   **Cấp quyền Seller:** Bidder nhấn yêu cầu trở thành Seller -> Admin phê duyệt -> Bidder nhận thông báo và menu "Create Session" xuất hiện.
*   **Concurrent Bidding (Xử lý đồng thời):**
    *   Hai Bidder cùng nhấn nút đặt giá tại cùng 1 thời điểm cực ngắn.
    *   **Showcase:** Hệ thống chỉ chấp nhận 1 người nhanh nhất (Atomic Lock), người còn lại báo lỗi yêu cầu đặt giá cao hơn.

### PHẦN VI: TỔNG KẾT (1 Client - Admin)
*   Admin duyệt nhanh qua các tab quản trị: Thống kê, Quản lý User (Khóa/Mở), Quản lý tất cả phiên đấu giá.
*   Dừng lại ở tab Users để kết thúc video.

---
**Ghi chú cho hậu kỳ:**
*   Sử dụng hiệu ứng phóng to (Zoom) vào các vùng thông báo (Toast) và đồng hồ đếm ngược.
*   Thêm text chú thích các công nghệ sử dụng (Socket.io, ReentrantLock, JavaFX Scaling) ở các đoạn tương ứng.