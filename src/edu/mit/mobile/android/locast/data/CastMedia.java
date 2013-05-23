package edu.mit.mobile.android.locast.data;

/*
 * Copyright (C) 2011-2013  MIT Mobile Experience Lab
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

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.column.BooleanColumn;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.DatetimeColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.sync.AbsMediaSync;

public abstract class CastMedia extends JsonSyncableItem {
	private static final String TAG = CastMedia.class.getSimpleName();

	/**
	 * A textual caption for the media. Optional.
	 */
	@DBColumn(type = TextColumn.class)
	public final static String COL_CAPTION = "caption";

	@DBColumn(type = DatetimeColumn.class)
	public final static String COL_CAPTURE_TIME = "capture_time";

	/**
	 * The main content. This is a public URL.
	 */
	@DBColumn(type = TextColumn.class)
	public final static String COL_MEDIA_URL = "media_url";

	/**
	 * The main content. This links to an optional local copy of the media.
	 */
	@DBColumn(type = TextColumn.class)
	public final static String COL_LOCAL_URL = "local_url";

	/**
	 * The content type of the media.
	 */
	@DBColumn(type = TextColumn.class)
	public final static String COL_MIME_TYPE = "mimetype";

	/**
	 * Whether or not to keep an offline copy. If set to true, the media sync engine will download a
	 * copy.
	 */
	@DBColumn(type = BooleanColumn.class)
	public final static String COL_KEEP_OFFLINE = "offline";

	// TODO move this to ImageContent, etc.
	@DBColumn(type = TextColumn.class)
	public final static String COL_THUMB_LOCAL = "local_thumb"; // filename of the local thumbnail

	/**
	 * If true, the media has been changed locally and should be re-uploaded.
	 */
	@DBColumn(type = BooleanColumn.class, defaultValueInt = 0)
	public final static String COL_MEDIA_DIRTY = "media_dirty";

	//@formatter:off
    public static final String
        MIMETYPE_HTML = "text/html",
        MIMETYPE_3GPP = "video/3gpp",
        MIMETYPE_MPEG4 = "video/mpeg4";
    //@formatter:on

	public CastMedia(Cursor c) {
		super(c);
	}

	public Uri getMedia(int mediaCol, int mediaLocalCol) {
		Uri media;
		if (!isNull(mediaLocalCol)) {
			media = Uri.parse(getString(mediaLocalCol));
		} else if (!isNull(mediaCol)) {
			media = Uri.parse(getString(mediaCol));
		} else {
			media = null;
		}
		return media;
	}

	/**
	 * Given a cursor pointing to a cast media item, attempt to resolve an intent that will show it.
	 *
	 * @param context
	 * @param c
	 *            a cast media cursor pointing to the desired item
	 * @param castMediaDir
	 * @return an intent that will show the media or null if there are none found
	 */
	public static Intent showMedia(Context context, Cursor c, Uri castMediaDir) {
		final String mediaString = c.getString(c.getColumnIndex(CastMedia.COL_MEDIA_URL));
		final String locMediaString = c.getString(c.getColumnIndex(CastMedia.COL_LOCAL_URL));
		String mimeType = null;

		Uri media;

		if (locMediaString != null) {
			media = Uri.parse(locMediaString);
			if ("file".equals(media.getScheme())) {
				mimeType = c.getString(c.getColumnIndex(CastMedia.COL_MIME_TYPE));
			}

		} else if (mediaString != null) {
			media = Uri.parse(mediaString);
			mimeType = c.getString(c.getColumnIndex(CastMedia.COL_MIME_TYPE));

			// we strip this because we don't really want to force them to go to the browser.
			if ("text/html".equals(mimeType)) {
				mimeType = null;
			}
		} else {
			Log.e(TAG, "asked to show media for " + castMediaDir + " but there was nothing to show");
			return null;
		}

		final Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(media, mimeType);

		final PackageManager pm = context.getPackageManager();

		// if we are set up to play back the content locally and it's a video, do such...
		if (mimeType != null && mimeType.startsWith("video/")) {
			final Intent localPlayback = new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(
					castMediaDir, c.getLong(c.getColumnIndex(CastMedia._ID))));

			if (localPlayback.resolveActivity(pm) != null) {
				return localPlayback;
			}
		}

		// setting the MIME type for URLs doesn't work.
		if (i.resolveActivity(pm) != null) {
			return i;
		}

		// try it again, but without a mime type.
		if (mimeType != null) {
			i.setDataAndType(media, null);
		}

		if (i.resolveActivity(pm) != null) {
			return i;
		}

		// Oh bother, nothing worked.
		return null;

	}

	/**
	 * Adds the given media to a castMedia uri.
	 *
	 * If the media is a content:// uri, queries the content resolver to get the needed information.
	 *
	 * @param context
	 * @param castMedia
	 *            uri of the cast media dir to insert into
	 * @param content
	 *            uri of the media to add
	 * @return a summary of the information discovered from the content uri
	 * @throws MediaProcessingException
	 */
	public static CastMediaInfo addMedia(Context context, Account me, Uri castMedia, Uri content,
			ContentValues cv) throws MediaProcessingException {

		final ContentResolver cr = context.getContentResolver();

		final CastMediaInfo castMediaInfo = toMediaInfo(context, content, cv);

		if (Constants.DEBUG) {
			Log.d(TAG, "addMedia(" + castMedia + ", " + cv + ")");
		}

		cv.put(COL_MEDIA_DIRTY, true);

		castMediaInfo.castMediaItem = cr.insert(castMedia, cv);

		return castMediaInfo;
	}

	/**
	 * <p>
	 * Updates the cast media item to use a new media item.
	 * </p>
	 *
	 * <p>
	 * If the media is a content:// uri, queries the content resolver to get the needed information.
	 * </p>
	 *
	 * @param context
	 * @param castMediaItem
	 *            uri of the castMedia item to update
	 * @param content
	 *            uri of the local media file to update
	 * @return a summary of the information discovered from the content uri
	 * @throws MediaProcessingException
	 */
	public static CastMediaInfo updateMedia(Context context, Account me, Uri castMediaItem,
			Uri content, ContentValues cv) throws MediaProcessingException {

		final ContentResolver cr = context.getContentResolver();

		final CastMediaInfo castMediaInfo = toMediaInfo(context, content, cv);
		castMediaInfo.castMediaItem = castMediaItem;

		if (Constants.DEBUG) {
			Log.d(TAG, "addMedia(" + castMediaItem + ", " + cv + ")");
		}

		cv.put(COL_MEDIA_DIRTY, true);

		final int updates = cr.update(castMediaItem, cv, null, null);

		return castMediaInfo;
	}

	/**
	 * Turns the given content into a new or updated cast media.
	 *
	 * If the media is a content:// uri, queries the content resolver to get the needed information.
	 *
	 * @param context
	 * @param castMedia
	 *            uri of the cast media dir to insert into
	 * @param content
	 *            uri of the media to add
	 * @return a summary of the information discovered from the content uri
	 * @throws MediaProcessingException
	 */
	protected static CastMediaInfo toMediaInfo(Context context, Uri content, ContentValues cv)
			throws MediaProcessingException {

		final ContentResolver cr = context.getContentResolver();

		final long now = System.currentTimeMillis();

		final String mimeType = getContentType(cr, content);
		cv.put(CastMedia.COL_MIME_TYPE, mimeType);

		String mediaPath;

		// for media that use content URIs, we need to query the content provider to look up all the
		// details

		final CastMediaInfo castMediaInfo = new CastMediaInfo();

		if ("content".equals(content.getScheme())) {
			mediaPath = extractContent(cr, content, cv, castMediaInfo);

		} else if ("file".equals(content.getScheme())) {
			final String path = content.getPath();
			if (!new File(path).exists()) {
				throw new MediaProcessingException("specified media file does not exist: "
						+ content);
			}
			mediaPath = content.toString();

			if ("image/jpeg".equals(mimeType)) {
				try {
					String exifDateTime = "";
					final ExifInterface exif = new ExifInterface(path);
					exifDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
					if (exifDateTime != null && exifDateTime.length() > 0) {
						cv.put(CastMedia.COL_CAPTURE_TIME, exifDateTime);
					}

				} catch (final IOException ioex) {
					Log.e(TAG, "EXIF: Couldn't find media: " + path);
				}
			}
		} else {
			throw new MediaProcessingException("Don't know how to process URI scheme "
					+ content.getScheme() + " for " + content);
		}

		// ensure that there's always a capture time, even if it's faked.
		if (!cv.containsKey(CastMedia.COL_CAPTURE_TIME)) {
			Log.w(TAG, "faking capture time with current time");
			cv.put(CastMedia.COL_CAPTURE_TIME, now);
		}

		// if it's an image, we can use the source file for the thumbnail.
		if (mimeType.startsWith("image/")) {
			cv.put(CastMedia.COL_THUMB_LOCAL, mediaPath);
		}
		cv.put(CastMedia.COL_LOCAL_URL, mediaPath);

		return castMediaInfo;

	}

	private static String extractContent(ContentResolver cr, Uri content, ContentValues cv,
			CastMediaInfo castMediaInfo) {
		String mediaPath = null;

		final Cursor c = cr.query(content, new String[] { MediaColumns._ID, MediaColumns.DATA,
				MediaColumns.TITLE, Media.LATITUDE, Media.LONGITUDE }, null, null, null);
		try {
			if (c.moveToFirst()) {
				final String title = c.getString(c.getColumnIndexOrThrow(MediaColumns.TITLE));
				cv.put(CastMedia.COL_CAPTION, title);
				castMediaInfo.title = title;
				mediaPath = "file://" + c.getString(c.getColumnIndexOrThrow(MediaColumns.DATA));
				castMediaInfo.path = mediaPath;

				// if the current location is null, infer it from the first media that's added.
				if (castMediaInfo.location == null) {
					final int latCol = c.getColumnIndex(Media.LATITUDE);
					final int lonCol = c.getColumnIndex(Media.LONGITUDE);
					final double lat = c.getDouble(latCol);
					final double lon = c.getDouble(lonCol);

					final boolean isInArmpit = lat == 0 && lon == 0; // Sorry, people in boats off
																		// the coast of Ghana, but
																		// you're an unfortunate
																		// edge case...
					if (!c.isNull(latCol) && !c.isNull(lonCol) && !isInArmpit) {

						castMediaInfo.location = new Location("manual");
						castMediaInfo.location.setLatitude(lat);
						castMediaInfo.location.setLatitude(lon);
					}
				}
			}
		} finally {
			c.close();
		}

		return mediaPath;
	}

	private static String getContentType(ContentResolver cr, Uri content) {
		String mimeType = cr.getType(content);
		if (mimeType == null) {
			mimeType = CastMedia.guessContentTypeFromUrl(content.toString());
		}
		return mimeType;
	}

	public static class CastMediaInfo {

		/**
		 * The content's title
		 */
		public String title;

		/**
		 * the content's MIME type
		 */
		public String mimeType;
		/**
		 * the content:// URI of the newly created cast media item
		 */
		public Uri castMediaItem;
		/**
		 * if location information was discovered from the media's metadata, this location is
		 * extracted. Otherwise null.
		 */
		public Location location;
		/**
		 * the path on disk to the media
		 */
		public String path;

		public CastMediaInfo() {

		}

		/**
		 * @param path
		 * @param mimeType
		 * @param castMediaItem
		 * @param location
		 */
		public CastMediaInfo(String path, String mimeType, Uri castMediaItem, Location location) {
			this.path = path;
			this.mimeType = mimeType;
			this.castMediaItem = castMediaItem;
			this.location = location;
		}

	}

	public static Uri getThumbnail(Cursor c, int thumbCol, int thumbLocalCol) {
		Uri thumbnail;
		if (!c.isNull(thumbLocalCol)) {
			thumbnail = Uri.parse(c.getString(thumbLocalCol));
		} else if (!c.isNull(thumbCol)) {
			thumbnail = Uri.parse(c.getString(thumbCol));
		} else {
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
	public static String guessContentTypeFromUrl(String url) {

		// this was improved in Gingerbread
		// http://code.google.com/p/android/issues/detail?id=10100
		// so we have some pre-defined types here so we can make sure to return SOMETHING.
		String mimeType = URLConnection.guessContentTypeFromName(url);
		if (mimeType != null) {
			return mimeType;
		}

		if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
			mimeType = "image/jpeg";

		} else if (url.endsWith(".3gp")) {
			mimeType = "video/3gpp";

		} else if (url.endsWith(".mp4") || url.endsWith(".mpeg4")) {
			mimeType = "video/mp4";

		} else if (url.endsWith(".png")) {
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

			addFlag(FLAG_PARENT_MUST_SYNC_FIRST);

			// put("_resources", );

			// no MIME type is passed with a link media type, so we need to add one in.
			put("_content_type", new SyncCustom("content_type", SyncItem.SYNC_BOTH) {

				@Override
				public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
						throws JSONException, NetworkProtocolException, IOException {
					String mimeType = c.getString(c.getColumnIndex(COL_MIME_TYPE));
					final String localUri = c.getString(c.getColumnIndex(COL_LOCAL_URL));

					if (mimeType == null && localUri != null) {

						// XXX context.getContentResolver().getType(url);

						mimeType = guessContentTypeFromUrl(localUri);
						if (mimeType != null) {
							Log.d(TAG, "guessed MIME type from uri: " + localUri + ": " + mimeType);
						}
					}

					if (mimeType == null) {
						return null;
					}

					if (mimeType.startsWith("video/")) {
						return "videomedia";
					} else if (mimeType.startsWith("image/")) {
						return "imagemedia";
					} else {
						return null;
					}
				}

				@Override
				public ContentValues fromJSON(Context context, Uri localItem, JSONObject item,
						String lProp) throws JSONException, NetworkProtocolException, IOException {
					final ContentValues cv = new ContentValues();
					final String content_type = item.getString(remoteKey);

					if ("linkedmedia".equals(content_type)) {
						cv.put(COL_MIME_TYPE, MIMETYPE_HTML);
					}

					return cv;
				}
			});

			put(COL_CAPTURE_TIME, new SyncFieldMap("capture_time", SyncFieldMap.DATE,
					SyncItem.FLAG_OPTIONAL));

			put(COL_CAPTION, new SyncFieldMap("caption", SyncFieldMap.STRING,
					SyncItem.FLAG_OPTIONAL));
		}

		@Override
		public void onPostSyncItem(Context context, Account account, Uri uri, JSONObject item,
				boolean updated) throws SyncException, IOException {
			super.onPostSyncItem(context, account, uri, item, updated);
			if (uri != null) {
				final Uri parent = ProviderUtils.removeLastPathSegment(uri);

				if (Constants.DEBUG) {
					Log.d(TAG, "Starting media sync for " + parent);
				}
				context.startService(new Intent(AbsMediaSync.ACTION_SYNC_RESOURCES, parent));
			} else if (Constants.DEBUG) {
				Log.w(TAG, "no uri provided when calling onPostSyncItem()");
			}
		}
	}
}
