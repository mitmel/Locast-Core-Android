package edu.mit.mel.locast.mobile.data;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.mit.mel.locast.mobile.net.NetworkProtocolException;

public interface JSONSerializable {
	public JSONObject toJSON() throws JSONException;
	public void fromJSON(JSONObject item) throws JSONException, IOException,
			NetworkProtocolException;
}
