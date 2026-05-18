USE auction_db;

-- ------------------------------------------------------------------------------
-- MIGRATION: Thêm cột theo dõi yêu cầu trở thành Seller
-- ------------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN pending_seller TINYINT(1) DEFAULT 0;
