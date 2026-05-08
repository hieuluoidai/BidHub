module online.auction.system {
    // Danh sách các nguyên liệu cần cho dự án
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires mysql.connector.j;

    // Thư viện băm mật khẩu BCrypt (Tính năng bảo mật của Hiếu)
    requires jbcrypt;

    // Cho phép JavaFX truy cập vào các class trong package để hiển thị dữ liệu lên bảng (Table) và load FXML
    opens model.auction to javafx.base;
    opens application to javafx.graphics;
    opens controller to javafx.fxml;

    // Xuất package để các module khác hoặc JVM có thể truy cập
    exports application;
    exports exception; // Xuất các Exception tùy chỉnh của nhóm (Cực kỳ quan trọng)
}