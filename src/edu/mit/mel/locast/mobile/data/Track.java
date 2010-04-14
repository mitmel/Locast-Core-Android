package edu.mit.mel.locast.mobile.data;

import java.util.Date;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Track extends UserCreatedItem {
	public Track() {
		setTitle(new Date().toString());
	}
	
	Vector<TemporalCoordinates> track = new Vector<TemporalCoordinates>();

	public JSONObject toJSON() throws JSONException {
		JSONObject json = super.toJSON();
		
		JSONArray times = new JSONArray();
		for (int i = 0; i < track.size(); i++) {
			times.put(((TemporalCoordinates) track.elementAt(i)).getDate());
		}
		
		JSONObject props = json.getJSONObject("properties");
		props.put("times", times);
		
		json.put("geometry", trackToGeoJsonLineString());
		
		return json;
	}

	public JSONArray coordinatesToJson(Coordinates location) throws JSONException {
		JSONArray a = new JSONArray();
		a.put(location.getLongitude());
		a.put(location.getLatitude());
		return a;
	}

	public JSONObject trackToGeoJsonLineString() throws JSONException {
		JSONObject o = new JSONObject();
		JSONArray coordinates = new JSONArray();

		for (int i = 0; i < track.size(); i++) {
			coordinates.put(coordinatesToJson(track.elementAt(i)));
		}
		o.put("type", "LineString");
		o.put("coordinates", coordinates);

		return o;
	}

	public Vector<TemporalCoordinates> getTrack() {
		return track;
	}

	public void setTrack(Vector<TemporalCoordinates> track) {
		this.track = track;
	}

	public void addLocation(TemporalCoordinates location) {
		track.addElement(location);
	}
}
