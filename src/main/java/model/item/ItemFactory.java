package model.item;

/**
 * Factory khởi tạo đối tượng Item (Factory Method).
 */
public class ItemFactory {
    //Tạo thực thể sản phẩm dựa trên loại được yêu cầu.
    public static Item createItem(String type, String id, String name, String desc, double price, String info) {
        switch (type.toUpperCase()) {
            case "ELECTRONICS":
                return new Electronics(id, name, desc, price, info);
            case "ART":
                return new Art(id, name, desc, price, info);
            case "VEHICLE":
                return new Vehicle(id, name, desc, price, info);
            default:
                throw new IllegalArgumentException("Loại sản phẩm không hợp lệ: " + type);
        }
    }
}