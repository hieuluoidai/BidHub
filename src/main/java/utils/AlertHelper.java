package utils;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

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

    public static void show(Type type, String message) {
        show(type, type.defaultTitle, message);
    }

    public static void show(Type type, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(type, title, message));
            return;
        }
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);

        Button okBtn = makeButton("OK", type.color, true, 80);
        okBtn.setOnAction(e -> stage.close());

        HBox card = buildCard(type, title, message, null, okBtn);
        showAnimated(stage, card, false);
    }

    private static boolean confirmResult;

    public static boolean showConfirm(String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            System.err.println(">>> AlertHelper.showConfirm must be called from FX Thread!");
            return false;
        }
        confirmResult = false;

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);

        Button btnCancel  = makeButton("Hủy",      "#94A3B8", false, 80);
        Button btnConfirm = makeButton("Xác nhận", "#3B82F6", true,  100);
        btnCancel .setOnAction(e -> {
            confirmResult = false;
            stage.close();
        });
        btnConfirm.setOnAction(e -> {
            confirmResult = true;
            stage.close();
        });

        HBox card = buildCard(Type.INFO, title, message, btnCancel, btnConfirm);
        showAnimated(stage, card, true);
        return confirmResult;
    }

    // ── Core layout builder ──────────────────────────────────────────

    private static HBox buildCard(Type type, String title, String message,
                                  Button leftBtn, Button rightBtn) {
        // Left accent bar (5px solid color)
        VBox accent = new VBox();
        accent.setMinWidth(5);
        accent.setPrefWidth(5);
        accent.setMaxWidth(5);
        accent.setStyle("-fx-background-color: " + type.color + ";");

        // Icon circle (small, inline)
        Label iconLbl = new Label(type.icon);
        iconLbl.setStyle(
                "-fx-background-color: " + type.color + ";" +
                "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;" +
                "-fx-min-width: 28px; -fx-min-height: 28px;" +
                "-fx-max-width: 28px; -fx-max-height: 28px;" +
                "-fx-background-radius: 50%; -fx-alignment: center;"
        );

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");

        HBox titleRow = new HBox(10, iconLbl, titleLbl);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label msgLbl = new Label(message);
        msgLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748B; -fx-wrap-text: true;");
        msgLbl.setMaxWidth(310);
        msgLbl.setWrapText(true);

        VBox textArea = new VBox(8, titleRow, msgLbl);
        textArea.setPadding(new Insets(20, 22, 16, 18));

        // Button row
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox btnRow = leftBtn != null
                ? new HBox(10, spacer, leftBtn, rightBtn)
                : new HBox(spacer, rightBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(10, 18, 14, 18));

        // White right side
        VBox rightSide = new VBox(textArea, btnRow);
        rightSide.setStyle("-fx-background-color: white;");
        HBox.setHgrow(rightSide, Priority.ALWAYS);

        // Card = accent | rightSide
        HBox card = new HBox(accent, rightSide);
        card.setPrefWidth(390);

        // Clip with rounded corners
        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        card.setClip(clip);

        return card;
    }

    private static void showAnimated(Stage stage, HBox card, boolean blocking) {
        StackPane shadowPane = new StackPane(card);
        shadowPane.setStyle(
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 28, 0, 0, 6);");

        StackPane sceneRoot = new StackPane(shadowPane);
        sceneRoot.setPadding(new Insets(14));
        sceneRoot.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(sceneRoot);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.setScene(scene);

        card.setOpacity(0);
        card.setTranslateY(10);
        FadeTransition ft = new FadeTransition(Duration.millis(220), card);
        ft.setFromValue(0); ft.setToValue(1);
        TranslateTransition tt = new TranslateTransition(Duration.millis(220), card);
        tt.setFromY(10); tt.setToY(0);
        ParallelTransition anim = new ParallelTransition(ft, tt);

        if (blocking) {
            stage.setOnShown(e -> anim.play());
            stage.showAndWait();
        } else {
            stage.show();
            anim.play();
        }
    }

    private static Button makeButton(String text, String color, boolean filled, double width) {
        Button btn = new Button(text);
        btn.setPrefWidth(width);
        btn.setPrefHeight(34);
        String bg = filled
                ? "-fx-background-color: " + color + "; -fx-text-fill: white;"
                : "-fx-background-color: #F1F5F9; -fx-text-fill: #475569;";
        btn.setStyle(bg + " -fx-font-weight: bold; -fx-font-size: 13px;"
                + " -fx-background-radius: 6px; -fx-cursor: hand;");
        return btn;
    }
}
