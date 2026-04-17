package model.item;

public class Electronics extends Item {
	private String brand;
	
	// Constructor
	public Electronics(String itemId, String itemName, String description, double startingPrice, String brand) {
		super(itemId, itemName, description, startingPrice);
		this.brand = brand;
	}
	
	@Override
	public String getItemType()		{ return "Electronics";   	  }
	public String getBrand() {	return this.brand;}
}
