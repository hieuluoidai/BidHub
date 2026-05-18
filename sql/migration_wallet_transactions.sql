USE auction_db;

-- ------------------------------------------------------------------------------
-- MIGRATION: Tạo bảng lưu trữ lịch sử biến động số dư thực tế (Ví)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wallet_transactions (
    wallet_tx_id    VARCHAR(36)  PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL,
    amount          DOUBLE       NOT NULL,  -- Giá trị giao dịch (có thể âm hoặc dương)
    type            ENUM('TOPUP', 'PAYMENT', 'EARNING', 'REFUND') NOT NULL,
    description     VARCHAR(255),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
