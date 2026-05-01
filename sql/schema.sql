-- Tạo database mới nếu chưa tồn tại, hỗ trợ lưu trữ tiếng Việt (utf8mb4)
CREATE DATABASE IF NOT EXISTS auction_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auction_db;

-- ------------------------------------------------------------------------------
-- 1. BẢNG USERS: Lưu trữ thông tin tài khoản người dùng
-- Quản lý xác thực và phân quyền (Role-based access control)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id     VARCHAR(36)  PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL DEFAULT 'BIDDER',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 2. BẢNG ITEMS: Lưu trữ thông tin sản phẩm đấu giá
-- Sử dụng chiến lược "Single Table Inheritance" để lưu trữ đa hình các loại hàng
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS items (
    -- Thông tin chung của mọi sản phẩm
    item_id         VARCHAR(36)  PRIMARY KEY,
    item_name       VARCHAR(200) NOT NULL,
    description     TEXT,
    starting_price  DOUBLE       NOT NULL,
    item_type       ENUM('ELECTRONICS', 'ART', 'VEHICLE') NOT NULL,
    seller_id       VARCHAR(36)  NOT NULL,

    -- Các cột thuộc tính riêng biệt (Sẽ có giá trị NULL nếu không đúng loại hàng)
    brand           VARCHAR(100),  -- Dùng chung cho Electronics và Vehicle
    warranty_months INT,           -- Chỉ dành cho Electronics (Tháng bảo hành)
    artist          VARCHAR(100),  -- Chỉ dành cho Art (Tác giả)
    material        VARCHAR(100),  -- Chỉ dành cho Art (Chất liệu)
    model           VARCHAR(100),  -- Chỉ dành cho Vehicle (Dòng xe)
    manufacture_year INT,          -- Chỉ dành cho Vehicle (Năm sản xuất)
    
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Khóa ngoại: Nếu xóa tài khoản người bán, toàn bộ sản phẩm của họ sẽ bị xóa theo
    FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 3. BẢNG AUCTIONS: Quản lý thông tin và vòng đời của một phiên đấu giá
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auctions (
    auction_id  VARCHAR(36)  PRIMARY KEY,
    item_id     VARCHAR(36)  NOT NULL,
    status      ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') NOT NULL DEFAULT 'OPEN',
    start_time  DATETIME     NOT NULL,
    end_time    DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Khóa ngoại: Nếu xóa sản phẩm, phiên đấu giá tương ứng cũng bị xóa theo
    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 4. BẢNG BID_TRANSACTIONS: Ghi chép lịch sử đặt giá 
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bid_transactions (
    transaction_id  VARCHAR(36) PRIMARY KEY,
    auction_id      VARCHAR(36) NOT NULL,
    bidder_id       VARCHAR(36) NOT NULL,
    bid_amount      DOUBLE      NOT NULL,
    bid_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Khóa ngoại: Đảm bảo tính toàn vẹn dữ liệu khi có thay đổi từ bảng cha
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id)  REFERENCES users(user_id)    ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_bid_auction_amount
    ON bid_transactions (auction_id, bid_amount DESC);

CREATE INDEX IF NOT EXISTS idx_bid_bidder
    ON bid_transactions (bidder_id);

-- ------------------------------------------------------------------------------
-- 5. DỮ LIỆU MẪU
-- Cung cấp sẵn các tài khoản với đủ 3 vai trò để kiểm thử ứng dụng
-- ------------------------------------------------------------------------------
INSERT INTO users (user_id, username, email, password, role) VALUES
('u-001', 'admin', 'admin@auction.vn', 'admin123', 'ADMIN'),
('u-002', 'seller', 'seller@auction.vn', 'seller123', 'SELLER'),
('u-003', 'bidder', 'bidder@auction.vn', 'bidder123', 'BIDDER');