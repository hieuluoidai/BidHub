package model.user;

public class Admin extends User {
    // Constructor
	public Admin(String id, String name, String email, String pass) { 
    	super(id, name, email, pass); 
	}
    
    @Override
    public void displayRole() { 
    	System.out.println("Role: ADMIN - Manages system."); 
    }
}
