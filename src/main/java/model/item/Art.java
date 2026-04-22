package model.item;

/**
 * Class cho item dạng Art
 */
public class Art extends Item {
    private static final long serialVersionUID = 1L;
    private String artist;

    public Art(String itemId, String itemName, String description, double startingPrice, String artist) {
        super(itemId, itemName, description, startingPrice);
        this.artist = artist;
    }

    @Override
    public String getItemType() { return "Art"; }

    public String getArtist() { return artist; }
}