package model.item;

public class Art extends Item{
	private String artistName;
	
	// Constructor
    public Art(String itemId, String itemName, String description, double startingPrice, String artistName) {
        super(itemId, itemName, description, startingPrice);
        this.artistName = artistName;
    }
    
    @Override 
    public String getItemType()   { return "Art"; 	   }
    public String getArtistName() { return artistName; }
}
