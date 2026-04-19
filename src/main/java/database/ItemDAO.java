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
        // Cấu trúc bảng items gom chung tất cả các loại hàng. 
        // Cột nào không dùng (vd: đồ điện tử thì không có artist) sẽ để giá trị NULL.
        String sql = "INSERT INTO items (item_id, item_name, description, starting_price, item_type, seller_id, brand, artist) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Lưu các thông tin cơ bản (loại hàng nào cũng có)
            stmt.setString(1, item.getItemId());
            stmt.setString(2, item.getItemName());
            stmt.setString(3, item.getDescription());
            stmt.setDouble(4, item.getStartingPrice());
            stmt.setString(5, item.getItemType());
            stmt.setString(6, sellerId);

            // Xử lý đa hình (Polymorphism) để lưu thông tin đặc thù
            if (item instanceof Electronics e) {
                stmt.setString(7, e.getBrand());      // Có hãng sản xuất
                stmt.setNull(8, Types.VARCHAR);       // Không có tác giả
            } else if (item instanceof Art a) {
                stmt.setNull(7, Types.VARCHAR);       // Không có hãng sản xuất
                stmt.setString(8, a.getAuthor());     // Có tác giả
            } else if (item instanceof Vehicle v) {
                stmt.setString(7, v.getBrand());      // Có hãng xe
                stmt.setNull(8, Types.VARCHAR);       // Không có tác giả
            } else {
                // Đề phòng trường hợp loại hàng rác
                stmt.setNull(7, Types.VARCHAR);
                stmt.setNull(8, Types.VARCHAR);
            }

            stmt.executeUpdate(); // Chạy lệnh INSERT
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
     * Xóa một sản phẩm khỏi DB. 
     * (Thường dùng khi chủ phòng muốn hủy đấu giá).
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
        
        // Dùng item_type (được lưu bằng chữ in hoa trong DB) để quyết định sẽ gọi Constructor nào
        String itemType      = rs.getString("item_type").toUpperCase(); 

        return switch (itemType) {
            // Nếu là đồ điện tử, lấy thêm cột 'brand'
            case "ELECTRONICS" -> new Electronics(itemId, itemName, description, startingPrice, rs.getString("brand"));
            // Nếu là nghệ thuật, lấy cột 'artist'
            case "ART"         -> new Art(itemId, itemName, description, startingPrice, rs.getString("artist"));
            // Nếu là xe cộ, lấy cột 'brand'
            case "VEHICLE"     -> new Vehicle(itemId, itemName, description, startingPrice, rs.getString("brand"));
            
            default -> throw new SQLException("Lỗi dữ liệu: Loại sản phẩm không hợp lệ: " + itemType);
        };
    }
}