package com.example.auction.client;

import com.example.auction.client.controller.LoginController;
import com.example.auction.client.controller.MainController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneManager {
  private static final String LOGIN_VIEW = "/com/example/auction/client/view/login_view.fxml";
  private static final String MAIN_VIEW = "/com/example/auction/client/view/main-view.fxml";

  private final Stage stage;
  private final AppState appState;

  public SceneManager(Stage stage, AppState appState) {
    this.stage = stage;
    this.appState = appState;
  }

  public void showLogin() {
    try {
        FXMLLoader = new FXMLLoader(SceneManager.class.getResource(LOGIN_VIEW));
        Parent root = loader.load();
        LoginController controller = loader.getController();
        controller.setAppState(appState);
        controller.setSceneManager(this);

        Scene scene = new Scene(root, 520, 480);
        stage.setTitle("Auction Client - Login");
        stage.setScene(scene);
        stage.show();
    } catch (Exception exception) {
      throw new IllegalStateException("Khong the mo login view", exception);
    }
  }

  public void showMain() {
    try {
        FXMLLoader loader = new FXMLLoader(SceneManager.class.getResource(MAIN_VIEW));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.setAppState(appState);
        controller.setSceneManager(this);
        controller.onViewReady();

        Scene scene = new Scene(root, 1460, 900);
        stage.setTitle("Auction Dashboard - JavaFX + MySQL");
        stage.setScene(scene);
        stage.show();
    } catch (Exception exception) {
      throw new IllegalStateException("Khong the mo main view", exception);
    }
  }
}