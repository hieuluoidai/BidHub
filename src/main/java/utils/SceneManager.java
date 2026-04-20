package utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

/**
 * Bộ điều phối chuyển cảnh, quản lý duy nhất một Stage chính.
 */
public class SceneManager {
    private final Stage stage;

    public SceneManager(Stage stage) {
        this.stage = stage;
    }

    public void showLogin() {
        switchScene("/view/login.fxml", "Hệ thống Đấu giá - Đăng nhập");
    }

    public void showDashboard() {
        switchScene("/view/dashboard.fxml", "Bảng điều khiển Đấu giá");
    }

    /**
     * Thực hiện thay đổi nội dung màn hình. 
     * Tận dụng lại Scene đã có để tối ưu tài nguyên.
     */
    private void switchScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            
            Scene scene = stage.getScene();
            if (scene == null) {
                // Khởi tạo Scene lần đầu tiên
                scene = new Scene(root);
                
                // Thêm Css
                String cssUrl = getClass().getResource("/view/style.css").toExternalForm();
                scene.getStylesheets().add(cssUrl);
                // ---------------------------------
                
                stage.setScene(scene);
            } else {
                // Chỉ thay đổi nội dung bên trong, giữ nguyên cửa sổ và CSS đã nạp
                scene.setRoot(root);
            }
            
            stage.setTitle(title);
            stage.centerOnScreen();
            stage.show();
            
        } catch (IOException e) {
            System.err.println("Lỗi nghiêm trọng: Không thể tải giao diện tại " + fxmlPath);
            e.printStackTrace();
        }
    }
}