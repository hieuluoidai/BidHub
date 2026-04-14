package com.example.auction.client;

import javafx.application.Application;

public final class ClientLauncher {
    private ClientLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(ClientApp.class, args);
    }
}
