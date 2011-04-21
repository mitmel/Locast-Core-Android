package edu.mit.mobile.android.locast.data;

import android.net.Uri;

/**
 * A video that makes up part of a segmented cast.
 *
 * @author steve
 *
 */
public class CastVideo extends JsonSyncableItem implements OrderedList.Columns{
	public final static String
		_MEDIA_URL    = "url",
		_LOCAL_URI    = "local_uri",
		_SCREENSHOT   = "screenshot",
		_MIME_TYPE    = "mimetype",
		_DURATION     = "duration",
		_PREVIEW_URL  = "preview_url"
		;
	public final static String PATH = "cast_media";
	public final static String SERVER_PATH = "content/";
	public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_URI,
		_PUBLIC_ID,
		_MODIFIED_DATE,
		_CREATED_DATE,
		_MEDIA_URL,
		_LOCAL_URI,
		_SCREENSHOT,
		_MIME_TYPE,
		_DURATION,
		_PARENT_ID,
		_LIST_IDX
	};

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	@Override
	public SyncMap getSyncMap() {

		return SYNC_MAP;
	}

	public final static ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends JsonSyncableItem.ItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = 8477549708016150941L;

		public ItemSyncMap() {
			super();
			// modify the modified date to make it optional.
			put(_MODIFIED_DATE, new SyncFieldMap("modified", SyncFieldMap.DATE, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));

			put(_SCREENSHOT, 	new SyncFieldMap("screenshot", SyncFieldMap.STRING,SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
			put(_PREVIEW_URL, 	new SyncFieldMap("preview_url", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
			put(_MEDIA_URL, 	new SyncFieldMap("file_url", SyncFieldMap.STRING,         SyncFieldMap.SYNC_FROM));
			put(_MIME_TYPE, 	new SyncFieldMap("mime_type", SyncFieldMap.STRING,        SyncFieldMap.SYNC_FROM));
		}
	}
}
