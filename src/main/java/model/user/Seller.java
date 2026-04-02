package model.user;

public class Seller extends User {
    // Constructor
	public Seller(String id, String name, String email, String pass) { 
    	super(id, name, email, pass); 
    }
    
    @Override
    public void displayRole() { 
    	System.out.println("Role: SELLER - Can post items."); 
    }
}
