-- ============================================================================
-- MIGRATION: Friendships — hệ thống kết bạn
-- ============================================================================

USE auction_db;

CREATE TABLE IF NOT EXISTS friendships (
    requester_id  VARCHAR(64) NOT NULL,
    addressee_id  VARCHAR(64) NOT NULL,
    status        ENUM('PENDING','ACCEPTED','DECLINED') DEFAULT 'PENDING',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (requester_id, addressee_id),
    INDEX idx_addressee (addressee_id, status),
    FOREIGN KEY (requester_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (addressee_id) REFERENCES users(user_id) ON DELETE CASCADE
);
