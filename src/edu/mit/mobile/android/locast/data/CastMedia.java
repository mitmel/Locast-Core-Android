package edu.mit.mobile.android.locast.data;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

public class CastMedia extends JsonSyncableItem {
	public final static String
		_AUTHOR		  = "author",

		_TITLE		  = "title",
		_DESCRIPTION  = "description",
		_LANGUAGE	  = "language",

		_MEDIA_URL    = "url",			// the body of the object
		_LOCAL_URI    = "local_uri",	// any local copy of the main media
		_MIME_TYPE    = "mimetype",		// type of the media
		_DURATION	  = "duration",
		_THUMBNAIL	  = "thumbnail",
		_THUMB_LOCAL  = "local_thumb"   // filename of the local thumbnail
		;
	public final static String PATH = "media";
	public final static String SERVER_PATH = "media/";
	//public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_URI,
		_MODIFIED_DATE,
		_CREATED_DATE,

		_AUTHOR,
		_TITLE,
		_DESCRIPTION,
		_LANGUAGE,

		_MEDIA_URL,
		_LOCAL_URI,
		_MIME_TYPE,
		_DURATION,
		_THUMBNAIL,
		_THUMB_LOCAL,
	};

	public static final String
		MIMETYPE_HTML = "text/html",
		MIMETYPE_3GPP = "video/3gpp",
		MIMETYPE_MPEG4 = "video/mpeg4";

	@Override
	public Uri getContentUri() {
		return null;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	@Override
	public SyncMap getSyncMap() {

		return SYNC_MAP;
	}

	public static Uri getCast(Uri castMediaUri){
		return ProviderUtils.removeLastPathSegments(castMediaUri, 2);
	}

	public final static ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends JsonSyncableItem.ItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = 8477549708016150941L;

		public ItemSyncMap() {
			super();

			put(_TITLE,			new SyncFieldMap("title", 			SyncFieldMap.STRING, SyncFieldMap.FLAG_OPTIONAL));
			put(_DESCRIPTION,	new SyncFieldMap("description", 	SyncFieldMap.STRING, SyncFieldMap.FLAG_OPTIONAL));
			put(_LANGUAGE,		new SyncFieldMap("language", 		SyncFieldMap.STRING));
			put(_AUTHOR, 		new SyncChildField(_AUTHOR, "author", "display_name", SyncFieldMap.STRING));

			put("_resources", new SyncCustom("resources", SyncCustom.SYNC_FROM|SyncCustom.FLAG_OPTIONAL) {

				@Override
				public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
						throws JSONException, NetworkProtocolException, IOException {
					return null;
				}

				@Override
				public ContentValues fromJSON(Context context, Uri localItem,
						JSONObject item, String lProp) throws JSONException,
						NetworkProtocolException, IOException {
					final ContentValues cv = new ContentValues();

					final JSONObject jo = item.getJSONObject(remoteKey);
					if (jo.has("primary")){
						final JSONObject primary = jo.getJSONObject("primary");
						cv.put(_MIME_TYPE, primary.getString("mime_type"));
						cv.put(_MEDIA_URL, primary.getString("url"));
					}
					if (jo.has("screenshot")){
						final JSONObject screenshot = jo.getJSONObject("screenshot");
						cv.put(_THUMBNAIL, screenshot.getString("url"));
					}

					return cv;
				}
			});

			// no MIME type is passed with a link media type, so we need to add one in.
			put("_content_type", new SyncCustom("content_type", SyncItem.SYNC_FROM) {

				@Override
				public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
						throws JSONException, NetworkProtocolException, IOException {
					return null;
				}

				@Override
				public ContentValues fromJSON(Context context, Uri localItem,
						JSONObject item, String lProp) throws JSONException,
						NetworkProtocolException, IOException {
					final ContentValues cv = new ContentValues();
					final String content_type = item.getString(remoteKey);

					if ("linkedmedia".equals(content_type)){
						cv.put(_MIME_TYPE, MIMETYPE_HTML);
					}

					return cv;
				}
			});

			// the media URL can come from either the flattened "url" attribute or the expanded "resources" structure above.
			put(_MEDIA_URL, 	new SyncFieldMap("url", 			SyncFieldMap.STRING,         SyncFieldMap.SYNC_FROM|SyncItem.FLAG_OPTIONAL));
			put(_MIME_TYPE, 	new SyncFieldMap("mime_type",		SyncFieldMap.STRING,         SyncFieldMap.SYNC_FROM|SyncItem.FLAG_OPTIONAL));
			put(_DURATION,		new SyncFieldMap("duration", 		SyncFieldMap.DURATION, 		 SyncItem.FLAG_OPTIONAL));

			put(_THUMBNAIL, 	new SyncFieldMap("preview_image",   SyncFieldMap.STRING,		 SyncFieldMap.SYNC_FROM|SyncItem.FLAG_OPTIONAL));
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri, JSONObject item,
				boolean updated) throws SyncException, IOException {
			super.onPostSyncItem(context, uri, item, updated);
			if (uri != null){
				context.startService(new Intent(MediaSync.ACTION_SYNC_RESOURCES, uri));
			}
		}
	}
}
