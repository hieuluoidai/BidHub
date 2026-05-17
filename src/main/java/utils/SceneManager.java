package utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Bộ điều phối chuyển cảnh, quản lý duy nhất một Stage chính.
 */
public class SceneManager {
    private final Stage stage;

    public SceneManager(Stage stage) {
        this.stage = stage;
    }

    public void showLogin() {
        switchScene("/view/login.fxml", "BidHub - Đăng nhập");
    }

    public void showRegister() {
        switchScene("/view/register.fxml", "BidHub - Đăng ký tài khoản");
    }

    public void showDashboard() {
        switchScene("/view/dashboard.fxml", "BidHub - Bảng điều khiển");
    }
    
    public void showAdminDashboard() {
        switchScene("/view/admin_dashboard.fxml", "BidHub - Admin Dashboard");
    }

    /**
     * Thực hiện thay đổi nội dung màn hình chính.
     */
    private void switchScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                String cssUrl = getClass().getResource("/view/style.css").toExternalForm();
                scene.getStylesheets().add(cssUrl);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            
            stage.setTitle(title);
            stage.sizeToScene();
            stage.centerOnScreen();
            stage.show();
            
        } catch (IOException e) {
            System.err.println("Lỗi nghiêm trọng: Không thể tải giao diện tại " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Mở một cửa sổ Pop-up (Modal) mới.
     * Tự động giới hạn kích thước theo màn hình để tránh tràn UI (Overflow).
     */
    public void showModal(String fxmlPath, String title, Consumer<Object> controllerConsumer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            
            if (controllerConsumer != null) {
                controllerConsumer.accept(loader.getController());
            }

            Stage modalStage = new Stage();
            modalStage.setTitle(title);
            modalStage.initModality(Modality.APPLICATION_MODAL);
            
            Scene scene = new Scene(root);
            String cssUrl = getClass().getResource("/view/style.css").toExternalForm();
            scene.getStylesheets().add(cssUrl);
            modalStage.setScene(scene);

            // Tự động điều chỉnh kích thước ban đầu để không vượt quá màn hình
            // nhưng vẫn cho phép user Maximize cửa sổ nếu muốn.
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double initialWidth = Math.min(1120, screenBounds.getWidth() * 0.95);
            double initialHeight = Math.min(850, screenBounds.getHeight() * 0.95);
            
            modalStage.setWidth(initialWidth);
            modalStage.setHeight(initialHeight);

            modalStage.show();
            modalStage.centerOnScreen();

        } catch (IOException e) {
            System.err.println("Lỗi mở Modal: " + fxmlPath);
            e.printStackTrace();
        }
    }
}
