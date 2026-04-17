package model.item;

public class ItemFactory {
    public static Item createItem(String type, String id, String name, String desc, double price, String origin) {
        switch (type.toUpperCase()) {
            case "ELECTRONICS":
                return new Electronics(id, name, desc, price, origin);
            case "ART":
                return new Art(id, name, desc, price, origin);
            case "VEHICLE":
                return new Vehicle(id, name, desc, price, origin);
            default:
                throw new IllegalArgumentException("Unknown item type: " + type);
        }
    }
}