package utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.image.Image;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Bộ điều phối chuyển cảnh, quản lý duy nhất một Stage chính.
 */
public class SceneManager {
    private final Stage stage;
    private double currentScale = 1.0;

    public SceneManager(Stage stage) {
        this.stage = stage;
        setAppIcon(stage);
    }

    /**
     * Gán icon BidHub cho bất kỳ cửa sổ (Stage) nào.
     */
    public static void setAppIcon(Stage stage) {
        if (stage == null) return;
        try {
            stage.getIcons().add(new Image(SceneManager.class.getResourceAsStream("/Images/bid-hub-logo.png")));
        } catch (Exception e) {
            System.err.println(">>> [WARN] Không thể nạp ứng dụng icon: " + e.getMessage());
        }
    }

    public void showLogin() {
        stage.setMaximized(false);
        switchScene("/view/login.fxml", "BidHub - Đăng nhập");
    }

    public void showRegister() {
        stage.setMaximized(false);
        switchScene("/view/register.fxml", "BidHub - Đăng ký tài khoản");
    }

    public void showDashboard() {
        switchScene("/view/dashboard.fxml", "BidHub - Home");
        stage.setMaximized(true);
    }
    
    public void showAdminDashboard() {
        switchScene("/view/admin_dashboard.fxml", "BidHub - Admin Dashboard");
        stage.setMaximized(true);
    }

    /**
     * Thực hiện thay đổi nội dung màn hình chính.
     */
    private void switchScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            
            // Lấy kích thước thiết kế ban đầu của Scene (hoặc dùng mặc định nếu không set)
            double targetWidth = root.prefWidth(-1);
            double targetHeight = root.prefHeight(-1);
            if (targetWidth <= 0) targetWidth = 1450.0;
            if (targetHeight <= 0) targetHeight = 760.0;
            
            // Ép buộc kích thước của giao diện bên trong
            if (root instanceof javafx.scene.layout.Region region) {
                region.setPrefSize(targetWidth, targetHeight);
                region.setMinSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
                region.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
            }

            // Tạo Group để chứa layout gốc, Group sẽ không bị resize layout mà chỉ thay đổi scale
            javafx.scene.Group group = new javafx.scene.Group(root);
            
            // StackPane đóng vai trò là Viewport, lắng nghe sự thay đổi kích thước của Cửa sổ (Stage)
            javafx.scene.layout.StackPane wrapper = new javafx.scene.layout.StackPane(group);
            wrapper.setStyle("-fx-background-color: #1E293B;"); // Thêm màu nền cho phần viền (letterbox)

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(wrapper, targetWidth, targetHeight);
                String cssUrl = getClass().getResource("/view/style.css").toExternalForm();
                scene.getStylesheets().add(cssUrl);
                stage.setScene(scene);
            } else {
                scene.setRoot(wrapper);
            }
            
            // Xử lý Scale (Giữ nguyên Aspect Ratio - Letterboxing)
            final double baseWidth = targetWidth;
            final double baseHeight = targetHeight;
            
            javafx.beans.value.ChangeListener<Number> sizeListener = (obs, oldVal, newVal) -> {
                double scaleX = wrapper.getWidth() / baseWidth;
                double scaleY = wrapper.getHeight() / baseHeight;
                double scale = Math.min(scaleX, scaleY);
                
                // Tránh scale quá lớn hoặc âm/bằng 0 khi mới khởi tạo
                if (scale > 0 && scale < 10) {
                    group.setScaleX(scale);
                    group.setScaleY(scale);
                    currentScale = scale;
                }
            };
            
            wrapper.widthProperty().addListener(sizeListener);
            wrapper.heightProperty().addListener(sizeListener);
            
            stage.setTitle(title);
            // Kích thước của Stage sẽ dựa trên Scene, hoặc do người dùng kéo thả, auto-fullscreen
            stage.show();
            
            // Kích hoạt tính toán scale ngay lập tức
            javafx.application.Platform.runLater(() -> {
                double w = wrapper.getWidth();
                double h = wrapper.getHeight();
                if (w > 0 && h > 0) {
                    double scaleX = w / baseWidth;
                    double scaleY = h / baseHeight;
                    double scale = Math.min(scaleX, scaleY);
                    group.setScaleX(scale);
                    group.setScaleY(scale);
                    currentScale = scale;
                }
                stage.centerOnScreen();
            });
            
        } catch (IOException e) {
            System.err.println("Lỗi nghiêm trọng: Không thể tải giao diện tại " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Áp dụng Global Scaling cho một Stage bất kỳ (thường là Pop-up).
     * Đồng bộ tỷ lệ với cửa sổ chính và căn giữa so với cửa sổ chính.
     */
    public void setupModalStage(Stage modalStage, Parent root, String title) {
        if (modalStage == null || root == null) return;

        setAppIcon(modalStage);
        modalStage.setTitle(title);

        // Lấy kích thước thiết kế gốc
        double targetWidth = root.prefWidth(-1);
        double targetHeight = root.prefHeight(-1);
        if (targetWidth <= 0) targetWidth = 1120.0;
        if (targetHeight <= 0) targetHeight = 850.0;

        if (root instanceof javafx.scene.layout.Region region) {
            region.setPrefSize(targetWidth, targetHeight);
            region.setMinSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
            region.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
        }

        // Bọc vào Group và StackPane để scale
        javafx.scene.Group group = new javafx.scene.Group(root);
        group.setScaleX(currentScale);
        group.setScaleY(currentScale);

        javafx.scene.layout.StackPane wrapper = new javafx.scene.layout.StackPane(group);
        wrapper.setStyle("-fx-background-color: transparent;");

        // Scene mới có kích thước đã scale
        Scene scene = new Scene(wrapper, targetWidth * currentScale, targetHeight * currentScale);
        String cssUrl = getClass().getResource("/view/style.css").toExternalForm();
        scene.getStylesheets().add(cssUrl);
        modalStage.setScene(scene);

        if (modalStage.getOwner() == null) {
            modalStage.initOwner(this.stage);
        }

        // Căn giữa so với main stage
        modalStage.setOnShowing(e -> {
            double mainX = this.stage.getX();
            double mainY = this.stage.getY();
            double mainW = this.stage.getWidth();
            double mainH = this.stage.getHeight();

            double modalW = modalStage.getWidth();
            double modalH = modalStage.getHeight();

            if (!Double.isNaN(mainW) && mainW > 0) {
                modalStage.setX(mainX + (mainW - modalW) / 2);
                modalStage.setY(mainY + (mainH - modalH) / 2);
            }
        });
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
            modalStage.initModality(Modality.APPLICATION_MODAL);
            setupModalStage(modalStage, root, title);
            modalStage.show();

        } catch (IOException e) {
            System.err.println("Lỗi mở Modal: " + fxmlPath);
            e.printStackTrace();
        }
    }
}
