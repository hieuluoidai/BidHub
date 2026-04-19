package model.core;

/**
 * Interface cho các Listener
 */
public interface Observer {
    // Update thông tin khi Subject có thay đổi.
    void update(String message);
}