package edu.mit.mobile.android.locast.data;

import java.io.IOException;

import org.json.JSONObject;

import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;

public class Itinerary extends TaggableItem {
	public final static String PATH = "itineraries";
	public final static Uri CONTENT_URI = Uri
			.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	public final static String SORT_DEFAULT = _MODIFIED_DATE + " DESC";

	public final static String SERVER_PATH = "itinerary/";

	public static final String
		_PATH = "path",
		_TITLE = "title",
		_DESCRIPTION = "description",
		_CASTS_URI = "casts";

	/**
	 * A through table to allow a many-to-many relation between itineraries and casts.
	 *
	 */
	public static class ItineraryCastsColumns implements BaseColumns {
		public static final String
			_CAST_ID = "cast_id",
			_ITINERARY_ID = "itinerary_id";
	}

	public static final String[] PROJECTION = {
		_ID,
		_AUTHOR,
		_TITLE,
		_DESCRIPTION,
		_PUBLIC_URI,
		_CREATED_DATE,
		_MODIFIED_DATE,
		_PATH,
	};

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends TaggableItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = 6975192764581466901L;


		public ItemSyncMap() {
			super();

			put(_DESCRIPTION, 		new SyncFieldMap("description", SyncFieldMap.STRING));
			put(_TITLE, 			new SyncFieldMap("title", SyncFieldMap.STRING));
			put(_CASTS_URI,			new SyncFieldMap("casts", SyncFieldMap.STRING, SyncFieldMap.SYNC_FROM | SyncFieldMap.FLAG_OPTIONAL));

			putAll(Locatable.SYNC_MAP);
			putAll(Commentable.SYNC_MAP);
			put("_shotlist",   new OrderedList.SyncMapItem("shotlist", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_FROM, new ShotList(), ShotList.PATH));

			remove(_PRIVACY);
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri, JSONObject item, boolean updated) throws SyncException, IOException {
			super.onPostSyncItem(context, uri, item, updated);
			if (updated){
				OrderedList.onUpdate(context, uri, item, "shotlist", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_FROM, new ShotList(), ShotList.PATH);
			}
		}
	}
}
