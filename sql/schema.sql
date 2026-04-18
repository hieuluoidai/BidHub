CREATE DATABASE IF NOT EXISTS auction_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auction_db;
CREATE TABLE IF NOT EXISTS users (
    user_id     VARCHAR(36)  PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL DEFAULT 'BIDDER',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS items (
    item_id         VARCHAR(36)  PRIMARY KEY,
    item_name       VARCHAR(200) NOT NULL,
    description     TEXT,
    starting_price  DOUBLE       NOT NULL,
    item_type       ENUM('ELECTRONICS', 'ART', 'VEHICLE') NOT NULL,
    seller_id       VARCHAR(36)  NOT NULL,

    brand           VARCHAR(100),
    warranty_months INT,
    artist          VARCHAR(100),
    material        VARCHAR(100),
    model           VARCHAR(100), 
    manufacture_year INT, 
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auctions (
    auction_id  VARCHAR(36)  PRIMARY KEY,
    item_id     VARCHAR(36)  NOT NULL,
    status      ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED')
                NOT NULL DEFAULT 'OPEN',
    start_time  DATETIME     NOT NULL,
    end_time    DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bid_transactions (
    transaction_id  VARCHAR(36) PRIMARY KEY,
    auction_id      VARCHAR(36) NOT NULL,
    bidder_id       VARCHAR(36) NOT NULL,
    bid_amount      DOUBLE      NOT NULL,
    bid_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id)  REFERENCES users(user_id)    ON DELETE CASCADE
);
INSERT INTO users (user_id, username, email, password, role) VALUES
('u-001', 'admin', 'admin@auction.vn', 'admin123', 'ADMIN'),
('u-002', 'seller', 'seller@auction.vn', 'seller123', 'SELLER'),
('u-003', 'bidder', 'bidder@auction.vn', 'bidder123', 'BIDDER');

