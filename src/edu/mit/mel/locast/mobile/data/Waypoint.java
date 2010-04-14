package edu.mit.mel.locast.mobile.data;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.mit.mel.locast.mobile.net.NetworkProtocolException;


public class Waypoint extends Locatable {
	// not much to see here.
	public Waypoint(){
		
	}
	public Waypoint(JSONObject item) throws JSONException, IOException,
			NetworkProtocolException {
		fromJSON(this, item);
	}
}
