-- ============================================================================
-- MIGRATION: Notification center
-- Lưu thông báo cho user (BIDDER/SELLER) và admin.
-- ============================================================================

USE auction_db;

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
