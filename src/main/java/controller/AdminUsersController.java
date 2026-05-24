package controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import model.auction.Auction;
import model.manager.AppState;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

import database.DepositRequestDAO;
import database.UserDAO;
import utils.ImageStorageService;

/**
 * Controller for the Users management view in the Admin Dashboard.
 */
public class AdminUsersController {

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colUserId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colStatus;
    @FXML private TableColumn<User, String> colBalance;
    @FXML private TextField txtUserSearch;
    @FXML private VBox paneEmptyUsers;
    @FXML private Label labelTotalUsers;
    @FXML private Label labelTotalBidders;
    @FXML private Label labelTotalSellers;

    private FilteredList<User> filteredUsers;
    private final ObservableList<User> masterUsers = FXCollections.observableArrayList();
    private Set<String> pendingDepositUserIds = new HashSet<>();
    private AdminController mainController;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        setupUserTable();
        loadUserData();

        userTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldVal, newVal) -> {
                row.getStyleClass().remove("row-pending");
                if (newVal != null && newVal.isPendingSeller()) {
                    row.getStyleClass().add("row-pending");
                }
            });
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openUserDetails(row.getItem());
                }
            });
            return row;
        });
    }

    public void setMainController(AdminController mainController) {
        this.mainController = mainController;
    }

    public void setupUserTable() {
        filteredUsers = new FilteredList<>(masterUsers, p -> true);
        userTable.setItems(filteredUsers);
        userTable.setPlaceholder(new Label(""));
        filteredUsers.addListener((javafx.collections.ListChangeListener<User>) c -> {
            boolean isEmpty = filteredUsers.isEmpty();
            if (paneEmptyUsers != null) {
                paneEmptyUsers.setVisible(isEmpty);
                paneEmptyUsers.setManaged(isEmpty);
            }
        });
        if (txtUserSearch != null) {
            txtUserSearch.textProperty().addListener((obs, old, newValue) -> {
                filteredUsers.setPredicate(user -> {
                    if (newValue == null || newValue.isEmpty()) {
                        return true;
                    }
                    String filter = newValue.toLowerCase().trim();
                    return user.getUsername().toLowerCase().contains(filter)
                            || user.getEmail().toLowerCase().contains(filter)
                            || user.getUserId().toLowerCase().contains(filter);
                });
            });
        }
        colUserId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserId()));
        colUsername.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().getUsername()));
        colUsername.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String username, boolean empty) {
                super.updateItem(username, empty);
                if (empty || username == null) {
                    setGraphic(null);
                    return;
                }
                User u = getTableRow().getItem();
                StackPane avatar = new StackPane();
                avatar.setMinSize(36, 36);
                avatar.setMaxSize(36, 36);
                String color = (u instanceof Admin) ? "#3B82F6"
                        : (u instanceof Seller) ? "#8B5CF6" : "#10B981";
                Label lblInit = new Label(
                        username.isEmpty() ? "?" : username.substring(0, 1).toUpperCase());
                lblInit.setStyle(
                        "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
                avatar.setStyle(
                        "-fx-background-color: " + color + "; -fx-background-radius: 50%;");
                avatar.getChildren().add(lblInit);
                if (u != null && u.getAvatarPath() != null && !u.getAvatarPath().isEmpty()) {
                    String uri = ImageStorageService.toImageUrl(u.getAvatarPath());
                    if (uri != null) {
                        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                                new javafx.scene.image.Image(uri, 36, 36, true, true));
                        iv.setFitWidth(36);
                        iv.setFitHeight(36);
                        iv.setClip(new javafx.scene.shape.Circle(18, 18, 18));
                        avatar.getChildren().add(iv);
                    }
                }
                Label lblName = new Label(username);
                lblName.setStyle("-fx-font-size: 13px;");
                HBox box = new HBox(10, avatar, lblName);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colRole.setCellValueFactory(
                d -> new SimpleStringProperty(
                        d.getValue().getClass().getSimpleName().toUpperCase()));
        colRole.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(role);
                    badge.getStyleClass().add("role-badge");
                    if ("ADMIN".equals(role)) {
                        badge.getStyleClass().add("role-admin");
                    } else if ("SELLER".equals(role)) {
                        badge.getStyleClass().add("role-seller");
                    } else {
                        badge.getStyleClass().add("role-bidder");
                    }
                    setGraphic(badge);
                }
            }
        });
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserId()));
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String userId, boolean empty) {
                super.updateItem(userId, empty);
                if (empty || userId == null
                        || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                User user = getTableRow().getItem();
                List<javafx.scene.Node> badges = buildStatusBadges(user);
                if (badges.isEmpty()) {
                    Label label = new Label("Bình thường");
                    label.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px;");
                    setGraphic(label);
                } else {
                    FlowPane flow = new FlowPane();
                    flow.getStyleClass().add("status-badge-flow");
                    flow.getChildren().addAll(badges);
                    setGraphic(flow);
                }
            }
        });
        if (colBalance != null) {
            colBalance.setCellValueFactory(
                    d -> new SimpleStringProperty(
                            String.format("%,.0f ₫", d.getValue().getBalance())));
            colBalance.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String balance, boolean empty) {
                    super.updateItem(balance, empty);
                    if (empty || balance == null) {
                        setText(null);
                        getStyleClass().remove("balance-cell-positive");
                    } else {
                        setText(balance);
                        if (!balance.startsWith("0 ₫")) {
                            getStyleClass().add("balance-cell-positive");
                        } else {
                            getStyleClass().remove("balance-cell-positive");
                        }
                    }
                }
            });
        }
    }

    private List<javafx.scene.Node> buildStatusBadges(User user) {
        List<javafx.scene.Node> badges = new ArrayList<>();
        if (user.isPendingSeller()) {
            badges.add(makeBadge("CHỜ DUYỆT SELLER", "role-pending"));
        }
        if (pendingDepositUserIds.contains(user.getUserId())) {
            badges.add(makeBadge("CHỜ NẠP TIỀN", "role-deposit"));
        }
        String uid = user.getUserId();
        boolean hosting = false;
        for (Auction a : AppState.getInstance().getAuctionList()) {
            if (!"RUNNING".equals(a.getStatus())) {
                continue;
            }
            if (uid.equals(a.getSellerId())) {
                hosting = true;
                break;
            }
        }
        if (hosting) {
            badges.add(makeBadge("ĐANG MỞ PHIÊN", "role-hosting"));
        }
        return badges;
    }

    private Label makeBadge(String text, String colorClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("role-badge", colorClass);
        return badge;
    }

    public void loadUserData() {
        try {
            pendingDepositUserIds = new HashSet<>(new DepositRequestDAO().getPendingUserIds());
            List<User> allUsers = new UserDAO().findAll();
            masterUsers.setAll(allUsers);
            long bidders = allUsers.stream().filter(u -> u instanceof Bidder).count();
            long sellers = allUsers.stream().filter(u -> u instanceof Seller).count();
            if (labelTotalUsers != null) {
                labelTotalUsers.setText(String.valueOf(allUsers.size()));
            }
            if (labelTotalBidders != null) {
                labelTotalBidders.setText(String.valueOf(bidders));
            }
            if (labelTotalSellers != null) {
                labelTotalSellers.setText(String.valueOf(sellers));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openUserDetails(User user) {
        if (mainController != null) {
            mainController.openUserDetails(user);
        }
    }

    @FXML
    public void handleRefresh() {
        loadUserData();
        if (userTable != null) {
            userTable.refresh();
        }
    }

    public TableView<User> getUserTable() {
        return userTable;
    }

    public ObservableList<User> getMasterUsers() {
        return masterUsers;
    }
}
