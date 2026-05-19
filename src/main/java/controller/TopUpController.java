package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import model.manager.AppState;
import model.user.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Dialog nạp tiền — 2 bước:
 *  1. User nhập số tiền → click "Tiếp theo"
 *  2. QR chuyển khoản BIDV hiện ra → user bấm "Đã chuyển tiền" → gửi DEPOSIT_REQUEST
 */
public class TopUpController {

    @FXML private VBox  stepInputPane;
    @FXML private VBox  stepQrPane;

    // Bước 1
    @FXML private Label             lblCurrentBalance;
    @FXML private javafx.scene.control.TextField textAmount;
    @FXML private Label             lblError;
    @FXML private Button            btnCancel;
    @FXML private Button            btnTopUp;

    // Bước 2
    @FXML private ImageView         imgQr;
    @FXML private Label             lblQrAmount;
    @FXML private Label             lblRefCode;
    @FXML private HBox              paneLoading;
    @FXML private Button            btnBack;
    @FXML private Button            btnConfirmTransfer;

    private double pendingAmount;
    private String pendingRequestId;

    private Runnable onTopUpSuccess;

    @FXML
    public void initialize() {
        User user = AppState.getInstance().getCurrentUser();
        if (user != null) {
            lblCurrentBalance.setText(String.format("%,.0f ₫", user.getBalance()));
        }
        textAmount.setOnAction(e -> handleTopUp());
    }

    public void setOnTopUpSuccess(Runnable callback) {
        this.onTopUpSuccess = callback;
    }

    /** Bước 1: validate rồi chuyển sang bước 2 QR. */
    @FXML
    void handleTopUp() {
        clearError();
        String input = textAmount.getText() == null ? "" : textAmount.getText().trim();

        if (input.isEmpty()) {
            showError("Vui lòng nhập số tiền cần nạp");
            return;
        }
        try {
            pendingAmount = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ");
            return;
        }
        if (pendingAmount <= 0) {
            showError("Số tiền phải lớn hơn 0");
            return;
        }
        if (pendingAmount > 1_000_000_000) {
            showError("Số tiền vượt quá giới hạn ($1B)");
            return;
        }

        User user = AppState.getInstance().getCurrentUser();
        if (user == null) {
            showError("Phiên đăng nhập đã hết hạn");
            return;
        }

        // Sinh mã giao dịch: BH + 6 ký tự userId + 6 số timestamp
        String uid = user.getUserId().replaceAll("[^a-zA-Z0-9]", "");
        uid = uid.substring(0, Math.min(6, uid.length()));
        pendingRequestId = "BH" + uid + (System.currentTimeMillis() % 1_000_000);

        showQrStep();
    }

    private void showQrStep() {
        stepInputPane.setVisible(false);
        stepInputPane.setManaged(false);
        stepQrPane.setVisible(true);
        stepQrPane.setManaged(true);
        Platform.runLater(() -> {
            if (btnBack != null && btnBack.getScene() != null) {
                ((Stage) btnBack.getScene().getWindow()).sizeToScene();
            }
        });

        lblQrAmount.setText(String.format("%,.0f ₫", pendingAmount));
        lblRefCode.setText(pendingRequestId);

        // Tải ảnh QR từ VietQR API
        try {
            String accountName = URLEncoder.encode("Nguyễn Trung Hiếu", StandardCharsets.UTF_8);
            String refCode     = URLEncoder.encode(pendingRequestId, StandardCharsets.UTF_8);
            long amountCents   = (long) pendingAmount; // VietQR dùng đơn vị VND, hiển thị USD ký hiệu
            String url = "https://img.vietqr.io/image/BIDV-4411622942-compact.png"
                    + "?amount=" + amountCents
                    + "&addInfo=" + refCode
                    + "&accountName=" + accountName;
            imgQr.setImage(new Image(url, true));
        } catch (Exception e) {
            System.err.println("[TopUp] Lỗi tải QR: " + e.getMessage());
        }
    }

    /** Bước 2: quay lại bước 1. */
    @FXML
    void handleBack() {
        stepQrPane.setVisible(false);
        stepQrPane.setManaged(false);
        stepInputPane.setVisible(true);
        stepInputPane.setManaged(true);
        setStep2Loading(false);
        Platform.runLater(() -> {
            if (btnCancel != null && btnCancel.getScene() != null) {
                ((Stage) btnCancel.getScene().getWindow()).sizeToScene();
            }
        });
    }

    /** Bước 2: user xác nhận đã chuyển → gửi DEPOSIT_REQUEST tới server. */
    @FXML
    void handleConfirmTransfer() {
        User user = AppState.getInstance().getCurrentUser();
        if (user == null) return;

        setStep2Loading(true);

        AppState.getInstance().getClient().setStringMessageCallback(this::handleServerResponse);
        AppState.getInstance().getClient().send(
                "DEPOSIT_REQUEST:" + user.getUserId() + ":" + pendingAmount + ":" + pendingRequestId);
    }

    private void handleServerResponse(String msg) {
        if (!msg.startsWith("DEPOSIT_REQUEST_OK") && !msg.startsWith("DEPOSIT_REQUEST_FAILED")) {
            return;
        }

        AppState.getInstance().getClient().setStringMessageCallback(null);

        Platform.runLater(() -> {
            setStep2Loading(false);
            if (msg.startsWith("DEPOSIT_REQUEST_OK")) {
                close();
                utils.AlertHelper.show(
                        utils.AlertHelper.Type.SUCCESS,
                        "Yêu cầu đã được gửi",
                        "Yêu cầu nạp " + String.format("%,.0f ₫", pendingAmount)
                                + " đã gửi tới admin.\n"
                                + "Bạn sẽ nhận thông báo khi được duyệt.");
                if (onTopUpSuccess != null) onTopUpSuccess.run();
            } else {
                String reason = msg.substring("DEPOSIT_REQUEST_FAILED:".length()).trim();
                utils.AlertHelper.show(utils.AlertHelper.Type.ERROR,
                        "Gửi yêu cầu thất bại: " + reason);
                handleBack();
            }
        });
    }

    @FXML
    void handleCancel() {
        AppState.getInstance().getClient().setStringMessageCallback(null);
        close();
    }

    private void setStep2Loading(boolean loading) {
        if (paneLoading != null) {
            paneLoading.setVisible(loading);
            paneLoading.setManaged(loading);
        }
        if (btnConfirmTransfer != null) btnConfirmTransfer.setDisable(loading);
        if (btnBack != null) btnBack.setDisable(loading);
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setTextFill(Color.web("#EF4444"));
    }

    private void clearError() {
        lblError.setText("");
    }

    private void close() {
        Platform.runLater(() -> {
            if (btnCancel != null && btnCancel.getScene() != null) {
                Stage stage = (Stage) btnCancel.getScene().getWindow();
                stage.close();
            }
        });
    }
}
