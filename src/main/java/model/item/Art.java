package model.item;

public class Art extends Item{
	private String author;
	
	// Constructor
    public Art(String itemId, String itemName, String description, double startingPrice, String author) {
        super(itemId, itemName, description, startingPrice);
        this.author = author;
    }
    
    @Override 
    public String getItemType()   { return "Art"; 	   }
    public String getAuthor() { return author; }
}
