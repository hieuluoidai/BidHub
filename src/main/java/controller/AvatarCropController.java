package controller;

import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class AvatarCropController {

    private static final double DISPLAY_W   = 450;
    private static final double DISPLAY_H   = 330;
    private static final double CROP_SIZE   = 220;
    private static final double CROP_LEFT   = (DISPLAY_W - CROP_SIZE) / 2;
    private static final double CROP_TOP    = (DISPLAY_H - CROP_SIZE) / 2;
    private static final int    OUTPUT_SIZE  = 400;
    private static final int    PREVIEW_SIZE = 64;

    @FXML private Pane      displayPane;
    @FXML private ImageView imgDisplay;
    @FXML private Canvas    overlayCanvas;
    @FXML private Canvas    previewCanvas;

    private Image            sourceImage;
    private double           imgX, imgY, imgW, imgH;
    private double           dragStartX, dragStartY, imgStartX, imgStartY;
    private Consumer<File>   onSave;

    @FXML
    public void initialize() {
        Rectangle clip = new Rectangle(DISPLAY_W, DISPLAY_H);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        displayPane.setClip(clip);
        previewCanvas.setClip(new Circle(PREVIEW_SIZE / 2.0, PREVIEW_SIZE / 2.0, PREVIEW_SIZE / 2.0));

        overlayCanvas.setOnMousePressed(e -> {
            dragStartX = e.getX(); dragStartY = e.getY();
            imgStartX  = imgX;    imgStartY  = imgY;
        });
        overlayCanvas.setOnMouseDragged(e -> {
            imgX = imgStartX + (e.getX() - dragStartX);
            imgY = imgStartY + (e.getY() - dragStartY);
            redraw();
        });
        overlayCanvas.setOnScroll(e -> {
            if (sourceImage == null) return;
            double factor  = e.getDeltaY() > 0 ? 1.1 : 1.0 / 1.1;
            double newImgW = imgW * factor;
            double newImgH = imgH * factor;
            if (newImgW < CROP_SIZE || newImgH < CROP_SIZE) return;
            double mx = e.getX(), my = e.getY();
            imgX = mx - (mx - imgX) * factor;
            imgY = my - (my - imgY) * factor;
            imgW = newImgW;
            imgH = newImgH;
            redraw();
        });
    }

    public void setImage(Image image, Consumer<File> onSave) {
        this.sourceImage = image;
        this.onSave      = onSave;
        double scale = Math.max(CROP_SIZE / image.getWidth(), CROP_SIZE / image.getHeight());
        imgW = image.getWidth()  * scale;
        imgH = image.getHeight() * scale;
        imgX = DISPLAY_W / 2.0 - imgW / 2.0;
        imgY = DISPLAY_H / 2.0 - imgH / 2.0;
        redraw();
    }

    private void redraw() {
        clamp();
        imgDisplay.setImage(sourceImage);
        imgDisplay.setFitWidth(imgW);
        imgDisplay.setFitHeight(imgH);
        imgDisplay.setLayoutX(imgX);
        imgDisplay.setLayoutY(imgY);
        drawOverlay();
        drawPreview();
    }

    private void clamp() {
        imgX = Math.max(CROP_LEFT + CROP_SIZE - imgW, Math.min(CROP_LEFT, imgX));
        imgY = Math.max(CROP_TOP  + CROP_SIZE - imgH, Math.min(CROP_TOP,  imgY));
    }

    private void drawOverlay() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, DISPLAY_W, DISPLAY_H);

        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillRect(0, 0, DISPLAY_W, DISPLAY_H);

        // Punch transparent hole for crop area
        gc.clearRect(CROP_LEFT, CROP_TOP, CROP_SIZE, CROP_SIZE);

        // Rule-of-thirds guides
        gc.setStroke(Color.color(1, 1, 1, 0.35));
        gc.setLineWidth(0.7);
        double t = CROP_SIZE / 3.0;
        gc.strokeLine(CROP_LEFT + t,   CROP_TOP, CROP_LEFT + t,   CROP_TOP + CROP_SIZE);
        gc.strokeLine(CROP_LEFT + 2*t, CROP_TOP, CROP_LEFT + 2*t, CROP_TOP + CROP_SIZE);
        gc.strokeLine(CROP_LEFT, CROP_TOP + t,   CROP_LEFT + CROP_SIZE, CROP_TOP + t);
        gc.strokeLine(CROP_LEFT, CROP_TOP + 2*t, CROP_LEFT + CROP_SIZE, CROP_TOP + 2*t);

        // Crop border
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeRect(CROP_LEFT, CROP_TOP, CROP_SIZE, CROP_SIZE);

        // Corner handles
        gc.setLineWidth(2.5);
        double h = 14;
        double l = CROP_LEFT, r = CROP_LEFT + CROP_SIZE;
        double top = CROP_TOP, bot = CROP_TOP + CROP_SIZE;
        gc.strokeLine(l, top, l + h, top); gc.strokeLine(l, top, l, top + h);
        gc.strokeLine(r, top, r - h, top); gc.strokeLine(r, top, r, top + h);
        gc.strokeLine(l, bot, l + h, bot); gc.strokeLine(l, bot, l, bot - h);
        gc.strokeLine(r, bot, r - h, bot); gc.strokeLine(r, bot, r, bot - h);
    }

    private void drawPreview() {
        if (sourceImage == null) return;
        double sx = sourceImage.getWidth()  / imgW;
        double sy = sourceImage.getHeight() / imgH;
        GraphicsContext gc = previewCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, PREVIEW_SIZE, PREVIEW_SIZE);
        gc.drawImage(sourceImage,
                (CROP_LEFT - imgX) * sx, (CROP_TOP - imgY) * sy,
                CROP_SIZE * sx, CROP_SIZE * sy,
                0, 0, PREVIEW_SIZE, PREVIEW_SIZE);
    }

    @FXML
    void handleSave() {
        if (sourceImage == null || onSave == null) return;
        try {
            onSave.accept(cropToFile());
            close();
        } catch (Exception e) {
            utils.AlertHelper.show(utils.AlertHelper.Type.ERROR,
                    "Lỗi", "Không thể lưu ảnh: " + e.getMessage());
        }
    }

    private File cropToFile() throws IOException {
        double sx   = sourceImage.getWidth()  / imgW;
        double sy   = sourceImage.getHeight() / imgH;
        double srcX = (CROP_LEFT - imgX) * sx;
        double srcY = (CROP_TOP  - imgY) * sy;
        double srcW = CROP_SIZE * sx;
        double srcH = CROP_SIZE * sy;

        Canvas c = new Canvas(OUTPUT_SIZE, OUTPUT_SIZE);
        c.getGraphicsContext2D().drawImage(sourceImage, srcX, srcY, srcW, srcH,
                0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        WritableImage wi = c.snapshot(sp, new WritableImage(OUTPUT_SIZE, OUTPUT_SIZE));

        int w = OUTPUT_SIZE, h = OUTPUT_SIZE;
        int[] pixels = new int[w * h];
        wi.getPixelReader().getPixels(0, 0, w, h,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, w);
        java.awt.image.BufferedImage bi =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, w, h, pixels, 0, w);

        File tmp = File.createTempFile("avatar_crop_", ".png");
        tmp.deleteOnExit();
        javax.imageio.ImageIO.write(bi, "PNG", tmp);
        return tmp;
    }

    @FXML
    void handleCancel() { close(); }

    private void close() {
        javafx.application.Platform.runLater(() -> {
            if (overlayCanvas.getScene() != null)
                ((Stage) overlayCanvas.getScene().getWindow()).close();
        });
    }
}
