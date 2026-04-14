package model.item;

class Vehicle extends Item {
    private String engineType;
    
    // Constructor
    public Vehicle(String itemId, String itemName, String description, double startingPrice, String engineType) {
        super(itemId, itemName, description, startingPrice);
        this.engineType = engineType;
    }

    @Override
    public String getItemType() { return "Vehicle"; }
    public String getEngineType() { return this.engineType; }
}
