package model.user;

/**
 * Class cho admin (quyền điều hành toàn bộ hệ thống)
 */
public class Admin extends User {
    public Admin(String id, String name, String email, String pass) { 
        super(id, name, email, pass); 
    }
    
    @Override
    public void displayRole() { 
        System.out.println("Role: ADMIN - Quản trị hệ thống."); 
    }
}