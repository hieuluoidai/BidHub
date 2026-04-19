package model.user;

/**
 * Class cho Bidder (có quyền đấu giá sản phẩm).
 */
public class Bidder extends User {
    public Bidder(String id, String name, String email, String pass) { 
        super(id, name, email, pass); 
    }
    
    @Override
    public void displayRole() { 
        System.out.println("Role: BIDDER - Tham gia trả giá."); 
    }
}