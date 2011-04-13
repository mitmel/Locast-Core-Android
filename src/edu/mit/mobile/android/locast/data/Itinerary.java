package edu.mit.mobile.android.locast.data;

import java.io.IOException;

import org.json.JSONObject;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;

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

	public static final String[] PROJECTION = {
		_ID,
		_AUTHOR,
		_TITLE,
		_DESCRIPTION,
		_PUBLIC_URI,
		_CREATED_DATE,
		_MODIFIED_DATE,
		_PATH,
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
