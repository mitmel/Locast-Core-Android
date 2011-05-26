package edu.mit.mobile.android.locast.maps;

import android.database.Cursor;

import com.google.android.maps.GeoPoint;

import edu.mit.mobile.android.locast.data.Locatable;

public class MapsUtils {

	public static GeoPoint getGeoPoint(Cursor item){
		return getGeoPoint(item, item.getColumnIndex(Locatable.Columns._LATITUDE), item.getColumnIndex(Locatable.Columns._LONGITUDE));
	}

	public static GeoPoint getGeoPoint(Cursor item, int latColumn, int lonColumn){
		final double[] result = new double[2];
		Locatable.toLocationArray(item, latColumn, lonColumn, result);
		final GeoPoint gp = new GeoPoint((int)(result[0]  * 1E6), (int)(result[1] * 1E6));
		return gp;
	}
}
