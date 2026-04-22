package model;

import model.user.Admin;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho cây kế thừa User – kiểm tra tính đa hình (Polymorphism).
 */
@DisplayName("User hierarchy – kiểm tra kế thừa và đa hình")
class UserTest {

    @Test
    @DisplayName("Bidder lưu đúng thông tin")
    void bidder_storesCorrectData() {
        Bidder b = new Bidder("U_01", "alice", "alice@mail.com", "pass1");

        assertEquals("U_01",           b.getUserId());
        assertEquals("alice",          b.getUsername());
        assertEquals("alice@mail.com", b.getEmail());
        assertEquals("pass1",          b.getPassword());
    }

    @Test
    @DisplayName("Seller lưu đúng thông tin")
    void seller_storesCorrectData() {
        Seller s = new Seller("U_02", "bob", "bob@mail.com", "pass2");

        assertEquals("U_02",         s.getUserId());
        assertEquals("bob",          s.getUsername());
        assertEquals("bob@mail.com", s.getEmail());
        assertEquals("pass2",        s.getPassword());
    }

    @Test
    @DisplayName("Admin lưu đúng thông tin")
    void admin_storesCorrectData() {
        Admin a = new Admin("U_03", "root", "root@mail.com", "pass3");

        assertEquals("U_03",          a.getUserId());
        assertEquals("root",          a.getUsername());
        assertEquals("root@mail.com", a.getEmail());
        assertEquals("pass3",         a.getPassword());
    }

    @Test
    @DisplayName("Bidder kế thừa từ User")
    void bidder_isUser() {
        User u = new Bidder("U_01", "alice", "alice@mail.com", "pass");
        assertInstanceOf(User.class, u);
    }

    @Test
    @DisplayName("Seller kế thừa từ User")
    void seller_isUser() {
        User u = new Seller("U_02", "bob", "bob@mail.com", "pass");
        assertInstanceOf(User.class, u);
    }

    @Test
    @DisplayName("Admin kế thừa từ User")
    void admin_isUser() {
        User u = new Admin("U_03", "root", "root@mail.com", "pass");
        assertInstanceOf(User.class, u);
    }

    @Test
    @DisplayName("displayRole không ném exception cho Bidder")
    void displayRole_bidder_noException() {
        Bidder b = new Bidder("U_01", "alice", "alice@mail.com", "pass");
        assertDoesNotThrow(b::displayRole);
    }

    @Test
    @DisplayName("displayRole không ném exception cho Seller")
    void displayRole_seller_noException() {
        Seller s = new Seller("U_02", "bob", "bob@mail.com", "pass");
        assertDoesNotThrow(s::displayRole);
    }

    @Test
    @DisplayName("displayRole không ném exception cho Admin")
    void displayRole_admin_noException() {
        Admin a = new Admin("U_03", "root", "root@mail.com", "pass");
        assertDoesNotThrow(a::displayRole);
    }

    @Test
    @DisplayName("Đa hình: gọi displayRole qua reference User hoạt động đúng")
    void polymorphism_worksCorrectly() {
        User[] users = {
            new Bidder("U_01", "alice", "alice@mail.com", "pass"),
            new Seller("U_02", "bob",   "bob@mail.com",   "pass"),
            new Admin ("U_03", "root",  "root@mail.com",  "pass")
        };

        for (User u : users) {
            assertDoesNotThrow(u::displayRole);
        }
    }

    @Test
    @DisplayName("setUsername cập nhật đúng username")
    void setUsername_updatesField() {
        Bidder b = new Bidder("U_01", "alice", "alice@mail.com", "pass");
        b.setUsername("alice_updated");
        assertEquals("alice_updated", b.getUsername());
    }

    @Test
    @DisplayName("setEmail cập nhật đúng email")
    void setEmail_updatesField() {
        Bidder b = new Bidder("U_01", "alice", "alice@mail.com", "pass");
        b.setEmail("newmail@test.com");
        assertEquals("newmail@test.com", b.getEmail());
    }
}