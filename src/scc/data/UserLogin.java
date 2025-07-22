package scc.data;

import java.util.Arrays;

/**
 * Represents a User, as returned to the clients
 * 
 * NOTE: array of house ids is shown as an example of how to store a list of elements and 
 * handle the empty list.
 */
public class UserLogin {
	private String username;
	private String pwd;

	public UserLogin() {
	}

	public UserLogin(String username, String pwd) {
		this.username = username;
		this.pwd = pwd;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPwd() {
		return pwd;
	}

	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
}
