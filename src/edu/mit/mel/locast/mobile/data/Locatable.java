package edu.mit.mel.locast.mobile.data;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.mit.mel.locast.mobile.net.NetworkProtocolException;


public abstract class Locatable extends UserCreatedItem {
	private Coordinates loc;
	
	public void setLoc(Coordinates loc) {
		this.loc = loc;
	}

	public Coordinates getLoc() {
		return loc;
	}

	/**
	 * Loads a locatable from a GeoJSON Feature object.
	 * 
	 * @param w
	 *            the locatable object to be populated.
	 * @param item
	 *            a GeoJSON Feature object.
	 * @return
	 * @throws JSONException
	 * @throws IOException
	 * @throws NetworkProtocolException
	 */
	public static Locatable fromJSON(Locatable w, JSONObject item)
			throws JSONException, IOException, NetworkProtocolException {
		UserCreatedItem.fromJSON(w, item);
		if (item.has("geometry")){
			w.setLoc(geometryToCoordinates(item.getJSONObject("geometry")));
		}else{
			System.err.println("Warning: locatable missing location");
		}
		return w;
	}
	
	/**
	 * @param loc
	 * @return
	 * @throws JSONException 
	 */
	static public JSONObject coordinatesToGeometry(Coordinates loc) throws JSONException{
		final JSONObject m = new JSONObject();
		
		final JSONArray coords = new JSONArray();
		
		coords.put(new Double(loc.getLongitude()));		
		coords.put(new Double(loc.getLatitude()));
		m.put("coordinates", coords);
		m.put("type", "Point");
		return m;
	}
	/**
	 * Converts a GeoJSON geometry object into a format usable in Java
	 * 
	 * @param geometry
	 * @return 
	 * @throws JSONException
	 */
	static public Coordinates geometryToCoordinates(JSONObject geometry) throws JSONException{
		if (! geometry.getString("type").equals("Point")){
			throw new JSONException("GeoJSON error: Don't know how to convert from a "+ 
					geometry.getString("type") +" to a Coordinates object.");
		}
		final JSONArray coordinates = geometry.getJSONArray("coordinates");
		
		return new Coordinates(
				(float)coordinates.getDouble(1), // lat (note the flipped order)
				(float)coordinates.getDouble(0), // lon
				(float)coordinates.optDouble(2, 0)); // altitude
	}
	
	@Override
	public JSONObject toJSON() throws JSONException{
		final JSONObject js = super.toJSON();
		js.put("geometry", Locatable.coordinatesToGeometry(getLoc()));
		
		return js;
	}
}
