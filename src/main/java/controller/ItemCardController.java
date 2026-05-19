package controller;

import javafx.application.Platform;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import model.auction.Auction;
import model.manager.AppState;
import model.user.Bidder;
import utils.ImageStorageService;

import java.util.function.Consumer;

/**
 * Controller cho từng thẻ sản phẩm (Item Card).
 */
public class ItemCardController {

    @FXML private VBox cardRoot;
    @FXML private ImageView imgItem;
    @FXML private Label lblCategory;
    @FXML private Label lblName;
    @FXML private Label lblPrice;
    @FXML private Label lblStatus;
    @FXML private Button btnQuickBid;
    @FXML private Label lblAutoBadge;

    private Auction auction;
    private Consumer<Auction> onViewDetails;
    private Consumer<Auction> onQuickBid;
    private SetChangeListener<String> autoBidListener;

    @FXML
    private void initialize() {
        cardRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null && autoBidListener != null) {
                AppState.getInstance().getMyAutoBidIds().removeListener(autoBidListener);
                autoBidListener = null;
            }
        });
    }

    /**
     * Nạp dữ liệu vào thẻ.
     */
    public void setData(Auction auction, Consumer<Auction> onViewDetails, Consumer<Auction> onQuickBid) {
        this.auction = auction;
        this.onViewDetails = onViewDetails;
        this.onQuickBid = onQuickBid;

        lblName.setText(auction.getItemName());
        lblPrice.setText(String.format("%,.0f ₫", auction.getCurrentPrice()));
        lblCategory.setText(auction.getItem() != null ? auction.getItem().getItemType().toUpperCase() : "GENERAL");
        
        String status = auction.getStatus();
        lblStatus.setText(status);
        
        // Cấu hình style cho status
        lblStatus.getStyleClass().removeAll("stat-green", "stat-orange", "stat-blue", "stat-purple", "stat-red");
        
        switch (status) {
            case "OPEN" -> {
                lblStatus.getStyleClass().add("stat-blue");
                lblStatus.setText("COMING");
            }
            case "RUNNING" -> {
                lblStatus.getStyleClass().add("stat-green");
                lblStatus.setText("LIVE");
            }
            case "FINISHED" -> {
                lblStatus.getStyleClass().add("stat-orange");
                lblStatus.setText("ENDED");
            }
            case "PAID" -> {
                lblStatus.getStyleClass().add("stat-purple");
                lblStatus.setText("PAID");
            }
            case "CANCELED" -> {
                lblStatus.getStyleClass().add("stat-red");
                lblStatus.setText("VOID");
            }
            default -> lblStatus.getStyleClass().add("stat-blue");
        }

        // Xử lý ảnh — tải từ HTTP server để mọi client đều thấy
        String imageUri = ImageStorageService.toImageUrl(auction.getItem().getImagePath());
        if (imageUri != null) {
            try {
                imgItem.setImage(new Image(imageUri, true)); // true = background loading
            } catch (Exception e) {
                setPlaceholder();
            }
        } else {
            setPlaceholder();
        }

        // Quyền hạn cho nút Quick Bid
        model.user.User cu = AppState.getInstance().getCurrentUser();
        boolean isOwnAuction = cu != null && cu.getUserId().equals(auction.getSellerId());
        boolean isBidder = (cu instanceof Bidder) && !isOwnAuction;
        boolean isRunning = "RUNNING".equals(status);
        btnQuickBid.setVisible(isBidder && isRunning);
        btnQuickBid.setManaged(isBidder && isRunning);

        // Badge Auto-Bid: hiển thị real-time khi user bật/tắt Auto-Bid
        if (lblAutoBadge != null) {
            refreshAutoBadge();
            if (autoBidListener != null) {
                AppState.getInstance().getMyAutoBidIds().removeListener(autoBidListener);
            }
            String auctionId = auction.getAuctionId();
            autoBidListener = change -> {
                if (auctionId.equals(change.getElementAdded())
                        || auctionId.equals(change.getElementRemoved())) {
                    Platform.runLater(this::refreshAutoBadge);
                }
            };
            AppState.getInstance().getMyAutoBidIds().addListener(autoBidListener);
        }
    }

    public String getAuctionId() {
        return auction != null ? auction.getAuctionId() : "";
    }

    private void refreshAutoBadge() {
        boolean hasAuto = AppState.getInstance().hasMyAutoBid(auction.getAuctionId());
        lblAutoBadge.setVisible(hasAuto);
        lblAutoBadge.setManaged(hasAuto);
    }

    private void setPlaceholder() {
        try {
            imgItem.setImage(new Image(getClass().getResourceAsStream("/Images/bid-hub-logo.png")));
        } catch (Exception e) {
            // Nếu logo cũng lỗi, để trống
        }
    }

    @FXML
    private void handleCardClick() {
        if (onViewDetails != null) {
            onViewDetails.accept(auction);
        }
    }

    @FXML
    private void handleQuickBid(javafx.event.ActionEvent event) {
        // Stop propagation để không kích hoạt handleCardClick
        event.consume();
        if (onQuickBid != null) {
            onQuickBid.accept(auction);
        }
    }
}
