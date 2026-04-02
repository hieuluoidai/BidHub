package model.item;

public class Electronics extends Item {
	private int warrantyMonths;
	
	// Constructor
	public Electronics(String itemId, String itemName, String description, double startingPrice, int warrantyMonths) {
		super(itemId, itemName, description, startingPrice);
		this.warrantyMonths = warrantyMonths;
	}
	
	@Override
	public String getItemType() {
		return "Electronics";
	}
	

}
