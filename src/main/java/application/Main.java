package application;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import model.manager.AppState;
import utils.SceneManager;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Cấu hình giao diện cơ bản (Title và Icon) trước khi hiển thị
            primaryStage.setTitle("UET Auction System");
            
            // Đảm bảo thư mục Images nằm trong src/main/resources hoặc đúng đường dẫn build
            try {
                Image logo = new Image(getClass().getResourceAsStream("/Images/logo-uet.jpg"));
                primaryStage.getIcons().add(logo);
            } catch (Exception e) {
                System.err.println(">>> Không tìm thấy ảnh logo, bỏ qua bước thiết lập icon.");
            }

            // 2. Khởi tạo SceneManager để quản lý chuyển cảnh
            SceneManager sceneManager = new SceneManager(primaryStage);

            // 3. Đăng ký SceneManager vào AppState để các Controller khác có thể sử dụng
            AppState.getInstance().setSceneManager(sceneManager);

            // 4. Hiển thị màn hình Login đầu tiên
            // Lưu ý: Logic kết nối AppState.getInstance().getClient().connect(...) 
            // hiện đã nằm trong LoginController nên không cần để ở đây nữa.
            sceneManager.showLogin();

            // 5. Xử lý sự kiện đóng cửa sổ chính (X dính trên góc màn hình)
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("Closing app... Goodbye!");
                // Đảm bảo ngắt kết nối an toàn và giải phóng tài nguyên
                if (AppState.getInstance().getClient() != null) {
                    AppState.getInstance().getClient().close();
                }
                System.exit(0); // Tắt hoàn toàn mọi tiến trình chạy ngầm
            });

        } catch(Exception e) {
            System.err.println(">>> Lỗi khởi động ứng dụng:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Hàm main luôn là static, không cần sửa gì thêm ở đây
        launch(args);
    }
}