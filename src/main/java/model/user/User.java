package model.user;

import model.core.Entity;

public abstract class User extends Entity {
	private String fullName;
	private String email;
	private String password;
	
	// Constructor 
	public User(String userId, String fullName, String email, String password) {
		super(userId);
		this.fullName = fullName;
		this.email = email;
		this.password = password;
	}
	
	// Abstract method
	public abstract void displayRole();
	
	// Setters
	public void setUserId(String userId) 	 { super.setId(userId);    	 }
	public void setfullName(String fullName) { this.fullName = fullName; }
	public void setEmail(String email) 		 { this.email = email; 		 }
	public void setPassWord(String password) { this.password = password; }
	
	
	// Getters
	public String getUserId()   { return super.getId(); }
	public String getFullName() { return this.fullName; }
	public String getEmail()    { return this.email;    }
	public String getPassWord() { return this.password; }

}
