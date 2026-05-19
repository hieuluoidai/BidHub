package model.item;

import model.core.Entity;
import java.io.Serializable;

/**
 * Abstract class cho mọi item trong hệ thống.
 *
 * Bổ sung field IMAGE_PATH — đường dẫn ảnh tương đối từ thư mục gốc project,
 * vd: "items/u-abc123.jpg". File thật nằm tại "./uploads/items/u-abc123.jpg".
 * NULL nếu sản phẩm không có ảnh.
 */
public abstract class Item extends Entity implements Serializable {
    private static final long serialVersionUID = 2L;   // bumped vì thêm field

    private String itemName;
    private String description;
    private double startingPrice;

    /** Đường dẫn ảnh tương đối, hoặc null nếu không có ảnh. */
    private String imagePath;

    public Item(String itemId, String itemName, String description, double startingPrice) {
        super(itemId);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    //Trả về itemType (Electronics, Art, Vehicle).
    public abstract String getItemType();

    // --- Getters & Setters ---
    public String getItemId() {
        return super.getId();
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public String getItemName() {
        return itemName;
    }

    public String getDescription() {
        return description;
    }

    public String getImagePath() {
        return imagePath;
    }

    // Update giá hiện tại (thường dùng khi có người Bid mới)
    public void setStartingPrice(double price) {
        this.startingPrice = price;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
