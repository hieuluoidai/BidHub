package model.item;

import model.core.Entity;

public abstract class Item extends Entity {
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
	
	// Abstract method
	public abstract String getItemType();
	
	// Getters
	public String getItemId() 		 { return super.getId(); }
	public double getStartingPrice() { return startingPrice; }
	public String getItemName() 	 { return itemName;		 }
	public String getDescription()  { return description;	 }

}
