package model.core;

public abstract class Entity {
    private String id;
    
    // Constructor
    public Entity(String id) {
        this.id = id;
    }
    
    // Getters & Setters
    public String getId() 			{ return id;    }
    public void setId(String id)    { this.id = id; }
}