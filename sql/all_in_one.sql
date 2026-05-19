-- ============================================================================
-- ALL-IN-ONE SCHEMA + MIGRATIONS (chạy 1 lần từ DB trống)
-- Thứ tự đã sắp xếp để không vi phạm dependency giữa các cột.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. DATABASE
-- ----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS auction_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auction_db;

-- ============================================================================
-- 1. SCHEMA GỐC (từ schema.sql)
--    Lưu ý: cột `bid_type` được tách sang migration phía dưới để giữ schema
--    gốc "phẳng" và migration tự bổ sung.
-- ============================================================================

-- 1.1 USERS
CREATE TABLE IF NOT EXISTS users (
    user_id     VARCHAR(36)  PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL DEFAULT 'BIDDER',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 1.2 ITEMS
CREATE TABLE IF NOT EXISTS items (
    item_id          VARCHAR(36)  PRIMARY KEY,
    item_name        VARCHAR(200) NOT NULL,
    description      TEXT,
    starting_price   DOUBLE       NOT NULL,
    item_type        ENUM('ELECTRONICS', 'ART', 'VEHICLE') NOT NULL,
    seller_id        VARCHAR(36)  NOT NULL,

    brand            VARCHAR(100),
    warranty_months  INT,
    artist           VARCHAR(100),
    material         VARCHAR(100),
    model            VARCHAR(100),
    manufacture_year INT,

    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- 1.3 AUCTIONS
CREATE TABLE IF NOT EXISTS auctions (
    auction_id  VARCHAR(36)  PRIMARY KEY,
    item_id     VARCHAR(36)  NOT NULL,
    status      ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') NOT NULL DEFAULT 'OPEN',
    start_time  DATETIME     NOT NULL,
    end_time    DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE
);

-- 1.4 BID_TRANSACTIONS (chưa có bid_type — sẽ thêm ở migration bên dưới)
CREATE TABLE IF NOT EXISTS bid_transactions (
    transaction_id  VARCHAR(36) PRIMARY KEY,
    auction_id      VARCHAR(36) NOT NULL,
    bidder_id       VARCHAR(36) NOT NULL,
    bid_amount      DOUBLE      NOT NULL,
    bid_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id)  REFERENCES users(user_id)       ON DELETE CASCADE
);

-- 1.5 Dữ liệu mẫu (3 vai trò)
INSERT IGNORE INTO users (user_id, username, email, password, role) VALUES
    ('u-001', 'admin',  'admin@auction.vn',  'admin123',  'ADMIN'),
    ('u-002', 'seller', 'seller@auction.vn', 'seller123', 'SELLER'),
    ('u-003', 'bidder', 'bidder@auction.vn', 'bidder123', 'BIDDER');


-- ============================================================================
-- 2. MIGRATION USERS — thông tin cá nhân + mở rộng password (BCrypt)
--    PHẢI chạy trước migration balance (balance được đặt AFTER phone_number)
-- ============================================================================
ALTER TABLE users
    ADD COLUMN full_name     VARCHAR(150) NULL AFTER password,
    ADD COLUMN date_of_birth DATE         NULL AFTER full_name,
    ADD COLUMN phone_number  VARCHAR(20)  NULL AFTER date_of_birth;

ALTER TABLE users
    MODIFY COLUMN password VARCHAR(72) NOT NULL;

UPDATE users SET full_name = 'System Administrator', phone_number = '0900000001' WHERE username = 'admin';
UPDATE users SET full_name = 'Default Seller',       phone_number = '0900000002' WHERE username = 'seller';
UPDATE users SET full_name = 'Default Bidder',       phone_number = '0900000003' WHERE username = 'bidder';


-- ============================================================================
-- 3. MIGRATION BALANCE + AUCTION STATES
--    Cần phone_number (đã thêm ở bước 2).
-- ============================================================================
ALTER TABLE users
    ADD COLUMN balance DECIMAL(15,2) NOT NULL DEFAULT 10000.00 AFTER phone_number;

ALTER TABLE auctions
    MODIFY COLUMN status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED')
    NOT NULL DEFAULT 'OPEN';

UPDATE users SET balance = 1000000.00 WHERE username IN ('admin', 'seller', 'bidder');


-- ============================================================================
-- 4. MIGRATION: đặt lại DEFAULT balance = 0.00 cho user mới
-- ============================================================================
ALTER TABLE users ALTER COLUMN balance SET DEFAULT 0.00;


-- ============================================================================
-- 5. MIGRATION AUTO-BID
--    Cần cột balance (đã có từ bước 3).
-- ============================================================================
ALTER TABLE users
    ADD COLUMN locked_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00 AFTER balance;

CREATE TABLE IF NOT EXISTS auto_bids (
    auto_bid_id  VARCHAR(36)   PRIMARY KEY,
    auction_id   VARCHAR(36)   NOT NULL,
    user_id      VARCHAR(36)   NOT NULL,
    max_bid      DECIMAL(15,2) NOT NULL,
    increment    DECIMAL(15,2) NOT NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(user_id)       ON DELETE CASCADE,

    UNIQUE KEY (auction_id, user_id)
);


-- ============================================================================
-- 6. MIGRATION BID_TYPE — ghi nguồn của bid (MANUAL/AUTO)
-- ============================================================================
ALTER TABLE bid_transactions
    ADD COLUMN bid_type ENUM('MANUAL', 'AUTO_BID') NOT NULL DEFAULT 'MANUAL' AFTER bid_time;


-- ============================================================================
-- 7. MIGRATION ITEM IMAGE
-- ============================================================================
ALTER TABLE items
    ADD COLUMN image_path VARCHAR(500) DEFAULT NULL AFTER description;


-- ============================================================================
-- 8. MIGRATION PENDING SELLER
-- ============================================================================
ALTER TABLE users
    ADD COLUMN pending_seller TINYINT(1) DEFAULT 0;


-- ============================================================================
-- 9. MIGRATION USER AVATAR
-- ============================================================================
ALTER TABLE users
    ADD COLUMN avatar_path VARCHAR(255) DEFAULT NULL;


-- ============================================================================
-- 10. MIGRATION WALLET TRANSACTIONS
-- ============================================================================
CREATE TABLE IF NOT EXISTS wallet_transactions (
    wallet_tx_id  VARCHAR(36)  PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    amount        DOUBLE       NOT NULL,
    type          ENUM('TOPUP', 'PAYMENT', 'EARNING', 'REFUND') NOT NULL,
    description   VARCHAR(255),
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);


-- ============================================================================
-- 11. MIGRATION NOTIFICATIONS — Trung tâm thông báo
-- ============================================================================
CREATE TABLE IF NOT EXISTS notifications (
    notification_id  VARCHAR(36)  PRIMARY KEY,
    user_id          VARCHAR(36)  NOT NULL,
    type             VARCHAR(40)  NOT NULL,
    title            VARCHAR(150) NOT NULL,
    message          VARCHAR(500) NOT NULL,
    is_read          TINYINT(1)   NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_unread (user_id, is_read, created_at),

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);


-- ============================================================================
-- DONE. Verify nhanh:
--   SHOW TABLES;
--   DESCRIBE users;
--   SELECT username, role, balance, locked_balance FROM users;
-- ============================================================================
