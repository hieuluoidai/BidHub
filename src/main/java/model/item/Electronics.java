package model.item;

/**
 * Class cho item dạng Electronic
 */
public class Electronics extends Item {
    private static final long serialVersionUID = 1L;
    private String brand;

    public Electronics(String itemId, String itemName, String description, double startingPrice, String brand) {
        super(itemId, itemName, description, startingPrice);
        this.brand = brand;
    }

    @Override
    public String getItemType() {
        return "Electronics";
    }

    public String getBrand() {
        return brand;
    }
}
