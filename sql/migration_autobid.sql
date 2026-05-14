-- ============================================================================
-- MIGRATION: Auto-Bidding Feature
--
-- 1. Add locked_balance to users table to hold money for Auto-Bids.
-- 2. Create auto_bids table to store auto-bid configurations.
-- ============================================================================

USE auction_db;

-- 1. Add locked_balance to users table
ALTER TABLE users
    ADD COLUMN locked_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00 AFTER balance;

-- 2. Create auto_bids table
CREATE TABLE IF NOT EXISTS auto_bids (
    auto_bid_id     VARCHAR(36) PRIMARY KEY,
    auction_id      VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    max_bid         DECIMAL(15,2) NOT NULL,
    increment       DECIMAL(15,2) NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign keys
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,

    -- One auto-bid per user per auction
    UNIQUE KEY (auction_id, user_id)
);
