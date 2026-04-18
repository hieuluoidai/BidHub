package model.item;

import model.core.Entity;
import java.io.Serializable; // Bắt buộc phải có để truyền qua mạng 

/**
 * Lớp Item đại diện cho sản phẩm đấu giá.
 * Đây là lớp Abstract để các loại sản phẩm cụ thể kế thừa[cite: 112, 122].
 */
public abstract class Item extends Entity implements Serializable {
    // serialVersionUID giúp đồng bộ hóa giữa Server và Client khi truyền Object [cite: 126]
    private static final long serialVersionUID = 1L;

    private String itemName;
    private String description;
    private double startingPrice;
    
    // Constructor
    public Item(String itemId, String itemName, String description, double startingPrice) {
        super(itemId);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
    }
    
    /**
     * Phương thức trừu tượng để xác định loại sản phẩm (Electronics, Art, Vehicle)[cite: 113, 121, 122].
     */
    public abstract String getItemType();
    
    // Getters
    public String getItemId()        { return super.getId(); }
    public double getStartingPrice() { return startingPrice; }
    public String getItemName()      { return itemName;      }
    public String getDescription()   { return description;   }

}