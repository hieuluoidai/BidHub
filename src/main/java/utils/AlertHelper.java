package utils;

import javafx.application.Platform; // <-- THÊM IMPORT NÀY
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Helper tạo popup đẹp, thay thế cho Alert mặc định của JavaFX.
 * Đã tích hợp tính năng Thread-Safe (An toàn đa luồng).
 */
public class AlertHelper {

    public enum Type {
        INFO   ("#3B82F6", "ⓘ",  "Thông báo"),
        SUCCESS("#10B981", "✓",  "Thành công"),
        WARNING("#F59E0B", "⚠",  "Cảnh báo"),
        ERROR  ("#EF4444", "✕",  "Lỗi");

        final String color, icon, defaultTitle;
        Type(String color, String icon, String title) {
            this.color = color; this.icon = icon; this.defaultTitle = title;
        }
    }

    /** Hiện popup với title mặc định theo type */
    public static void show(Type type, String message) {
        show(type, type.defaultTitle, message);
    }

    /** Hiện popup với title tuỳ chỉnh */
    public static void show(Type type, String title, String message) {
        // TỰ ĐỘNG ĐIỀU HƯỚNG LUỒNG (TRÁNH LỖI ĐƠ APP)
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(type, title, message));
            return;
        }

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);

        // ── Icon tròn có màu ──
        Label iconLabel = new Label(type.icon);
        iconLabel.setStyle(
            "-fx-background-color: " + type.color + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 24px;" +
            "-fx-font-weight: bold;" +
            "-fx-min-width: 48px; -fx-min-height: 48px;" +
            "-fx-max-width: 48px; -fx-max-height: 48px;" +
            "-fx-background-radius: 50%;" +
            "-fx-alignment: center;"
        );

        // ── Title ──
        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #1E293B;"
        );

        // ── Nội dung ──
        Label messageLabel = new Label(message);
        messageLabel.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-text-fill: #475569;" +
            "-fx-wrap-text: true;"
        );
        messageLabel.setMaxWidth(320);
        messageLabel.setWrapText(true);

        VBox textBox = new VBox(6, titleLabel, messageLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);

        HBox contentBox = new HBox(16, iconLabel, textBox);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.setPadding(new Insets(20, 24, 16, 24));

        // ── Nút OK ──
        Button okButton = new Button("OK");
        okButton.setPrefWidth(90);
        okButton.setPrefHeight(34);
        okButton.setStyle(
            "-fx-background-color: " + type.color + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13px;" +
            "-fx-background-radius: 6px;" +
            "-fx-cursor: hand;"
        );
        okButton.setOnMouseEntered(e -> okButton.setStyle(okButton.getStyle()
            + "-fx-opacity: 0.9;"));
        okButton.setOnMouseExited(e -> okButton.setStyle(okButton.getStyle()
            .replace("-fx-opacity: 0.9;", "")));
        okButton.setOnAction(e -> stage.close());

        HBox buttonBox = new HBox(okButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 24, 20, 24));

        // ── Layout chính ──
        VBox root = new VBox(contentBox, buttonBox);
        root.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-color: #E2E8F0;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 12px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 4);"
        );

        // StackPane bọc ngoài để có thể có padding quanh popup
        StackPane wrapper = new StackPane(root);
        wrapper.setPadding(new Insets(10));
        wrapper.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(wrapper);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.setScene(scene);
        stage.showAndWait();
    }

    /**
     * Hiện popup xác nhận (Confirm) trả về boolean.
     */
    private static boolean confirmResult;
    public static boolean showConfirm(String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            // Chế độ này hơi khó trả về boolean từ background thread, 
            // nên ta ép buộc gọi trong luồng UI.
            System.err.println(">>> AlertHelper.showConfirm must be called from FX Thread!");
            return false;
        }

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);

        confirmResult = false;

        // ── Icon tròn màu Blue ──
        Label iconLabel = new Label("?");
        iconLabel.setStyle(
            "-fx-background-color: #3B82F6;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 24px;" +
            "-fx-font-weight: bold;" +
            "-fx-min-width: 48px; -fx-min-height: 48px;" +
            "-fx-background-radius: 50%;" +
            "-fx-alignment: center;"
        );

        // ── Title & Message ──
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569; -fx-wrap-text: true;");
        messageLabel.setMaxWidth(320);

        VBox textBox = new VBox(6, titleLabel, messageLabel);
        HBox contentBox = new HBox(16, iconLabel, textBox);
        contentBox.setPadding(new Insets(20, 24, 16, 24));

        // ── Nút Cancel ──
        Button btnCancel = new Button("Hủy");
        btnCancel.setPrefWidth(80);
        btnCancel.setStyle(
                "-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-weight: bold;"
                + " -fx-background-radius: 6px; -fx-cursor: hand;");
        btnCancel.setOnAction(e -> {
            confirmResult = false;
            stage.close();
        });

        // ── Nút Confirm ──
        Button btnConfirm = new Button("Xác nhận");
        btnConfirm.setPrefWidth(100);
        btnConfirm.setStyle(
                "-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-background-radius: 6px; -fx-cursor: hand;");
        btnConfirm.setOnAction(e -> {
            confirmResult = true;
            stage.close();
        });

        HBox buttonBox = new HBox(12, btnCancel, btnConfirm);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(0, 24, 20, 24));

        VBox root = new VBox(contentBox, buttonBox);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12px;"
                + " -fx-border-color: #E2E8F0; -fx-border-width: 1px; -fx-border-radius: 12px;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 4);");

        StackPane wrapper = new StackPane(root);
        wrapper.setPadding(new Insets(10));
        wrapper.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(wrapper);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.setScene(scene);
        stage.showAndWait();

        return confirmResult;
    }
}