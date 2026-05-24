package controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import model.auction.Auction;
import model.manager.AppState;

/**
 * Controller for the Auctions management view in the Admin Dashboard.
 */
public class AdminAuctionsController {

    @FXML private FlowPane flowPaneAuctions;
    @FXML private VBox paneEndedSection;
    @FXML private FlowPane flowPaneEnded;
    @FXML private TextField txtAuctionSearch;
    @FXML private ToggleGroup auctionStatusGroup;
    @FXML private VBox paneEmptyAuctions;
    @FXML private Label labelTotalAuctions;
    @FXML private Label labelRunningAuctions;
    @FXML private Label labelFinishedAuctions;

    private FilteredList<Auction> filteredAuctions;
    private String selectedCategoryFilter = "ALL";
    private AdminController mainController;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        setupAuctionFiltering();
        updateAuctionStats();

        AppState.getInstance().getAuctionList().addListener(
            (javafx.collections.ListChangeListener<Auction>) c -> {
                javafx.application.Platform.runLater(this::updateAuctionStats);
            });

        AppState.getInstance().getStarredAuctionIds().addListener(
            (javafx.collections.SetChangeListener<String>) c ->
                javafx.application.Platform.runLater(this::renderAuctions));
    }

    public void setMainController(AdminController mainController) {
        this.mainController = mainController;
    }

    private void setupAuctionFiltering() {
        filteredAuctions = new FilteredList<>(AppState.getInstance().getAuctionList(), p -> true);
        filteredAuctions.addListener((javafx.collections.ListChangeListener<Auction>) c ->
                javafx.application.Platform.runLater(this::renderAuctions));
        txtAuctionSearch.textProperty().addListener(
                (obs, old, newValue) -> updateAuctionPredicate());
        auctionStatusGroup.selectedToggleProperty().addListener(
                (obs, old, newValue) -> updateAuctionPredicate());
        renderAuctions();
    }

    public void updateAuctionPredicate() {
        String searchText = txtAuctionSearch.getText() == null
                ? ""
                : txtAuctionSearch.getText().toLowerCase().trim();
        ToggleButton selectedTgl = (ToggleButton) auctionStatusGroup.getSelectedToggle();
        String statusFilter = selectedTgl == null ? "TẤT CẢ" : selectedTgl.getText().toUpperCase();
        
        filteredAuctions.setPredicate(auction -> {
            if (!selectedCategoryFilter.equals("ALL")) {
                if (auction.getItem() == null 
                        || !selectedCategoryFilter.equals(auction.getItem().getItemType().toUpperCase())) {
                    return false;
                }
            }

            boolean matchesSearch = searchText.isEmpty()
                    || auction.getItem().getItemName().toLowerCase().contains(searchText)
                    || auction.getAuctionId().toLowerCase().contains(searchText);
            if (!matchesSearch) {
                return false;
            }
            if (statusFilter.equals("TẤT CẢ")) {
                return true;
            }
            if (statusFilter.equals("SẮP DIỄN RA")) {
                return "OPEN".equals(auction.getStatus());
            }
            if (statusFilter.equals("ĐANG DIỄN RA")) {
                return "RUNNING".equals(auction.getStatus());
            }
            if (statusFilter.equals("ĐÃ KẾT THÚC")) {
                return isAuctionEnded(auction);
            }
            return true;
        });
    }

    private void renderAuctions() {
        if (flowPaneAuctions == null || flowPaneEnded == null) {
            return;
        }
        flowPaneAuctions.getChildren().clear();
        flowPaneEnded.getChildren().clear();

        if (filteredAuctions.isEmpty()) {
            showEmptyState(true);
            return;
        }
        showEmptyState(false);

        List<Auction> activeList = new ArrayList<>();
        List<Auction> endedList = new ArrayList<>();
        for (Auction a : filteredAuctions) {
            if (isAuctionEnded(a)) {
                endedList.add(a);
            } else {
                activeList.add(a);
            }
        }

        activeList.sort(this::compareAuctions);
        endedList.sort(this::compareAuctions);

        for (Auction a : activeList) {
            flowPaneAuctions.getChildren().add(createCard(a));
        }
        if (!endedList.isEmpty()) {
            paneEndedSection.setVisible(true);
            paneEndedSection.setManaged(true);
            for (Auction a : endedList) {
                flowPaneEnded.getChildren().add(createCard(a));
            }
        } else {
            paneEndedSection.setVisible(false);
            paneEndedSection.setManaged(false);
        }
    }

    private int compareAuctions(Auction a, Auction b) {
        boolean starA = AppState.getInstance().isStarred(a.getAuctionId());
        boolean starB = AppState.getInstance().isStarred(b.getAuctionId());
        if (starA && !starB) {
            return -1;
        }
        if (!starA && starB) {
            return 1;
        }
        return a.getAuctionId().compareTo(b.getAuctionId());
    }

    private boolean isAuctionEnded(Auction a) {
        String s = a.getStatus();
        return "FINISHED".equals(s) || "PAID".equals(s) || "CANCELED".equals(s);
    }

    private void showEmptyState(boolean empty) {
        if (paneEmptyAuctions != null) {
            paneEmptyAuctions.setVisible(empty);
            paneEmptyAuctions.setManaged(empty);
        }
        if (empty) {
            paneEndedSection.setVisible(false);
            paneEndedSection.setManaged(false);
        }
    }

    private javafx.scene.Node createCard(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/item_card.fxml"));
            javafx.scene.Node card = loader.load();
            ItemCardController cardController = loader.getController();
            cardController.setData(auction, this::openItemDetails, this::handleQuickBidOf);
            card.setUserData(cardController);
            return card;
        } catch (IOException e) {
            return new Label("Error");
        }
    }

    private void openItemDetails(Auction auction) {
        if (mainController != null) {
            mainController.openItemDetails(auction);
        }
    }

    private void handleQuickBidOf(Auction auction) {
        if (mainController != null) {
            mainController.handleQuickBidOf(auction);
        }
    }

    public void updateAuctionStats() {
        ObservableList<Auction> auctions = AppState.getInstance().getAuctionList();
        long running = auctions.stream().filter(a -> "RUNNING".equals(a.getStatus())).count();
        long finished = auctions.stream().filter(a -> "FINISHED".equals(a.getStatus())).count();
        long finalized = auctions.stream()
                .filter(a -> "PAID".equals(a.getStatus()) || "CANCELED".equals(a.getStatus()))
                .count();
        if (labelTotalAuctions != null) {
            labelTotalAuctions.setText(String.valueOf(auctions.size()));
        }
        if (labelRunningAuctions != null) {
            labelRunningAuctions.setText(String.valueOf(running));
        }
        if (labelFinishedAuctions != null) {
            labelFinishedAuctions.setText(String.valueOf(finished + finalized));
        }
    }

    @FXML
    public void handleRefresh() {
        updateAuctionStats();
        renderAuctions();
    }

    public void setCategoryFilter(String category) {
        this.selectedCategoryFilter = category;
        updateAuctionPredicate();
    }
}
