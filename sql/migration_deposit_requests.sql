-- Migration: Bảng yêu cầu nạp tiền qua chuyển khoản ngân hàng
-- Chạy lệnh này nếu DB đã tồn tại (không dùng all_in_one.sql):
--   mysql -u root -p auction_db < migration_deposit_requests.sql

CREATE TABLE IF NOT EXISTS deposit_requests (
    request_id   VARCHAR(30)   PRIMARY KEY,
    user_id      VARCHAR(36)   NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    status       ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    admin_note   VARCHAR(255)  DEFAULT NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at  DATETIME      DEFAULT NULL,

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
