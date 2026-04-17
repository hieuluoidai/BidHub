module online.auction.system {
    requires javafx.controls;
    requires javafx.fxml;

    // Cho phép JavaFX truy cập vào các class trong package application và controller
    opens application to javafx.graphics, javafx.fxml;
    opens controller to javafx.fxml;

    // Xuất package nếu các module khác cần dùng
    exports application;
}