package model.core;

import java.io.Serializable;

/**
 * Abstract class cho mọi thực thể có ID trong hệ thống.
 */
public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;

    public Entity(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    
    public void setId(String id) { this.id = id; }
}