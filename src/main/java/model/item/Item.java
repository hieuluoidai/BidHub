package model.item;

import model.core.Entity;
import java.io.Serializable;

/**
 * Abstract class cho mọi item trong hệ thống.
 */
public abstract class Item extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String itemName;
    private String description;
    private double startingPrice;

    public Item(String itemId, String itemName, String description, double startingPrice) {
        super(itemId);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    //Trả về itemType (Electronics, Art, Vehicle).
    public abstract String getItemType();

    // --- Getters & Setters ---
    public String getItemId()        { return super.getId(); }
    public double getStartingPrice() { return startingPrice; }
    public String getItemName()      { return itemName; }
    public String getDescription()   { return description; }
    
    // Update giá hiện tại (thường dùng khi có người Bid mới)
    public void setStartingPrice(double price) { this.startingPrice = price; }
}