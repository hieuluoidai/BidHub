package model.item;

public class Vehicle extends Item {
    private String brand;
    
    // Constructor
    public Vehicle(String itemId, String itemName, String description, double startingPrice, String brand) {
        super(itemId, itemName, description, startingPrice);
        this.brand = brand;
    }

    @Override
    public String getItemType() { return "Vehicle"; }
    public String getBrand() { return this.brand; }
}
