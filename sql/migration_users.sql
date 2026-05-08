-- ============================================================================
-- MIGRATION: Bảng users
-- ============================================================================

USE auction_db;

-- 1. Thêm cột thông tin cá nhân (NULL-able để tương thích các tài khoản cũ)
ALTER TABLE users
    ADD COLUMN full_name      VARCHAR(150)  NULL AFTER password,
    ADD COLUMN date_of_birth  DATE          NULL AFTER full_name,
    ADD COLUMN phone_number   VARCHAR(20)   NULL AFTER date_of_birth;

-- 2. Mở rộng cột password để chứa BCrypt hash 
ALTER TABLE users
    MODIFY COLUMN password VARCHAR(72) NOT NULL;

-- (Tuỳ chọn) Cập nhật thông tin cá nhân cho 3 tài khoản mẫu sẵn có
UPDATE users SET full_name = 'System Administrator', phone_number = '0900000001'
    WHERE username = 'admin';
UPDATE users SET full_name = 'Default Seller',       phone_number = '0900000002'
    WHERE username = 'seller';
UPDATE users SET full_name = 'Default Bidder',       phone_number = '0900000003'
    WHERE username = 'bidder';
