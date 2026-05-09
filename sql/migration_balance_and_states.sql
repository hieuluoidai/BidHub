-- ============================================================================
-- MIGRATION: Bổ sung tính năng PAID/CANCELED + ví ảo (balance)
--
-- Thực hiện 3 việc:
--   1. Thêm cột balance vào bảng users (mặc định 10000 cho user mới)
--   2. Đảm bảo cột status của auctions có đủ ENUM ('OPEN', 'RUNNING',
--      'FINISHED', 'PAID', 'CANCELED') — schema cũ đã có rồi nên đây là
--      "safety check"
--   3. Cập nhật balance cho 3 tài khoản mẫu admin/seller/bidder = 1,000,000
--
-- Cách chạy:
--   USE auction_db;
--   SOURCE đường/dẫn/đến/migration_balance_and_states.sql;
-- ============================================================================

USE auction_db;

-- 1. Thêm cột balance (mặc định 10000 cho user mới đăng ký)
ALTER TABLE users
    ADD COLUMN balance DECIMAL(15,2) NOT NULL DEFAULT 10000.00 AFTER phone_number;

-- 2. Đảm bảo ENUM status đủ 5 trạng thái (an toàn — câu này không phá nếu đã đủ)
ALTER TABLE auctions
    MODIFY COLUMN status ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED')
    NOT NULL DEFAULT 'OPEN';

-- 3. Set balance $1,000,000 cho 3 tài khoản mẫu (demo thoải mái)
UPDATE users SET balance = 1000000.00 WHERE username IN ('admin', 'seller', 'bidder');

-- Hiển thị kết quả để verify
SELECT username, role, balance FROM users;
