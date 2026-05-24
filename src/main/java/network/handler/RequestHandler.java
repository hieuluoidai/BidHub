package network.handler;

import network.ClientHandler;

/**
 * Interface for handling client requests.
 */
public interface RequestHandler {
    /**
     * Handles a specific command from the client.
     * 
     * @param context The ClientHandler context for sending responses and accessing state.
     * @param msg The full message string from the client.
     */
    void handle(ClientHandler context, String msg);
}
