package edu.mit.mobile.android.locast.data;
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

import java.io.IOException;
import java.net.URLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mobile.android.content.ForeignKeyDBHelper;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.UriPath;
import edu.mit.mobile.android.content.column.BooleanColumn;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.DBForeignKeyColumn;
import edu.mit.mobile.android.content.column.DatetimeColumn;
import edu.mit.mobile.android.content.column.IntegerColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.sync.MediaSync;
import edu.mit.mobile.android.locast.ver2.R;

@UriPath(CastMedia.PATH)
public class CastMedia extends JsonSyncableItem {
	private static final String TAG = CastMedia.class.getSimpleName();

	@DBColumn(type=TextColumn.class)
	public final static String _AUTHOR = "author";

	@DBColumn(type=TextColumn.class)
	public final static String _AUTHOR_URI = "author_uri";

	@DBColumn(type=TextColumn.class)
	public final static String _TITLE = "title";

	@DBColumn(type=TextColumn.class)
	public final static String _DESCRIPTION = "description";
	
	@DBColumn(type=DatetimeColumn.class)
	public final static String _EXIF_DATETIME = "exif_datetime";

	@DBColumn(type=TextColumn.class)
	public final static String _LANGUAGE = "language";

	@DBColumn(type=TextColumn.class)
	public final static String _MEDIA_URL = "url"; // the body of the object

	@DBColumn(type=TextColumn.class)
	public final static String _LOCAL_URI = "local_uri"; // any local copy of the main media

	@DBColumn(type=TextColumn.class)
	public final static String _MIME_TYPE = "mimetype"; // type of the media

	@DBColumn(type=IntegerColumn.class)
	public final static String _DURATION = "duration";

	@DBColumn(type=TextColumn.class)
	public final static String _THUMBNAIL = "thumbnail";

	@DBColumn(type=BooleanColumn.class)
	public final static String _KEEP_OFFLINE = "offline";

	@DBColumn(type=TextColumn.class)
	public final static String _THUMB_LOCAL = "local_thumb"; // filename of the local thumbnail

	@DBForeignKeyColumn(Cast.class)
	public final static String CAST = "cast_id";

	public final static String PATH = "media";
	public final static String CASTS_CASTMEDIA_PATH = Cast.PATH + "/"
			+ ForeignKeyDBHelper.WILDCARD_PATH_SEGMENT + "/" + PATH;
	public final static String SERVER_PATH = "media/";

	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_URI,
		_MODIFIED_DATE,
		_CREATED_DATE,

		_AUTHOR,
		_AUTHOR_URI,
		_TITLE,
		_DESCRIPTION,
		_LANGUAGE,

		_MEDIA_URL,
		_LOCAL_URI,
		_EXIF_DATETIME,
		_MIME_TYPE,
		_DURATION,
		_THUMBNAIL,
		_THUMB_LOCAL,
		_KEEP_OFFLINE
	};

	public static final String
		MIMETYPE_HTML = "text/html",
		MIMETYPE_3GPP = "video/3gpp",
		MIMETYPE_MPEG4 = "video/mpeg4";

	public static final Uri CONTENT_URI = ProviderUtils.toContentUri(MediaProvider.AUTHORITY,
			CASTS_CASTMEDIA_PATH);

	public CastMedia(Cursor c) {
		super(c);
	}

	@Override
	public Uri getCanonicalUri() {
		return ProviderUtils.toContentUri(MediaProvider.AUTHORITY, Cast.PATH + "/"
				+ getLong(getColumnIndex(CAST)) + "/" + PATH + "/" + getLong(getColumnIndex(_ID)));
	}

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public SyncMap getSyncMap() {

		return SYNC_MAP;
	}

	public static Uri getCast(Uri castMediaUri){
		return ProviderUtils.removeLastPathSegments(castMediaUri, 2);
	}

	public static Uri getMedia(Cursor c, int mediaCol, int mediaLocalCol){
		Uri media;
		if (! c.isNull(mediaLocalCol)){
			media = Uri.parse(c.getString(mediaLocalCol));
		}else if (! c.isNull(mediaCol)){
			media = Uri.parse(c.getString(mediaCol));
		}else{
			media = null;
		}
		return media;
	}

	public static void showMedia(Context context, Cursor c, Uri castMediaUri){
		final String mediaString = c.getString(c
				.getColumnIndex(CastMedia._MEDIA_URL));
		final String locMediaString = c.getString(c
				.getColumnIndex(CastMedia._LOCAL_URI));
		String mimeType = null;

		Uri media;

		if (locMediaString != null) {
			media = Uri.parse(locMediaString);
			if ("file".equals(media.getScheme())) {
				mimeType = c.getString(c.getColumnIndex(CastMedia._MIME_TYPE));
			}

		} else if (mediaString != null) {
			media = Uri.parse(mediaString);
			mimeType = c.getString(c.getColumnIndex(CastMedia._MIME_TYPE));

			// we strip this because we don't really want to force them to go to the browser.
			if ("text/html".equals(mimeType)){
				mimeType = null;
			}
		} else {
			Log.e(TAG, "asked to show media for "+ castMediaUri + " but there was nothing to show");
			return;
		}

		final Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(media, mimeType);

		if (mimeType != null && mimeType.startsWith("video/")){
			context.startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(castMediaUri, c.getLong(c.getColumnIndex(CastMedia._ID)))));
		}else{
			// setting the MIME type for URLs doesn't work.
			try {
				context.startActivity(i);
			}catch (final ActivityNotFoundException e){
				// try it again, but without a mime type.
				if (mimeType != null){
					i.setDataAndType(media, null);
				}
				try {
					context.startActivity(i);
				}catch (final ActivityNotFoundException e2){
					Toast.makeText(context, R.string.error_cast_media_no_activities, Toast.LENGTH_LONG).show();
				}
			}
		}
	}

	public static Uri getThumbnail(Cursor c, int thumbCol, int thumbLocalCol){
		Uri thumbnail;
		if (! c.isNull(thumbLocalCol)){
			thumbnail = Uri.parse(c.getString(thumbLocalCol));
		}else if (! c.isNull(thumbCol)){
			thumbnail = Uri.parse(c.getString(thumbCol));
		}else{
			thumbnail = null;
		}
		return thumbnail;
	}

	/**
	 * Guesses the mime type from the URL
	 *
	 * @param url
	 * @return the inferred mime type based on the file extension or null if it can't determine one
	 */
	public static String guessMimeTypeFromUrl(String url){

		// this was improved in Gingerbread
		// http://code.google.com/p/android/issues/detail?id=10100
		// so we have some pre-defined types here so we can make sure to return SOMETHING.
		String mimeType = URLConnection.guessContentTypeFromName(url);
		if (mimeType != null){
			return mimeType;
		}

		if (url.endsWith(".jpg") || url.endsWith(".jpeg")){
			mimeType = "image/jpeg";

		}else if (url.endsWith(".3gp")){
			mimeType = "video/3gpp";

		}else if (url.endsWith(".mp4") || url.endsWith(".mpeg4")){
			mimeType = "video/mp4";

		}else if (url.endsWith(".png")){
			mimeType = "image/png";
		}

		return mimeType;
	}

	public final static ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends JsonSyncableItem.ItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = 8477549708016150941L;

		public ItemSyncMap() {
			super();

			this.addFlag(FLAG_PARENT_MUST_SYNC_FIRST);

			put(_TITLE,			new SyncFieldMap("title", 			SyncFieldMap.STRING, SyncFieldMap.FLAG_OPTIONAL));
			put(_DESCRIPTION,	new SyncFieldMap("description", 	SyncFieldMap.STRING, SyncFieldMap.FLAG_OPTIONAL));
			put(_LANGUAGE,		new SyncFieldMap("language", 		SyncFieldMap.STRING));
			put(_AUTHOR, 		new SyncChildField(_AUTHOR, "author", "display_name", SyncFieldMap.STRING, SyncFieldMap.FLAG_OPTIONAL));
			put(_AUTHOR_URI,	new SyncChildField(_AUTHOR_URI, "author", "uri", SyncFieldMap.STRING, SyncFieldMap.FLAG_OPTIONAL));

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
					if (jo.has("medium")){
						final JSONObject screenshot = jo.getJSONObject("medium");
						cv.put(_THUMBNAIL, screenshot.getString("url"));

					}else if (jo.has("screenshot")){
						final JSONObject screenshot = jo.getJSONObject("screenshot");
						cv.put(_THUMBNAIL, screenshot.getString("url"));
					}

					return cv;
				}
			});

			// no MIME type is passed with a link media type, so we need to add one in.
			put("_content_type", new SyncCustom("content_type", SyncItem.SYNC_BOTH) {

				@Override
				public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
						throws JSONException, NetworkProtocolException, IOException {
					String mimeType = c.getString(c.getColumnIndex(_MIME_TYPE));
					final String localUri = c.getString(c.getColumnIndex(_LOCAL_URI));

					if (mimeType == null && localUri != null){

						mimeType = guessMimeTypeFromUrl(localUri);
						if (mimeType != null){
							Log.d(TAG, "guessed MIME type from uri: " + localUri + ": " + mimeType);
						}
					}

					if (mimeType == null){
						return null;
					}

					if (mimeType.startsWith("video/")){
						return "videomedia";
					}else if (mimeType.startsWith("image/")){
						return "imagemedia";
					}else{
						return null;
					}
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
			put(_MIME_TYPE, 	new SyncFieldMap("mime_type",		SyncFieldMap.STRING,         SyncItem.FLAG_OPTIONAL));
			put(_DURATION,		new SyncFieldMap("duration", 		SyncFieldMap.DURATION, 		 SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
			put(_THUMBNAIL, 	new SyncFieldMap("preview_image",   SyncFieldMap.STRING,		 SyncFieldMap.SYNC_FROM|SyncItem.FLAG_OPTIONAL));
			put(_EXIF_DATETIME, new SyncFieldMap("exif_datetime",	SyncFieldMap.DATE,			SyncFieldMap.SYNC_FROM|SyncItem.FLAG_OPTIONAL));
		}

		@Override
		public void onPostSyncItem(Context context, Account account, Uri uri,
				JSONObject item, boolean updated) throws SyncException, IOException {
			super.onPostSyncItem(context, account, uri, item, updated);
			if (uri != null){
				Log.d(TAG, "Starting media sync for " + uri);
				context.startService(new Intent(MediaSync.ACTION_SYNC_RESOURCES, uri));
			}
		}
	}
}
