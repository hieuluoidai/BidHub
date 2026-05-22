-- ============================================================================
-- MIGRATION: Fixing Anonymous Bidding Inconsistencies
-- 
-- 1. Add is_anonymous to auto_bids table (missing in previous turn).
-- 2. Add anonymous_display_name to bid_transactions to persist generated names.
-- ============================================================================

USE auction_db;

-- 1. Add is_anonymous to auto_bids
ALTER TABLE auto_bids 
    ADD COLUMN is_anonymous TINYINT(1) NOT NULL DEFAULT 0;

-- 2. Add anonymous_display_name to bid_transactions
ALTER TABLE bid_transactions 
    ADD COLUMN anonymous_display_name VARCHAR(50) DEFAULT NULL;
