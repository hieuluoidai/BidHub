module online.auction.system {
	// Danh sách các "nguyên liệu" cần cho dự án
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    // Cho phép JavaFX truy cập vào các class trong package application và controller
    opens application to javafx.graphics, javafx.fxml;
    opens controller to javafx.fxml;

    // Xuất package nếu các module khác cần dùng
    exports application;
}