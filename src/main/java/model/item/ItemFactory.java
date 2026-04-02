package model.item;

public class ItemFactory {
    public static Item createItem(String type, String id, String name, String desc, double price, String extraInfo) {
        switch (type.toUpperCase()) {
            case "ELECTRONICS":
                return new Electronics(id, name, desc, price, Integer.parseInt(extraInfo));
            case "ART":
                return new Art(id, name, desc, price, extraInfo);
            case "VEHICLE":
                return new Vehicle(id, name, desc, price, extraInfo);
            default:
                throw new IllegalArgumentException("Unknown item type: " + type);
        }
    }
}