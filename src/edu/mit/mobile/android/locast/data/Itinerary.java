package edu.mit.mobile.android.locast.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.google.android.maps.GeoPoint;

import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.utils.ListUtils;

public class Itinerary extends TaggableItem implements Favoritable.Columns {
	public final static String PATH = "itineraries";
	public final static Uri CONTENT_URI = Uri
			.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	public final static String SORT_DEFAULT = _MODIFIED_DATE + " DESC";

	public final static String SERVER_PATH = "itinerary/";

	public static final String
		_PATH = "path",
		_TITLE = "title",
		_DESCRIPTION = "description",
		_CASTS_URI = "casts",
		_CASTS_COUNT = "casts_count",
		_FAVORITES_COUNT = "favorites_count",
		_THUMBNAIL = "thumbnail";

	public static final String[] PROJECTION = {
		_ID,
		_AUTHOR,
		_TITLE,
		_DESCRIPTION,
		_PUBLIC_URI,
		_CREATED_DATE,
		_MODIFIED_DATE,
		_PATH,
		_THUMBNAIL,
		_DRAFT,
	};

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	public static Uri getCastsUri(Uri itinerary){
		if (ContentUris.parseId(itinerary) == -1){
			throw new IllegalArgumentException(itinerary + " does not appear to be an itinerary item URI");
		}
		return Uri.withAppendedPath(itinerary, Cast.PATH);
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	public static List<GeoPoint> getPath(Cursor c){
		final ArrayList<GeoPoint> path = new ArrayList<GeoPoint>();
		final String encodedPath = c.getString(c.getColumnIndex(_PATH));
		if (encodedPath == null){
			return new ArrayList<GeoPoint>();
		}

		int prevI = 0;

		int i = 0;
		while(i != -1){
			i = encodedPath.indexOf(',', i+1);
			// handle the case for empty paths
			if (i == -1){
				break;
			}
			final int lat = Integer.parseInt(encodedPath.substring(prevI, i));
			prevI = i+1;
			i = encodedPath.indexOf(',', prevI);

			int end = i;
			if (i == -1){
				end = encodedPath.length();
			}
			final int lon = Integer.parseInt(encodedPath.substring(prevI, end));
			path.add(new GeoPoint(lat, lon));
			prevI = i+1;
		}
		return path;
	}

	public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends TaggableItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = 6975192764581466901L;


		public ItemSyncMap() {
			super();

			putAll(Favoritable.SYNC_MAP);

			put(_DESCRIPTION, 		new SyncFieldMap("description", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL));
			put(_TITLE, 			new SyncFieldMap("title", SyncFieldMap.STRING));
			put(_THUMBNAIL, 		new SyncFieldMap("preview_image",   SyncFieldMap.STRING,		 SyncFieldMap.SYNC_FROM|SyncItem.FLAG_OPTIONAL));
			put(_CASTS_COUNT,      	new SyncFieldMap("casts_count", SyncFieldMap.INTEGER, SyncFieldMap.SYNC_FROM));
			put(_FAVORITES_COUNT,   new SyncFieldMap("favorites", SyncFieldMap.INTEGER, SyncFieldMap.SYNC_FROM));
			put(_CASTS_URI,			new SyncChildRelation("casts", new SyncChildRelation.SimpleRelationship("casts"), false, SyncFieldMap.SYNC_FROM | SyncFieldMap.FLAG_OPTIONAL));
			put(_PATH,				new SyncCustom("path", SyncFieldMap.SYNC_FROM) {

				@Override
				public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
						throws JSONException, NetworkProtocolException, IOException {
					return null;
				}

				@Override
				public ContentValues fromJSON(Context context, Uri localItem,
						JSONObject item, String lProp) throws JSONException,
						NetworkProtocolException, IOException {
					final JSONArray jsonPath = item.getJSONArray(remoteKey);
					final String[] path = new String[jsonPath.length() * 2];

					// TODO loads it all into memory. May make more sense to use a StringBuilder
					final int len = jsonPath.length();
					for (int i = 0; i < len; i++){
						final JSONArray point = jsonPath.getJSONArray(i);
						// stored in [lon,lat] form. Internally, we use lat,lon.
						path[i*2] = String.valueOf((long)(point.getDouble(1) * 1E6));
						path[i*2+1] = String.valueOf((long)(point.getDouble(0) * 1E6));
					}
					final ContentValues cv = new ContentValues();

					cv.put(lProp, ListUtils.join(Arrays.asList(path), ","));

					return cv;

				}
			});

			remove(_PRIVACY);
		}
	}

	public Uri getChildDirUri(Uri parent, String relation) {
		if("casts".equals(relation)){
			return getCastsUri(parent);
		}
		return null;
	}
}
