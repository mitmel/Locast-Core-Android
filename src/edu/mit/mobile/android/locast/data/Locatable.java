package edu.mit.mobile.android.locast.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;

import com.beoui.geocell.GeocellUtils;
import com.beoui.geocell.model.Point;

import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncCustom;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;

/**
 * A helper for things that are locatable. Implement Locatable.Columns to use.
 * @author steve
 *
 */
public abstract class Locatable {

	/**
	 * implement this in order to inherit columns needed for becoming locatable.
	 * @author steve
	 *
	 */
	public static interface Columns {
	public static final String
		_LATITUDE = "lat",
		_LONGITUDE = "lon",
		_GEOCELL = "geocell";

	};

	public static final String
		SERVER_QUERY_PARAMETER = "dist";

	public static final String
		SELECTION_LAT_LON = "abs("+Columns._LATITUDE + " - ?) < 1 and abs("+Columns._LONGITUDE + " - ?) < 1";

	public static Uri toGeoUri(Cursor c){
		if (c.isNull(c.getColumnIndex(Columns._LATITUDE)) || c.isNull(c.getColumnIndex(Columns._LONGITUDE))) {
			return null;
		}
		return Uri.parse("geo:"+c.getDouble(c.getColumnIndex(Columns._LATITUDE))+","+c.getDouble(c.getColumnIndex(Columns._LONGITUDE)));
	}

	/**
	 * Makes a URI that queries the locatable item by distance.
	 *
	 * @param contentUri the Locatable content URI to build upon. Must be a dir, not an item.
	 * @param location center point
	 * @param distance distance in meters
	 * @return
	 */
	public static Uri toDistanceSearchUri(Uri contentUri, Location location, double distance){
		return contentUri.buildUpon().appendQueryParameter(SERVER_QUERY_PARAMETER, location.getLongitude()+","+location.getLatitude()+","+distance).build();
	}

	/**
	 * Get the latitude/longitude from the row currently selected in the cursor. Requires Locatable.Columns._LATITUDE and Locatable.Columns._LONGITUDE to be selected.
	 * @param c
	 * @return
	 */
	public static Location toLocation(Cursor c){
		final int lat_idx = c.getColumnIndex(Columns._LATITUDE);
		final int lon_idx = c.getColumnIndex(Columns._LONGITUDE);
		if (c.isNull(lat_idx) || c.isNull(lon_idx)) {
			return null;
		}
		final Location l = new Location("internal");
		l.setLatitude(c.getDouble(lat_idx));
		l.setLongitude(c.getDouble(lon_idx));
		return l;
	}

	/**
	 * Get the latitude/longitude from the row currently selected in the cursor. Requires Locatable.Columns._LATITUDE and Locatable.Columns._LONGITUDE to be selected.
	 * @param c
	 * @return
	 */
	public static Location toLocation(Cursor c, int latColumn, int lonColumn){
		if (c.isNull(latColumn) || c.isNull(lonColumn)) {
			return null;
		}
		final Location l = new Location("internal");
		l.setLatitude(c.getDouble(latColumn));
		l.setLongitude(c.getDouble(lonColumn));
		return l;
	}

	/**
	 * Fills the result array with the current location.
	 *
	 * @param c cursor pointing to row to get location of
	 * @param result output array. Must have 2 or more elements. Latitude is in index 0.
	 */
	public static void toLocationArray(Cursor c, int latColumn, int lonColumn, double[] result){
		if (c.isNull(latColumn) || c.isNull(lonColumn)) {
			return;
		}
		result[0] = c.getDouble(latColumn);
		result[1] = c.getDouble(lonColumn);
	}

	public static final SyncMap SYNC_MAP = new SyncMap();

	static {
		SYNC_MAP.put("_location", new SyncCustom("location", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_BOTH) {

			@Override
			public JSONArray toJSON(Context context, Uri localItem, Cursor c, String lProp)
					throws JSONException {

				final int latCol = c.getColumnIndex(Columns._LATITUDE);
				final int lonCol = c.getColumnIndex(Columns._LONGITUDE);

				if (c.isNull(latCol) || c.isNull(lonCol)){
					return null;
				}

				final JSONArray coords = new JSONArray();
				coords.put(c.getDouble(lonCol));
				coords.put(c.getDouble(latCol));
				return coords;
			}

			@Override
			public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
					throws JSONException {
				final JSONArray ja = item.getJSONArray(remoteKey);
				final ContentValues cv = new ContentValues();
				final double lon = ja.getDouble(0);
				final double lat = ja.getDouble(1);
				cv.put(Columns._LONGITUDE, lon);
				cv.put(Columns._LATITUDE, lat);
				cv.put(Columns._GEOCELL, GeocellUtils.compute(new Point(lat, lon), 6));
				return cv;
			}
		});
	}
}
