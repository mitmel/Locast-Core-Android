package edu.mit.mel.locast.mobile.data;

import java.util.Date;

public class TemporalCoordinates extends Coordinates {

	private Date date;
	
	public TemporalCoordinates(float latitude, float longitude, float altitude, Date date) {
		super(latitude, longitude, altitude);
		this.date = date;
	}

	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	
}
