package model.item;

/**
 * Class cho item dạng Art
 */
public class Art extends Item {
	private static final long serialVersionUID = 1L;
    private String author;

    public Art(String itemId, String itemName, String description, double startingPrice, String author) {
        super(itemId, itemName, description, startingPrice);
        this.author = author;
    }

    @Override 
    public String getItemType() { return "Art"; }
    
    public String getAuthor() { return author; }
}