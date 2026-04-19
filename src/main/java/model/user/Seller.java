package model.user;

/**
 * Class cho seller, có quyền upload sản phẩm đấu giá.
 */
public class Seller extends User {
    public Seller(String id, String name, String email, String pass) { 
        super(id, name, email, pass); 
    }
    
    @Override
    public void displayRole() { 
        System.out.println("Role: SELLER - Đăng bán sản phẩm."); 
    }
}