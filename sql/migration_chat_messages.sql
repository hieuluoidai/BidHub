-- ============================================================================
-- MIGRATION CHAT MESSAGES — tin nhắn 1-1 giữa 2 user
-- ============================================================================

USE auction_db;

CREATE TABLE IF NOT EXISTS chat_messages (
    message_id   VARCHAR(64) PRIMARY KEY,
    sender_id    VARCHAR(64) NOT NULL,
    receiver_id  VARCHAR(64) NOT NULL,
    content      TEXT        NOT NULL,
    sent_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    read_at      TIMESTAMP   NULL,
    liked        TINYINT(1)  DEFAULT 0,

    INDEX idx_pair_ab         (sender_id, receiver_id),
    INDEX idx_pair_ba         (receiver_id, sender_id),
    INDEX idx_receiver_unread (receiver_id, read_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
