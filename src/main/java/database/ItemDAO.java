package database;

import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.Vehicle;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp chịu trách nhiệm lưu trữ và truy xuất thông tin Item từ MySQL.
 */
public class ItemDAO {

    private final Connection conn;

    /**
     * Lấy kết nối Database dùng chung từ Singleton.
     */
    public ItemDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Lưu một item mới vào DB cùng với thông tin seller.
     */
    public boolean save(Item item, String sellerId) {
        String sql = """
            INSERT INTO items 
                (item_id, item_name, description, image_path, starting_price, item_type, seller_id,
                 brand, warranty_months, artist, material, model, manufacture_year)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getItemId());
            stmt.setString(2, item.getItemName());
            stmt.setString(3, item.getDescription());
            // image_path: NULL nếu không có ảnh
            if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
                stmt.setString(4, item.getImagePath());
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }
            stmt.setDouble(5, item.getStartingPrice());
            stmt.setString(6, item.getItemType().toUpperCase());
            stmt.setString(7, sellerId);

            // Mặc định tất cả cột đặc thù là NULL
            stmt.setNull(8,  Types.VARCHAR); // brand
            stmt.setNull(9,  Types.INTEGER); // warranty_months
            stmt.setNull(10, Types.VARCHAR); // artist
            stmt.setNull(11, Types.VARCHAR); // material
            stmt.setNull(12, Types.VARCHAR); // model
            stmt.setNull(13, Types.INTEGER); // manufacture_year

            // Override đúng cột theo từng loại
            if (item instanceof Electronics e) {
                stmt.setString(8, e.getBrand());
            } else if (item instanceof Art a) {
                stmt.setString(10, a.getArtist());
            } else if (item instanceof Vehicle v) {
                stmt.setString(8, v.getBrand());
            }

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu sản phẩm: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy toàn bộ danh sách sản phẩm đang có trong DB (Dùng khi Server khởi động).
     */
    public List<Item> findAll() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // Duyệt qua từng dòng kết quả và biến nó thành đối tượng Java
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách sản phẩm: " + e.getMessage());
        }
        return items;
    }

    /**
     * Tìm một sản phẩm cụ thể dựa trên ID.
     */
    public Item findById(String itemId) {
        String sql = "SELECT * FROM items WHERE item_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToItem(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm sản phẩm theo ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật thông tin một sản phẩm đã có trong DB.
     * Lưu ý: Không cho phép đổi item_type (Electronics/Art/Vehicle)
     * vì các cột đặc thù (brand, artist...) phụ thuộc vào type.
     *
     * @param item     Item đã được cập nhật (cùng item_id, có thể đổi name/desc/price/extra)
     * @param sellerId ID của người bán — để đảm bảo chỉ chủ phiên mới sửa được
     * @return true nếu update thành công ít nhất 1 dòng
     */
    public boolean update(Item item, String sellerId) {
        // Mệnh đề WHERE có cả seller_id để chống "tampering":
        // người khác mạo danh cũng không sửa được sản phẩm của người khác
        String sql = """
            UPDATE items
               SET item_name      = ?,
                   description    = ?,
                   image_path     = ?,
                   starting_price = ?,
                   brand          = ?,
                   artist         = ?
             WHERE item_id   = ?
               AND seller_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getItemName());
            stmt.setString(2, item.getDescription());
            // image_path: cho phép NULL nếu seller xóa ảnh
            if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
                stmt.setString(3, item.getImagePath());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            stmt.setDouble(4, item.getStartingPrice());

            // Reset cả 2 cột đặc thù về NULL trước, sau đó set đúng cột theo type
            stmt.setNull(5, Types.VARCHAR); // brand
            stmt.setNull(6, Types.VARCHAR); // artist

            if (item instanceof Electronics e) {
                stmt.setString(5, e.getBrand());
            } else if (item instanceof Art a) {
                stmt.setString(6, a.getArtist());
            } else if (item instanceof Vehicle v) {
                stmt.setString(5, v.getBrand());
            }

            stmt.setString(7, item.getItemId());
            stmt.setString(8, sellerId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật sản phẩm: " + e.getMessage());
            return false;
        }
    }

    /**
     * Phiên bản update cho Admin: không kiểm tra seller_id nên admin có quyền sửa mọi sản phẩm.
     */
    public boolean updateAsAdmin(Item item) {
        String sql = """
            UPDATE items
               SET item_name      = ?,
                   description    = ?,
                   image_path     = ?,
                   starting_price = ?,
                   brand          = ?,
                   artist         = ?
             WHERE item_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getItemName());
            stmt.setString(2, item.getDescription());
            if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
                stmt.setString(3, item.getImagePath());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            stmt.setDouble(4, item.getStartingPrice());

            stmt.setNull(5, Types.VARCHAR);
            stmt.setNull(6, Types.VARCHAR);

            if (item instanceof Electronics e) {
                stmt.setString(5, e.getBrand());
            } else if (item instanceof Art a) {
                stmt.setString(6, a.getArtist());
            } else if (item instanceof Vehicle v) {
                stmt.setString(5, v.getBrand());
            }

            stmt.setString(7, item.getItemId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật sản phẩm (admin): " + e.getMessage());
            return false;
        }
    }

    /**
     * Tìm seller_id của một sản phẩm — phục vụ cho việc kiểm tra quyền sở hữu.
     */
    public String findSellerIdByItemId(String itemId) {
        String sql = "SELECT seller_id FROM items WHERE item_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("seller_id");
        } catch (SQLException e) {
            System.err.println("Lỗi tìm seller_id của sản phẩm: " + e.getMessage());
        }
        return null;
    }

    /**
     * Xóa một sản phẩm khỏi DB.
     */
    public boolean delete(String itemId) {
        String sql = "DELETE FROM items WHERE item_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);

            // executeUpdate trả về số dòng bị ảnh hưởng. Nếu > 0 nghĩa là xóa thành công.
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tiện ích: Biến đổi một dòng dữ liệu thô từ MySQL thành đúng loại đối tượng Item.
     */
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        // Lấy các thuộc tính chung
        String itemId        = rs.getString("item_id");
        String itemName      = rs.getString("item_name");
        String description   = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");

        // Dùng item_type để quyết định sẽ gọi Constructor nào
        String itemType      = rs.getString("item_type").toUpperCase();

        Item item = switch (itemType) {
            // Nếu là đồ điện tử, lấy thêm cột 'brand'
            case "ELECTRONICS" -> new Electronics(itemId, itemName, description, startingPrice, rs.getString("brand"));
            // Nếu là đồ điện tử, lấy thêm cột 'artist'
            case "ART"         -> new Art(itemId, itemName, description, startingPrice, rs.getString("artist"));
            // Nếu là xe cộ, lấy cột 'brand'
            case "VEHICLE"     -> new Vehicle(itemId, itemName, description, startingPrice, rs.getString("brand"));

            default -> throw new SQLException("Lỗi dữ liệu: Loại sản phẩm không hợp lệ: " + itemType);
        };

        // Đọc image_path — bọc try/catch cho tương thích DB cũ chưa migrate
        try {
            item.setImagePath(rs.getString("image_path"));
        } catch (SQLException ignore) { /* DB chưa có cột image_path */ }

        return item;
    }
}