package database;

import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.Vehicle;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    private final Connection conn;

    public ItemDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    public boolean save(Item item, String sellerId) {
        String sql = "INSERT INTO items (item_id, item_name, description, starting_price, item_type, seller_id, brand, artist) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getItemId());
            stmt.setString(2, item.getItemName());
            stmt.setString(3, item.getDescription());
            stmt.setDouble(4, item.getStartingPrice());
            stmt.setString(5, item.getItemType());
            stmt.setString(6, sellerId);

            if (item instanceof Electronics e) {
                stmt.setString(7, e.getBrand());
                stmt.setNull(8, Types.VARCHAR);
            } else if (item instanceof Art a) {
                stmt.setNull(7, Types.VARCHAR);
                stmt.setString(8, a.getAuthor());
            } else if (item instanceof Vehicle v) {
                stmt.setString(7, v.getBrand());
                stmt.setNull(8, Types.VARCHAR);
            } else {
                stmt.setNull(7, Types.VARCHAR);
                stmt.setNull(8, Types.VARCHAR);
            }

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi save item: " + e.getMessage());
            return false;
        }
    }

    public List<Item> findAll() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi findAll items: " + e.getMessage());
        }
        return items;
    }

    public Item findById(String itemId) {
        String sql = "SELECT * FROM items WHERE item_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapResultSetToItem(rs);
        } catch (SQLException e) {
            System.err.println("Lỗi findById item: " + e.getMessage());
        }
        return null;
    }

    public boolean delete(String itemId) {
        String sql = "DELETE FROM items WHERE item_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi delete item: " + e.getMessage());
            return false;
        }
    }

    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        String itemId        = rs.getString("item_id");
        String itemName      = rs.getString("item_name");
        String description   = rs.getString("description");
        double startingPrice = rs.getDouble("starting_price");
        String itemType      = rs.getString("item_type");

        return switch (itemType) {
            case "ELECTRONICS" -> new Electronics(itemId, itemName, description, startingPrice, rs.getString("brand"));
            case "ART"         -> new Art(itemId, itemName, description, startingPrice, rs.getString("artist"));
            case "VEHICLE"     -> new Vehicle(itemId, itemName, description, startingPrice, rs.getString("brand"));
            default -> throw new SQLException("item_type không hợp lệ: " + itemType);
        };
    }
}