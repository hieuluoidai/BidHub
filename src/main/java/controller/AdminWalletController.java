package controller;

import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import model.auction.DepositRequest;
import model.manager.AppState;

import database.DepositRequestDAO;
import utils.AlertHelper;

/**
 * Controller for the Wallet/Deposits management view in the Admin Dashboard.
 */
public class AdminWalletController {

    @FXML private TableView<DepositRequest> depositTable;
    @FXML private TableColumn<DepositRequest, String> colDepositRefCode;
    @FXML private TableColumn<DepositRequest, String> colDepositUsername;
    @FXML private TableColumn<DepositRequest, String> colDepositAmount;
    @FXML private TableColumn<DepositRequest, String> colDepositTime;
    @FXML private TableColumn<DepositRequest, String> colDepositAction;
    @FXML private VBox paneEmptyDeposits;
    @FXML private Label lblDepositCount;

    private final ObservableList<DepositRequest> depositList = FXCollections.observableArrayList();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private AdminController mainController;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        setupDepositTable();
        loadDepositData();
    }

    public void setMainController(AdminController mainController) {
        this.mainController = mainController;
    }

    private void setupDepositTable() {
        if (depositTable == null) {
            return;
        }
        depositTable.setItems(depositList);
        depositTable.setPlaceholder(new Label(""));
        depositList.addListener((javafx.collections.ListChangeListener<DepositRequest>) c -> {
            boolean isEmpty = depositList.isEmpty();
            if (paneEmptyDeposits != null) {
                paneEmptyDeposits.setVisible(isEmpty);
                paneEmptyDeposits.setManaged(isEmpty);
            }
        });
        colDepositRefCode.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().getRequestId()));
        colDepositUsername.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().getUsername()));
        colDepositAmount.setCellValueFactory(
                d -> new SimpleStringProperty(
                        String.format("%,.0f ₫", d.getValue().getAmount())));
        colDepositAmount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                } else {
                    setText(v);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #0369a1;");
                }
            }
        });
        colDepositTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCreatedAt() != null
                        ? d.getValue().getCreatedAt().format(DT_FMT) : ""));
        colDepositAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnApprove = new Button("✓ Duyệt");
            private final Button btnReject = new Button("✗ Từ chối");
            {
                btnApprove.setStyle(
                        "-fx-background-color: #10B981; -fx-text-fill: white;"
                        + " -fx-background-radius: 6; -fx-font-size: 12px; -fx-padding: 4 10;");
                btnReject.setStyle(
                        "-fx-background-color: #EF4444; -fx-text-fill: white;"
                        + " -fx-background-radius: 6; -fx-font-size: 12px; -fx-padding: 4 10;");
                btnApprove.setOnAction(
                        e -> handleDepositApprove(getTableView().getItems().get(getIndex())));
                btnReject.setOnAction(
                        e -> handleDepositReject(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(10, btnApprove, btnReject);
                    box.setAlignment(javafx.geometry.Pos.CENTER);
                    setGraphic(box);
                    setStyle("-fx-alignment: CENTER; -fx-padding: 0;");
                }
            }
        });
    }

    public void loadDepositData() {
        try {
            List<DepositRequest> pending = new DepositRequestDAO().findPending();
            depositList.setAll(pending);
            if (lblDepositCount != null) {
                lblDepositCount.setText(String.valueOf(pending.size()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDepositApprove(DepositRequest dr) {
        AppState.getInstance().getClient().setStringMessageCallback(msg -> {
            if (!msg.startsWith("DEPOSIT_REVIEW_OK")
                    && !msg.startsWith("DEPOSIT_REVIEW_FAILED")) {
                return;
            }
            AppState.getInstance().getClient().setStringMessageCallback(null);
            javafx.application.Platform.runLater(() -> {
                if (msg.startsWith("DEPOSIT_REVIEW_OK")) {
                    loadDepositData();
                    if (mainController != null) {
                        mainController.refreshUserData();
                    }
                    AlertHelper.show(AlertHelper.Type.SUCCESS,
                            "Đã duyệt yêu cầu nạp cho " + dr.getUsername());
                } else {
                    AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi",
                            msg.substring("DEPOSIT_REVIEW_FAILED:".length()));
                }
            });
        });
        AppState.getInstance().getClient().send(
                "DEPOSIT_REVIEW:" + dr.getRequestId() + ":APPROVED:");
    }

    private void handleDepositReject(DepositRequest dr) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Từ chối yêu cầu nạp tiền");
        dialog.setHeaderText("Từ chối nạp của " + dr.getUsername());
        dialog.setContentText("Lý do từ chối (tùy chọn):");
        dialog.showAndWait().ifPresent(note -> {
            AppState.getInstance().getClient().setStringMessageCallback(msg -> {
                if (!msg.startsWith("DEPOSIT_REVIEW_OK")
                        && !msg.startsWith("DEPOSIT_REVIEW_FAILED")) {
                    return;
                }
                AppState.getInstance().getClient().setStringMessageCallback(null);
                javafx.application.Platform.runLater(() -> {
                    if (msg.startsWith("DEPOSIT_REVIEW_OK")) {
                        loadDepositData();
                        if (mainController != null) {
                            mainController.refreshUserData();
                        }
                        AlertHelper.show(AlertHelper.Type.SUCCESS,
                                "Đã từ chối yêu cầu nạp tiền của " + dr.getUsername());
                    } else {
                        AlertHelper.show(AlertHelper.Type.ERROR, "Lỗi",
                                msg.substring("DEPOSIT_REVIEW_FAILED:".length()));
                    }
                });
            });
            AppState.getInstance().getClient().send(
                    "DEPOSIT_REVIEW:" + dr.getRequestId() + ":REJECTED:" + note);
        });
    }

    @FXML
    public void handleRefreshDeposits() {
        loadDepositData();
        if (mainController != null) {
            mainController.refreshUserData();
        }
    }
}
