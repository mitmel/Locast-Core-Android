package edu.mit.mobile.android.locast.data;

import android.net.Uri;

public class CastMedia extends JsonSyncableItem {
	public final static String
		_MEDIA_URL    = "url",
		_LOCAL_URI    = "local_uri",
		_MIME_TYPE    = "mimetype",
		_DURATION     = "duration",
		_AUTHOR		  = "author",
		_LANGUAGE	  = "language",
		_THUMBNAIL	  = "thumbnail"
		;
	public final static String PATH = "media";
	public final static String SERVER_PATH = "media/";
	public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_URI,
		_MODIFIED_DATE,
		_CREATED_DATE,

		_MEDIA_URL,
		_LANGUAGE,
		_LOCAL_URI,
		_THUMBNAIL,
		_MIME_TYPE,
		_AUTHOR,
		_DURATION
	};

	public static final String MIMETYPE_HTML = "text/html";

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
			put(_THUMBNAIL, 	new SyncFieldMap("preview_image", 	SyncFieldMap.STRING,SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
			put(_MEDIA_URL, 	new SyncFieldMap("file", 			SyncFieldMap.STRING,         SyncFieldMap.SYNC_FROM));
			put(_MIME_TYPE, 	new SyncFieldMap("mime_type", 		SyncFieldMap.STRING,        SyncFieldMap.SYNC_FROM));
			put(_LANGUAGE,		new SyncFieldMap("language", 		SyncFieldMap.STRING));

			put(_AUTHOR, 		new SyncChildField("author", "display_name", SyncFieldMap.STRING));
			//put(_AUTHOR,		new SyncChildRelation("author", new SyncChildRelation.SimpleRelationship("author"), SyncItem.SYNC_FROM));

		}
	}
}
