package utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class SceneManager {
    private final Stage stage;

    public SceneManager(Stage stage) {
        this.stage = stage;
    }

    public void showLogin() {
        switchScene("/view/login.fxml", "Auction System - Login");
    }

    public void showDashboard() {
        switchScene("/view/dashboard.fxml", "UET Auction Dashboard");
    }

    private void switchScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                stage.setScene(scene);
            } else {
                stage.getScene().setRoot(root);
            }
            
            stage.setTitle(title);
            stage.centerOnScreen();
            stage.show();
            
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi: Không tìm thấy file giao diện tại " + fxmlPath);
        }
    }
}