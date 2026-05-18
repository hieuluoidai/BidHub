USE auction_db;

-- ------------------------------------------------------------------------------
-- MIGRATION: Đặt lại số dư mặc định cho người dùng mới là 0.00
-- ------------------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN balance SET DEFAULT 0.00;
