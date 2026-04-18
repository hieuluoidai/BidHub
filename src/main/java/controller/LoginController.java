package controller;

import java.io.IOException;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import model.manager.AppState;
import model.manager.SessionManager;
import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import utils.SceneSwitcher;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    
    private List<User> mockUsers = List.of(
    	    new Admin("AD01", "admin", "admin@vnu.edu.vn", "uet123"),
    	    new Bidder("B01", "bidder", "hieu@vnu.edu.vn", "uet456"),
    	    new Seller("S01", "seller", "seller@vnu.edu.vn", "uet789")
    	);
    
    @FXML
    void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        User foundUser = null;
        for (User u : mockUsers) {
            if (u.getUsername().equals(username) && u.getPassword().equals(password)) {
                foundUser = u;
                break;
            }
        }

        if (foundUser != null) {
            try {
                // SỬA 1: Kết nối Socket với Server TRƯỚC khi vào Dashboard.
                // LƯU Ý QUAN TRỌNG: Hãy đảm bảo port "1234" ở đây 
                // TRÙNG KHỚP với port bạn đang bật bên file AuctionServer.java nhé!
                AppState.getInstance().getClient().connect("localhost", 1234);

                // SỬA 2: Lưu User vào AppState để DashboardController lấy được
                AppState.getInstance().setCurrentUser(foundUser);
                
                System.out.println("Logged in as: " + foundUser.getClass().getSimpleName());
                
                // Chuyển sang màn hình Dashboard
                SceneSwitcher.switchScene(event, "/view/dashboard.fxml");
                
            } catch (Exception e) {
                messageLabel.setText("Lỗi: Không thể kết nối tới Server! Bạn đã bật Server chưa?");
                messageLabel.setTextFill(Color.RED);
                e.printStackTrace();
            }
        } else {
            messageLabel.setText("Invalid username or password!");
            messageLabel.setTextFill(Color.RED);
        }
    }
}