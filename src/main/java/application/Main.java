package application;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import model.manager.AppState;
import utils.SceneManager;

/**
 * Lớp khởi chạy chính của hệ thống
 */
public class Main extends Application {
    
    @Override
    public void start(@SuppressWarnings("exports") Stage primaryStage) {
        try {
            // 1. Thiết lập các thuộc tính cơ bản cho cửa sổ chính (Main Stage)
            primaryStage.setTitle("UET Auction System");
            
            // Xử lý nạp icon ứng dụng (bọc try-catch để tránh crash nếu thiếu tài nguyên)
            try {
                Image logo = new Image(getClass().getResourceAsStream("/Images/logo-uet.jpg"));
                primaryStage.getIcons().add(logo);
            } catch (Exception e) {
                System.err.println(">>> Cảnh báo: Không tìm thấy tài nguyên logo, sử dụng icon mặc định.");
            }

            // 2. Khởi tạo bộ quản lý điều hướng màn hình (SceneManager)
            SceneManager sceneManager = new SceneManager(primaryStage);

            // 3. Đăng ký SceneManager vào Singleton AppState
            // Mục đích: Cung cấp ngữ cảnh (context) chuyển trang toàn cục cho các Controller khác.
            AppState.getInstance().setSceneManager(sceneManager);

            // 4. Khởi chạy màn hình Login làm giao diện mặc định
            sceneManager.showLogin();

            // 5. Xử lý sự kiện đóng ứng dụng
            // Đảm bảo các luồng (thread) mạng được giải phóng an toàn trước khi tắt.
            primaryStage.setOnCloseRequest(event -> {
                System.out.println(">>> Đang giải phóng tài nguyên và đóng ứng dụng...");
                
                // Đóng kết nối Socket/Client nếu đang tồn tại
                if (AppState.getInstance().getClient() != null) {
                    AppState.getInstance().getClient().close();
                }
                
                // Kết thúc triệt để mọi tiến trình đang chạy ngầm
                System.exit(0); 
            });

        } catch(Exception e) {
            System.err.println(">>> Lỗi Runtime trong quá trình khởi tạo ứng dụng:");
            e.printStackTrace();
        }
    }

    /**
     * Phương thức main mặc định.
     */
    public static void main(String[] args) {
        launch(args);
    }
}