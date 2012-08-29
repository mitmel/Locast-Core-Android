package edu.mit.mobile.android.locast.maps;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
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

import android.database.Cursor;

import com.google.android.maps.GeoPoint;

import edu.mit.mobile.android.locast.data.Locatable;

public class MapsUtils {

    public static GeoPoint getGeoPoint(Cursor item){
        return getGeoPoint(item, item.getColumnIndexOrThrow(Locatable.Columns._LATITUDE), item.getColumnIndexOrThrow(Locatable.Columns._LONGITUDE));
    }

    public static GeoPoint getGeoPoint(Cursor item, int latColumn, int lonColumn){
        final double[] result = new double[2];
        Locatable.toLocationArray(item, latColumn, lonColumn, result);
        final GeoPoint gp = new GeoPoint((int)(result[0]  * 1E6), (int)(result[1] * 1E6));
        return gp;
    }
}
