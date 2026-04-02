package model.user;

public class Bidder extends User {
    // Constructor
	public Bidder(String id, String name, String email, String pass) { 
		super(id, name, email, pass); 
	}
    
    @Override
    public void displayRole() { 
    	System.out.println("Role: BIDDER - Can place bids."); 
    }
}
