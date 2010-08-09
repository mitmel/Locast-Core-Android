package edu.mit.mel.locast.mobile.data;
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
import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncCustom;
import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncItem;

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
		_LONGITUDE = "lon";

	};

	public static final String
		SELECTION_LAT_LON = Columns._LATITUDE + " - ? < 1 and "+Columns._LONGITUDE + " - ? < 1";

	public static Uri toGeoUri(Cursor c){
		if (c.isNull(c.getColumnIndex(Columns._LATITUDE)) || c.isNull(c.getColumnIndex(Columns._LONGITUDE))) {
			return null;
		}
		return Uri.parse("geo:"+c.getDouble(c.getColumnIndex(Columns._LATITUDE))+","+c.getDouble(c.getColumnIndex(Columns._LONGITUDE)));
	}

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
				cv.put(Columns._LONGITUDE, ja.getDouble(0));
				cv.put(Columns._LATITUDE, ja.getDouble(1));
				return cv;
			}
		});
	}
}
