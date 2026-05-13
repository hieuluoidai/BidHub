-- ============================================================================
-- MIGRATION: Thêm cột image_path để lưu đường dẫn ảnh sản phẩm
--
-- Ảnh được lưu trong thư mục ./uploads/items/ của project, DB chỉ lưu
-- relative path (vd: "items/u-abc123.jpg") để dễ portability.
-- ============================================================================

USE auction_db;

-- 1. Thêm cột image_path (NULLABLE — sản phẩm không có ảnh thì NULL)
ALTER TABLE items
    ADD COLUMN image_path VARCHAR(500) DEFAULT NULL AFTER description;

-- 2. Verify
DESCRIBE items;
