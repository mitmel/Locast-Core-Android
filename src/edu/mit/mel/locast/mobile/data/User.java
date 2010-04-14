package edu.mit.mel.locast.mobile.data;

import java.util.Date;



public class User{

	public String username;
	private String name;
	private String language;
	//private Image icon;
	private String transport; 
	private Date lastUpdated;
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the language
	 */
	public String getLanguage() {
		return language;
	}

	/**
	 * @param language the language to set
	 */
	public void setLanguage(String language) {
		this.language = language;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	
	
	public Date getLastUpdated() {
		return lastUpdated;
	}

	public String getFullName(){
		if(name != null && name.length() > 0){
			return name;
		}else{
			return username;
		}
	}
	public void setLastUpdated(Date lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	/*
	public Image getIcon() {
		return icon;
	}

	public void setIcon(Image icon) {
		this.icon = icon;
	}*/

	public String getTransport() {
		return transport;
	}

	public void setTransport(String transport) {
		this.transport = transport;
	}

	public User(String username){
		this.username = username;
	}
}
