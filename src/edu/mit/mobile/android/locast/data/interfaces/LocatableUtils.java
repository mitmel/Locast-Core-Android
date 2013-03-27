package edu.mit.mobile.android.locast.data.interfaces;

/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.location.Location;
import android.net.Uri;

import com.beoui.geocell.GeocellUtils;
import com.beoui.geocell.model.Point;
import com.google.android.maps.GeoPoint;

import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncCustom;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;
import edu.mit.mobile.android.locast.data.SyncMap;

/**
 * A helper for things that are locatable. Implement {@link Locatable} to use.
 *
 * @author steve
 *
 */
public class LocatableUtils {

    public static final String SERVER_QUERY_PARAMETER = "dist";

    public static final String SELECTION_LAT_LON = "abs(" + Locatable.COL_LATITUDE
            + " - ?) < 1 and abs(" + Locatable.COL_LONGITUDE + " - ?) < 1";

    public static Uri toGeoUri(Cursor c) {
        if (c.isNull(c.getColumnIndex(Locatable.COL_LATITUDE))
                || c.isNull(c.getColumnIndex(Locatable.COL_LONGITUDE))) {
            return null;
        }
        return Uri.parse("geo:" + c.getDouble(c.getColumnIndex(Locatable.COL_LATITUDE)) + ","
                + c.getDouble(c.getColumnIndex(Locatable.COL_LONGITUDE)));
    }

    /**
     * Makes a URI that queries the locatable item by distance.
     *
     * @param contentUri
     *            the LocatableUtils content URI to build upon. Must be a dir, not an item.
     * @param location
     *            center point
     * @param distance
     *            distance in meters
     * @return
     */
    public static Uri toDistanceSearchUri(Uri contentUri, Location location, double distance) {
        return contentUri
                .buildUpon()
                .appendQueryParameter(SERVER_QUERY_PARAMETER,
                        location.getLongitude() + "," + location.getLatitude() + "," + distance)
                .build();
    }

    /**
     * Makes a URI that queries the locatable item by distance.
     *
     * @param contentUri
     *            the GeoPoint content URI to build upon. Must be a dir, not an item.
     * @param location
     *            center point
     * @param distance
     *            distance in meters
     * @return
     */
    public static Uri toDistanceSearchUri(Uri contentUri, GeoPoint location, double distance) {
        return contentUri
                .buildUpon()
                .appendQueryParameter(
                        SERVER_QUERY_PARAMETER,
                        location.getLongitudeE6() / 1E6f + "," + location.getLatitudeE6() / 1E6f
                                + "," + distance).build();
    }

    /**
     * Get the latitude/longitude from the row currently selected in the cursor. Requires
     * LocatableUtils.Locatable._LATITUDE and LocatableUtils.Locatable._LONGITUDE to be selected.
     *
     * @param c
     * @return
     */
    public static Location toLocation(Cursor c) {
        final int lat_idx = c.getColumnIndex(Locatable.COL_LATITUDE);
        final int lon_idx = c.getColumnIndex(Locatable.COL_LONGITUDE);
        if (c.isNull(lat_idx) || c.isNull(lon_idx)) {
            return null;
        }
        final Location l = new Location("internal");
        l.setLatitude(c.getDouble(lat_idx));
        l.setLongitude(c.getDouble(lon_idx));
        return l;
    }

    private static final Pattern LOC_STRING_REGEX = Pattern
            .compile("^([\\d\\.-]+),([\\d\\.-]+)(?:,([\\d\\.]+))?");

    // TODO finish this query. Currently ignores distance parameter
    public Cursor queryByLocation(SQLiteQueryBuilder qb, SQLiteDatabase db, String locString,
            String locatableItemTable, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        qb.setTables(locatableItemTable);
        final Matcher m = LOC_STRING_REGEX.matcher(locString);
        if (!m.matches()) {
            throw new IllegalArgumentException("bad location string '" + locString + "'");
        }
        final String lon = m.group(1);
        final String lat = m.group(2);
        String dist = "1500";
        if (m.groupCount() == 3) {
            dist = m.group(3);
        }

        final String cell = GeocellUtils.compute(
                new Point(Double.valueOf(lat), Double.valueOf(lon)), 8);

        final List<String> adjacent = GeocellUtils.allAdjacents(cell);

        adjacent.add(cell);

        final StringBuilder selSb = new StringBuilder();

        boolean join = false;
        for (int i = 0; i < adjacent.size(); i++) {
            if (join) {
                selSb.append(" OR ");
            } else {
                join = true;
            }

            selSb.append(Locatable.COL_GEOCELL);
            selSb.append(" LIKE ? || '%'");

        }

        final String selectionExtra = selSb.toString();

        return qb.query(db, projection, ProviderUtils.addExtraWhere(selection, selectionExtra),
                ProviderUtils.addExtraWhereArgs(selectionArgs, adjacent.toArray(new String[] {})),
                null, null, sortOrder);

        // String extraWhere =
        // "(lat - 2) > ? AND (lon - 2) > ? AND (lat + 2) < ? AND (lat + 2) < ?";

        // final String[] extraArgs = {lat, lon};
        // return qb.query(db, projection, ProviderUtils.addExtraWhere(selection,
        // LocatableUtils.SELECTION_LAT_LON), ProviderUtils.addExtraWhereArgs(selectionArgs,
        // extraArgs),
        // null, null, sortOrder);
    }

    /**
     * Get the latitude/longitude from the row currently selected in the cursor. Requires
     * LocatableUtils.Locatable._LATITUDE and LocatableUtils.Locatable._LONGITUDE to be selected.
     *
     * @param c
     * @return
     */
    public static Location toLocation(Cursor c, int latColumn, int lonColumn) {
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
     * @param c
     *            cursor pointing to row to get location of
     * @param result
     *            output array. Must have 2 or more elements. Latitude is in index 0.
     */
    public static void toLocationArray(Cursor c, int latColumn, int lonColumn, double[] result) {
        if (c.isNull(latColumn) || c.isNull(lonColumn)) {
            return;
        }
        result[0] = c.getDouble(latColumn);
        result[1] = c.getDouble(lonColumn);
    }

    /**
     * Adds the appropriate {@link Authorable#COL_LATITUDE}, {@link Authorable#COL_LONGITUDE}
     * columns to the given {@link ContentValues} for the given location.
     *
     * @param cv
     * @param location
     * @return the same {@link ContentValues} that was passed in.
     */
    public static ContentValues toContentValues(ContentValues cv, GeoPoint location) {
        cv.put(Locatable.COL_LATITUDE, location.getLatitudeE6() / 1E6d);
        cv.put(Locatable.COL_LONGITUDE, location.getLongitudeE6() / 1E6d);

        return cv;
    }

    public static final SyncMap SYNC_MAP = new LocatableSyncMap();

    public static class LocatableSyncMap extends SyncMap {
        /**
         *
         */
        private static final long serialVersionUID = 6568225507008227781L;

        public LocatableSyncMap() {
            put("_location",
                    new SyncCustom("location", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_BOTH) {

                        @Override
                        public JSONArray toJSON(Context context, Uri localItem, Cursor c,
                                String lProp) throws JSONException {

                            final int latCol = c.getColumnIndex(Locatable.COL_LATITUDE);
                            final int lonCol = c.getColumnIndex(Locatable.COL_LONGITUDE);

                            if (c.isNull(latCol) || c.isNull(lonCol)) {
                                return null;
                            }

                            final JSONArray coords = new JSONArray();
                            coords.put(c.getDouble(lonCol));
                            coords.put(c.getDouble(latCol));
                            return coords;
                        }

                        @Override
                        public ContentValues fromJSON(Context context, Uri localItem,
                                JSONObject item, String lProp) throws JSONException {
                            final JSONArray ja = item.getJSONArray(remoteKey);
                            final ContentValues cv = new ContentValues();
                            final double lon = ja.getDouble(0);
                            final double lat = ja.getDouble(1);
                            cv.put(Locatable.COL_LONGITUDE, lon);
                            cv.put(Locatable.COL_LATITUDE, lat);
                            cv.put(Locatable.COL_GEOCELL,
                                    GeocellUtils.compute(new Point(lat, lon), 13));
                            return cv;
                        }
                    });
        }
    }
}
