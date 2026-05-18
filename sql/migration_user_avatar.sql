-- Thêm cột avatar_path cho bảng users
ALTER TABLE users ADD COLUMN avatar_path VARCHAR(255) DEFAULT NULL;
