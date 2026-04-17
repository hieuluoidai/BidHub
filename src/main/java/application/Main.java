package application;

/*
import model.item.*;
import model.user.*;
import model.auction.*;
import model.manager.AuctionManager;
import java.time.LocalDateTime;
*/
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import model.manager.AppState;
import utils.SceneManager;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            Image logo = new Image(getClass().getResourceAsStream("/images/logo-uet.jpg"));
            primaryStage.getIcons().add(logo);

            SceneManager sceneManager = new SceneManager(primaryStage);

            // 3. (Tùy chọn) Thử kết nối Server ở luồng phụ để không làm đơ giao diện
            /*
            new Thread(() -> {
                try {
                    // Thử kết nối đến server đồng nghiệp (VD: localhost, cổng 8080)
                    // AppState.getInstance().getClient().connect("localhost", 8080);
                } catch (Exception e) {
                    System.err.println("Chưa bật Server, đang chạy chế độ Offline.");
                }
            }).start();
            */

            // 4. Gọi SceneManager hiển thị màn hình Login
            sceneManager.showLogin();

            primaryStage.setTitle("UET Auction System - " + AppState.getInstance().getCurrentUser() == null ? "Guest" : "Logged In");
            
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("Closing app... Goodbye!");
            });

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
    		launch(args);
    }
}