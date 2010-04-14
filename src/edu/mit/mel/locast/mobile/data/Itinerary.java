package edu.mit.mel.locast.mobile.data;

import java.io.IOException;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.mit.mel.locast.mobile.net.NetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkProtocolException;

public class Itinerary extends UserCreatedItem {
	private Vector<Waypoint> waypoints;

	public Itinerary (){
		waypoints = new Vector<Waypoint>();
	}
	
	public Itinerary (JSONObject jsonItinerary) throws JSONException, IOException, NetworkProtocolException{
		this();
		fromJSON(this, jsonItinerary);
	}
	
	public Vector<Waypoint> getWaypoints() {
		return waypoints;
	}

	public void setWaypoints(Vector<Waypoint> waypoints) {
		this.waypoints = waypoints;
	}
	/*
	public WgsBoundingBox getBoundingBox() {
		double minLat = 999, minLon = 999;
		double maxLat = -999, maxLon = -999;
		
		for (int i = 0; i < waypoints.size(); i++) {
			Coordinates c = ((Locatable)waypoints.elementAt(i)).getLoc();
			final double lat = c.getLatitude();
			final double lon = c.getLongitude();
			
			if (lat < minLat) minLat = lat;
			if (lat > maxLat) maxLat = lat;
			
			if (lon < minLon) minLon = lon;
			if (lon > maxLon) maxLon = lon;		
		}
		return new WgsBoundingBox(minLon, minLat, maxLon, maxLat);
	}
	*/
	public static void fromJSONProperties(Itinerary itinerary, JSONObject jsonProperties) throws JSONException, IOException, NetworkProtocolException{
		JSONObject jsonFeature = new JSONObject();
		jsonFeature.put("id", jsonProperties.getInt("id"));
		jsonFeature.put("properties", jsonProperties);
		fromJSON(itinerary, jsonFeature);
	}
	
	public static void fromJSON(Itinerary itinerary, JSONObject jsonItinerary) throws JSONException, IOException, NetworkProtocolException{
		Locatable.fromJSON(itinerary, jsonItinerary);
		
		if (jsonItinerary.has("ordered_waypoints")){
			JSONArray waypoints = NetworkClient.featureCollectionToList(jsonItinerary.getJSONObject("ordered_waypoints"));
			for (int i = 0; i < waypoints.length(); i++){
				itinerary.waypoints.addElement(new Waypoint(waypoints.getJSONObject(i)));
			}
		}
	}
}
