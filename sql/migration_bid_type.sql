-- ============================================================================
-- MIGRATION: Persist bid source for realtime chart/history
-- ============================================================================

USE auction_db;

ALTER TABLE bid_transactions
    ADD COLUMN bid_type ENUM('MANUAL', 'AUTO_BID') NOT NULL DEFAULT 'MANUAL' AFTER bid_time;