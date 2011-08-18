package edu.mit.mobile.android.locast.maps;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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

import org.osmdroid.util.GeoPoint;

import android.database.Cursor;
import edu.mit.mobile.android.locast.data.Locatable;

public class MapsUtils {

	public static GeoPoint getGeoPoint(Cursor item){
		return getGeoPoint(item, item.getColumnIndexOrThrow(Locatable.Columns._LATITUDE), item.getColumnIndexOrThrow(Locatable.Columns._LONGITUDE));
	}

	public static GeoPoint getGeoPoint(Cursor item, int latColumn, int lonColumn){
		final double[] result = new double[2];
		Locatable.toLocationArray(item, latColumn, lonColumn, result);
		final GeoPoint gp = new GeoPoint(result[0], result[1]);
		return gp;
	}
}
