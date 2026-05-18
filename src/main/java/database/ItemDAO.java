package database;

import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.Vehicle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp chịu trách nhiệm lưu trữ và truy xuất thông tin Item từ MySQL sử dụng Connection Pool.
 */
public class ItemDAO {

    public ItemDAO() {
    }

    public boolean save(Item item, String sellerId) {
        String sql = """
            INSERT INTO items 
                (item_id, item_name, description, image_path, starting_price, item_type, seller_id,
                 brand, warranty_months, artist, material, model, manufacture_year)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getItemId());
            stmt.setString(2, item.getItemName());
            stmt.setString(3, item.getDescription());
            if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
                stmt.setString(4, item.getImagePath());
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }
            stmt.setDouble(5, item.getStartingPrice());
            stmt.setString(6, item.getItemType().toUpperCase());
            stmt.setString(7, sellerId);

            stmt.setNull(8,  Types.VARCHAR);
            stmt.setNull(9,  Types.INTEGER);
            stmt.setNull(10, Types.VARCHAR);
            stmt.setNull(11, Types.VARCHAR);
            stmt.setNull(12, Types.VARCHAR);
            stmt.setNull(13, Types.INTEGER);

            if (item instanceof Electronics e) {
                stmt.setString(8, e.getBrand());
            } else if (item instanceof Art a) {
                stmt.setString(10, a.getArtist());
            } else if (item instanceof Vehicle v) {
                stmt.setString(8, v.getBrand());
            }

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println(">>> [DB] Đã lưu sản phẩm: " + item.getItemId());
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println(">>> [DB ERROR] Lỗi lưu sản phẩm " + item.getItemId() + ": " + e.getMessage());
            return false;
        }
    }

    public List<Item> findAll() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy danh sách sản phẩm: " + e.getMessage());
        }
        return items;
    }

    public Item findById(String itemId) {
        String sql = "SELECT * FROM items WHERE item_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapResultSetToItem(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm sản phẩm theo ID: " + e.getMessage());
        }
        return null;
    }

    public boolean update(Item item, String sellerId) {
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

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            stmt.setString(8, sellerId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật sản phẩm: " + e.getMessage());
            return false;
        }
    }

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

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public String findSellerIdByItemId(String itemId) {
        String sql = "SELECT seller_id FROM items WHERE item_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("seller_id");
        } catch (SQLException e) {
            System.err.println("Lỗi tìm seller_id của sản phẩm: " + e.getMessage());
        }
        return null;
    }

    public boolean delete(String itemId) {
        String sql = "DELETE FROM items WHERE item_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        String itemId        = rs.getString("item_id");
        String itemName      = rs.getString("item_name");
        String description   = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");
        String itemType      = rs.getString("item_type").toUpperCase();

        Item item = switch (itemType) {
            case "ELECTRONICS" -> new Electronics(itemId, itemName, description, startingPrice, rs.getString("brand"));
            case "ART"         -> new Art(itemId, itemName, description, startingPrice, rs.getString("artist"));
            case "VEHICLE"     -> new Vehicle(itemId, itemName, description, startingPrice, rs.getString("brand"));
            default -> throw new SQLException("Lỗi dữ liệu: Loại sản phẩm không hợp lệ: " + itemType);
        };

        try {
            item.setImagePath(rs.getString("image_path"));
        } catch (SQLException ignore) { }

        return item;
    }
}
