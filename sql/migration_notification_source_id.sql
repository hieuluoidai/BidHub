USE auction_db;

SET @add_source_id = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'notifications'
              AND column_name = 'source_id'
        ),
        'SELECT ''notifications.source_id already exists''',
        'ALTER TABLE notifications ADD COLUMN source_id VARCHAR(64) DEFAULT NULL'
    )
);
PREPARE stmt FROM @add_source_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx_user_unread = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'notifications'
              AND index_name = 'idx_user_unread'
        ),
        'SELECT ''idx_user_unread already exists''',
        'CREATE INDEX idx_user_unread ON notifications (user_id, is_read, created_at)'
    )
);
PREPARE stmt FROM @add_idx_user_unread;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx_chat_upsert = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'notifications'
              AND index_name = 'idx_chat_upsert'
        ),
        'SELECT ''idx_chat_upsert already exists''',
        'CREATE INDEX idx_chat_upsert ON notifications (user_id, type, source_id, is_read)'
    )
);
PREPARE stmt FROM @add_idx_chat_upsert;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
