package application;

/*
import model.item.*;
import model.user.*;
import model.auction.*;
import model.manager.AuctionManager;
import java.time.LocalDateTime;
*/
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/login.fxml"));
            
            Scene scene = new Scene(root);
            
            Image logo = new Image(getClass().getResourceAsStream("/images/logo-uet.jpg"));
            primaryStage.getIcons().add(logo);
            
            primaryStage.setScene(scene);
            primaryStage.setTitle("JavaFX Test App");
            primaryStage.show();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
    		launch(args);
        
    	/*
    	System.out.println("========== TEST HỆ THỐNG ĐẤU GIÁ (FACTORY & SINGLETON) ==========");

        AuctionManager manager = AuctionManager.getInstance();

        Item laptop = ItemFactory.createItem("ELECTRONICS", "ITM01", "MacBook Pro", "M3 Chip", 1500.0, "12");
        Item car = ItemFactory.createItem("VEHICLE", "ITM02", "Tesla Model 3", "Electric", 40000.0, "Dual Motor");
        

        System.out.println("[Factory] Đã tạo thành công: " + laptop.getItemName() + " và " + car.getItemName());

        Auction auc1 = new Auction("AUC01", laptop, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        Auction auc2 = new Auction("AUC02", car, LocalDateTime.now(), LocalDateTime.now().plusDays(2));

        manager.addAuction(auc1);
        manager.addAuction(auc2);
        
        System.out.println("[Manager] Đã thêm " + manager.getAllAuctions().size() + " phiên đấu giá vào bộ nhớ dùng chung.");

        User bidder1 = new Bidder("U01", "hieuluoidai", "luoidai@gmail.com", "pass123");
        User bidder2 = new Bidder("U02", "hieuluoingan", "luoingan@email.com", "pass456");

        System.out.println("\n--- BẮT ĐẦU PHIÊN ĐẤU GIÁ " + laptop.getItemName() + " ---");
        auc1.setStatus("RUNNING");

        try {
            auc1.placeBid(bidder1, 1600.0);
            System.out.println("Current Highest Bid: " + auc1.getHighestBid().getBidder().getUsername() + " - " + auc1.getHighestBid().getBidAmount());

            auc1.placeBid(bidder2, 1800.0);
            System.out.println("Current Highest Bid: " + auc1.getHighestBid().getBidder().getUsername() + " - " + auc1.getHighestBid().getBidAmount());

            // bidder1 tries to bid 1700$
            auc1.placeBid(bidder1, 1700.0);

        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println("\n========== TỔNG KẾT HỆ THỐNG ==========");
        for (Auction a : AuctionManager.getInstance().getAllAuctions()) {
            System.out.println("Mã đấu giá: " + a.getAuctionId() + " | Sản phẩm: " + a.getItem().getItemName());
            if (a.getHighestBid() != null) {
                System.out.println("   -> Người dẫn đầu: " + a.getHighestBid().getBidder().getUsername() + " ($" + a.getHighestBid().getBidAmount() + ")");
            } else {
                System.out.println("   -> Chưa có ai đặt giá.");
            }
        }
        
        */
    }
}