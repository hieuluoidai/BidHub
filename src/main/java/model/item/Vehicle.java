package model.item;

/**
 * Class cho item dạng Vehicle
 */
public class Vehicle extends Item {
	private static final long serialVersionUID = 1L;
    private String brand;

    public Vehicle(String itemId, String itemName, String description, double startingPrice, String brand) {
        super(itemId, itemName, description, startingPrice);
        this.brand = brand;
    }

    @Override
    public String getItemType() { return "Vehicle"; }
    
    public String getBrand() { return brand; }
}