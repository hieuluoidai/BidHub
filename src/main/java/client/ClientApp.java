package com.example.auction.client;

import javafx.application.Application;
import javafx.stage.Stage;

public class ClientApp extends Application {
    private final AppState appState = new AppState();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(780);
        primaryStage.setOnCloseRequest(event -> {
            try {
                appState.close();
            } catch (Exception ignored) {
            }
        });

        SceneManager sceneManager = new SceneManager(primaryStage, appState);
        sceneManager.showLogin();
    }

    @Override
    public void stop() throws Exception {
        appState.close();
        super.stop();
    }
}
