package edu.mit.mel.locast.mobile.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.cookie.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Video.Media;
import android.util.Log;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.StreamUtils;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkProtocolException;
import edu.mit.mel.locast.mobile.net.NotificationProgressListener;
import edu.mit.mel.locast.mobile.net.NetworkClient.InputStreamWatcher;
import edu.mit.mel.locast.mobile.notifications.ProgressNotification;

public class Cast extends TaggableItem implements MediaScannerConnectionClient, Favoritable.Columns, Locatable.Columns, Commentable.Columns {
	public final static String TAG = "LocastSyncCast";
	public final static String PATH = "casts";
	public final static Uri
		CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String SERVER_PATH = "cast/";
	public final static String DEVICE_EXTERNAL_MEDIA_PATH = "/locast/";

	public static final String
		_TITLE 			= "title",
		_DESCRIPTION 	= "description";

	public static final String
		_MEDIA_LOCAL_URI = "local_uri",
		_MEDIA_PUBLIC_URI = "public_uri",
		_CONTENT_TYPE = "content_type",
		_PROJECT_ID   = "project_id",
		_PROJECT_URI = "project_uri",
		_CASTMEDIA_DIR_URI = "castmedia_dir_uri",
		_THUMBNAIL_URI = "thumbnail_uri";

	public static final String[] PROJECTION =
	{   _ID,
		_PUBLIC_URI,
		_MEDIA_PUBLIC_URI,
		_TITLE,
		_DESCRIPTION,
		_PRIVACY,
		_AUTHOR,
		_CREATED_DATE,
		_PROJECT_ID,
		_PROJECT_URI,
		_MEDIA_LOCAL_URI,
		_CASTMEDIA_DIR_URI,
		_CONTENT_TYPE,
		_MODIFIED_DATE,
		_THUMBNAIL_URI,
		_FAVORITED,
		_LATITUDE,
		_LONGITUDE,
		_DRAFT };

	public static final String
		SORT_ORDER_DEFAULT = Cast._FAVORITED + " DESC," + Cast._MODIFIED_DATE+" DESC";

	private Context context;
	private MediaScannerConnection msc;
	private final Queue<String> toScan = new LinkedList<String>();
	private final Map<String, ScanQueueItem> scanMap = new TreeMap<String, ScanQueueItem>();

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends TaggableItem.TaggableItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = -6513174961005635755L;

		public ItemSyncMap() {
			super();
			putAll(Favoritable.SYNC_MAP);
			putAll(Locatable.SYNC_MAP);
			putAll(Commentable.SYNC_MAP);


			put(_DESCRIPTION, 		new SyncFieldMap("description", SyncFieldMap.STRING));
			put(_TITLE, 			new SyncFieldMap("title", SyncFieldMap.STRING));

			put(_THUMBNAIL_URI, 	new SyncFieldMap("screenshot", SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
			put(_MEDIA_PUBLIC_URI,  new SyncFieldMap("file_url",   SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
			//put(_PROJECT_URI,		new SyncFieldMap("project",    SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
			put(_PROJECT_URI,		new SyncFieldMap("project", SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

			// this is a local ID.
			put(_PROJECT_ID, 		new SyncCustom("project", SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL) {

				@Override
				public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
						throws JSONException, NetworkProtocolException, IOException {
					return null;
				}

				/* (non-Javadoc)
				 *
				 * This field will only work if the project has been sync'd before the cast.
				 *
				 * @see edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncItem#fromJSON(android.content.Context, android.net.Uri, org.json.JSONObject, java.lang.String)
				 */
				@Override
				public ContentValues fromJSON(Context context, Uri localItem,
						JSONObject item, String lProp) throws JSONException,
						NetworkProtocolException, IOException {
					String projectUri;
					if (item.optString(this.remoteKey).contains("project")){
						projectUri = item.optString(this.remoteKey);
					}else{
						projectUri = MediaProvider.getPublicPath(context.getContentResolver(), Project.CONTENT_URI, item.optLong(this.remoteKey));
					}
					final String[] selectionArgs = {projectUri};

					final Cursor c = context.getContentResolver().query(Project.CONTENT_URI, Project.SYNC_PROJECTION, Project._PUBLIC_URI + "=?", selectionArgs, null);
					final ContentValues cv = new ContentValues();
					if (c.moveToFirst()){
						cv.put(lProp, c.getLong(c.getColumnIndex(_ID)));
					}
					c.close();
					return cv;
				}
			});


			put("_contents", new OrderedList.SyncMapItem("castvideos", new CastMedia(), CastMedia.PATH));
		}
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	@Override
	public void onPostSyncItem(Context context, Uri uri, JSONObject item, boolean updated) throws SyncException, IOException {
		this.context = context;

		final ContentResolver cr = context.getContentResolver();
		//final Cursor c = cr.query(uri, PROJECTION, null, null, null);
		//c.moveToFirst();


		OrderedList.onUpdate(context, uri, item, "castvideos", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_FROM, new CastMedia(), CastMedia.PATH);
		final Uri castMediaDirUri = Uri.withAppendedPath(uri, CastMedia.PATH);
		final String pubCastMediaUri = MediaProvider.getPublicPath(cr, castMediaDirUri);


		final Cursor castMedia = cr.query(castMediaDirUri, CastMedia.PROJECTION, null, null, null);
		boolean haveAnyLocMedia = false;
		try {
			final int mediaUrlCol = castMedia.getColumnIndex(CastMedia._MEDIA_URL);
			final int localUriCol = castMedia.getColumnIndex(CastMedia._LOCAL_URI);
			final int idxCol = castMedia.getColumnIndex(CastMedia._LIST_IDX);
			final int mediaContentTypeCol = castMedia.getColumnIndex(CastMedia._MIME_TYPE);
			final int locIdCol = castMedia.getColumnIndex(CastMedia._ID);

			for (castMedia.moveToFirst(); ! castMedia.isAfterLast(); castMedia.moveToNext()){
				final Uri locMediaUri = castMedia.isNull(localUriCol) ? null : parseMaybeUri(castMedia.getString(localUriCol));
				final String pubMediaUri = castMedia.getString(mediaUrlCol);
				final boolean hasLocMediaUri = locMediaUri != null;
				final boolean hasPubMediaUri = pubMediaUri != null && pubMediaUri.length() > 0;
				haveAnyLocMedia = haveAnyLocMedia || hasLocMediaUri;

				if (hasLocMediaUri && !hasPubMediaUri){
					// upload
					try {
						final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(context);
						nc.uploadContentWithNotification(context, uri, pubCastMediaUri + castMedia.getLong(idxCol)+"/", locMediaUri, castMedia.getString(mediaContentTypeCol));
					} catch (final Exception e){
						final SyncException se = new SyncException(context.getString(R.string.error_uploading_cast_video));
						se.initCause(e);
						throw se;
					}
				Log.d(TAG, "Cast Media #" + castMedia.getPosition() + " is " + castMedia.getString(castMedia.getColumnIndex(CastMedia._MEDIA_URL)));
				}
			}
		}finally{
			castMedia.close();
		}

		if (!haveAnyLocMedia){
			Log.d(TAG, "There are no local videos, so looking to see if we should download");
			final Set<String> systemTags = getTags(cr, uri, TaggableItem.SYSTEM_PREFIX);
			final Cursor cast = cr.query(uri, PROJECTION, null, null, null);
			cast.moveToFirst();
			MediaProvider.dumpCursorToLog(cast, Cast.PROJECTION);
			final int pubMediaUrlCol = cast.getColumnIndex(_MEDIA_PUBLIC_URI);
			final String pubMediaUri = cast.getString(pubMediaUrlCol);
			cast.close();
			final boolean hasPubMediaUri = pubMediaUri != null && pubMediaUri.length() > 0;

			if (hasPubMediaUri && systemTags.contains("_featured")){
				Log.d(TAG, "cast is featured, so we'll download it.");
				final Uri pubMediaUriUri = Uri.parse(pubMediaUri);
				// only have a public copy, so download it and store locally.
				final File destfile = getFilePath(pubMediaUriUri);
				String newLocUri = null;
				if (!downloadCastMedia(context, destfile, uri, pubMediaUri)){
					newLocUri = checkForMediaEntry(context, uri, pubMediaUriUri);
				}
			}
		}
	} // onPostSyncItem()


	/**
	 * A wrapper to catch any instances of paths stored in the database.
	 * XXX Deprecate after any database upgrades.
	 *
	 * @param maybeUri Either a uri or a local filesystem path.
	 * @return
	 */
	public static Uri parseMaybeUri(String maybeUri){
		if (maybeUri.startsWith("/")){
			return Uri.fromFile(new File(maybeUri));
		}else{
			return Uri.parse(maybeUri);
		}
	}

	/**
	 * @param castUri uri for the cast.
	 * @return The CastMedia URI of the given cast.
	 */
	public static final Uri getCastMediaUri(Uri castUri){
		return Uri.withAppendedPath(castUri, CastMedia.PATH);
	}

	/**
	 * Gets the preferred full URI of the given cast. If it's a cast that's in a project,
	 * returns that uri.
	 *
	 * Makes a single query.
	 *
	 * @param context
	 * @param cast
	 * @return the canonical URI for the given cast.
	 */
	public static Uri getCanonicalUri(Context context, Uri cast){
		Uri canonical = null;

		final Cursor c = context.getContentResolver().query(cast, new String[]{Cast._ID, Cast._PROJECT_ID}, null, null, null);
		if (c.moveToFirst()){
			final long castId = c.getLong(c.getColumnIndex(Cast._ID));
			final Uri project = getProjectUri(c);
			if (project != null){
				canonical = project.buildUpon().appendPath(PATH).appendPath(Long.toString(castId)).build();
			}else{
				canonical = cast;
			}
		}
		c.close();
		return canonical;
	}

	/**
	 * Gets the preferred full URI of the given cast. If it's a cast that's in a project,
	 * returns that uri.
	 *
	 * Makes a single query.
	 *
	 * @param c a cursor pointing to a cast. Ensure the cursor has selected the Cast._PROJECT_ID field.
	 * @return the canonical URI for the given cast.
	 */
	public static Uri getCanonicalUri(Cursor c){
		Uri canonical = null;

		final long castId = c.getLong(c.getColumnIndex(Cast._ID));
		final Uri project = getProjectUri(c);
		if (project != null){
			canonical = project.buildUpon().appendPath(PATH).appendPath(Long.toString(castId)).build();
		}else{
			canonical = ContentUris.withAppendedId(CONTENT_URI, castId);
		}
		return canonical;
	}

	/**
	 * @param cast a cursor pointing to a cast.
	 * @return the uri of the project associated with this cast, or null if there is none.
	 */
	public static final Uri getProjectUri(Cursor cast){
		final int projectIdx = cast.getColumnIndex(Cast._PROJECT_ID);
		if (cast.isNull(projectIdx)){
			return null;
		}

		return ContentUris.withAppendedId(Project.CONTENT_URI, cast.getLong(projectIdx));
	}

	/**
	 * @param context
	 * @param cast
	 * @return the title of the cast or null if there's an error.
	 */
	public static String getTitle(Context context, Uri cast){
		final String[] projection = {Cast._ID, Cast._TITLE};
		final Cursor c = context.getContentResolver().query(cast, projection, null, null, null);
		String castTitle = null;
		if (c.moveToFirst()){
			castTitle = c.getString(c.getColumnIndex(Cast._TITLE));
		}
		c.close();
		return castTitle;
	}

	/*
	public void updateCastMedia(String castVideoPath, String mimeType){
		if (msc == null){
			this.msc = new MediaScannerConnection(context, this);
			this.msc.connect();

		}else if (msc.isConnected()){
			msc.scanFile(castVideoPath, mimeType);

		}else{
			scanMap.put(castVideoPath, new ScanQueueItem(castUri, contentType));
			toScan.add(filePath);
		}
	}*/

	/**
	 * @param pubUri public media uri
	 * @return The local path on disk for the given remote video.
	 * @throws SyncException
	 */
	public static File getFilePath(Uri pubUri) throws SyncException{
		final File sdcardPath = Environment.getExternalStorageDirectory();
		// pull off the server's name for this file.
		final String localFile = pubUri.getLastPathSegment();

		final File locastSavePath = new File(sdcardPath, DEVICE_EXTERNAL_MEDIA_PATH);
		locastSavePath.mkdirs();
		if (!locastSavePath.canWrite()) {
			throw new SyncException("cannot write to external storage '"+locastSavePath.getAbsolutePath()+"'");
		}
		final File saveFile = new File(locastSavePath, localFile);
		return saveFile;
	}
	/**
	 * Checks the local file system and checks to see if the given media resource has been downloaded successfully already.
	 * If not, it will download it from the server and store it in the filesystem.
	 * This uses the last-modified header to determine if a media resource is up to date.
	 *
	 * @param castUri the content:// uri of the cast
	 * @param pubUri the http:// uri of the public video
	 * @return true if anything has changed. False if this function has determined it doesn't need to do anything.
	 * @throws SyncException
	 */
	public boolean downloadCastMedia(Context context, File saveFile, Uri castUri, String pubUri) throws SyncException {
		final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(context);
		try {
			boolean dirty = true;
			String contentType = null;

			if (saveFile.exists()){
				final HttpResponse headRes = nc.head(pubUri);
				final long serverLength = Long.valueOf(headRes.getFirstHeader("Content-Length").getValue());
				// XXX should really be checking the e-tag too, but this will be fine for our application.
				final Header remoteLastModifiedHeader = headRes.getFirstHeader("last-modified");

				long remoteLastModified = 0;
				if (remoteLastModifiedHeader != null){
					remoteLastModified = DateUtils.parseDate(remoteLastModifiedHeader.getValue()).getTime();
				}

				final HttpEntity entity = headRes.getEntity();
				if (entity != null){
					entity.consumeContent();
				}
				if (saveFile.length() == serverLength && saveFile.lastModified() == remoteLastModified){
					Log.i(TAG, "Local copy of cast "+saveFile+" seems to be the same as the one on the server. Not re-downloading.");

					dirty = false;
				}
				// fall through and re-download, as we have a different size file locally.
			}
			if (dirty){
				String castTitle = getTitle(context, castUri);
				if (castTitle == null){
					castTitle = "untitled";
				}

				final HttpResponse res = nc.get(pubUri);
				final HttpEntity ent = res.getEntity();
				final ProgressNotification notification = new ProgressNotification(context,
						context.getString(R.string.sync_downloading_cast, castTitle),
						ProgressNotification.TYPE_DOWNLOAD,
						PendingIntent.getActivity(context, 0, new Intent(Intent.ACTION_VIEW, castUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0),
						false);

				final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				final NotificationProgressListener npl = new NotificationProgressListener(nm, notification, ent.getContentLength(), 0);
				final InputStreamWatcher is = new InputStreamWatcher(ent.getContent(), npl);

				try {
					Log.d(TAG, "Downloading "+pubUri + " and saving it in "+ saveFile.getAbsolutePath());
					final FileOutputStream fos = new FileOutputStream(saveFile);
					StreamUtils.inputStreamToOutputStream(is, fos);
					fos.close();

					// set the file's last modified to match the remote.
					// We can check this later to see if everything is up to date.
					final Header lastModified = res.getFirstHeader("last-modified");
					if (lastModified != null){
						saveFile.setLastModified(DateUtils.parseDate(lastModified.getValue()).getTime());
					}

					contentType = ent.getContentType().getValue();

				}finally{
					npl.done();
					ent.consumeContent();
					is.close();
				}
			}

			final String filePath = saveFile.getAbsolutePath();
			scanMediaItem(castUri, filePath, contentType);

		} catch (final Exception e) {
			final SyncException se = new SyncException("Error downloading content item.");
			se.initCause(e);
			throw se;
		}
		return true;
	}

	/**
	 * Enqueues a media item to be scanned.
	 * onScanComplete() will be called once the item has been scanned successfully.
	 *
	 * @param castUri
	 * @param filePath
	 * @param contentType
	 */
	public void scanMediaItem(Uri castUri, String filePath, String contentType){
		if (msc == null){
			scanMap.put(filePath, new ScanQueueItem(castUri, contentType));
			toScan.add(filePath);
			this.msc = new MediaScannerConnection(context, this);
			this.msc.connect();

		}else if (msc.isConnected()){
			msc.scanFile(filePath, contentType);

		// if we're not connected yet, we need to remember what we want scanned,
		// so that we can queue it up once connected.
		}else{
			scanMap.put(filePath, new ScanQueueItem(castUri, contentType));
			toScan.add(filePath);
		}
	}

	public void onScanCompleted(String path, Uri uri) {
		if (uri == null){
			Log.e(TAG, "Scan failed for newly downloaded content: "+path);
			return;
		}

		final ContentValues cvCast = new ContentValues();
		cvCast.put(_MEDIA_LOCAL_URI, uri.toString());
		cvCast.put(MediaProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

		final ScanQueueItem item = scanMap.get(path);
		if (item == null){
			Log.e(TAG, "Couldn't find media item ("+path+") in scan map, so we couldn't update any casts.");
			return;
		}
		Log.d(TAG, "new local uri " + uri + " for cast "+item.castUri);
		this.context.getContentResolver().update(item.castUri, cvCast, null, null);
	}

	public void onMediaScannerConnected() {
		while (!toScan.isEmpty()){
			final String scanme = toScan.remove();
			final ScanQueueItem item = scanMap.get(scanme);
			this.msc.scanFile(scanme, item.contentType);
		}
	}

	private class ScanQueueItem {
		public Uri castUri;
		public String contentType;
		public ScanQueueItem(Uri castUri, String contentType) {
			this.castUri = castUri;
			this.contentType = contentType;
		}
	}

	/**
	 * Scans the media database to see if the given item is currently there.
	 * If it is, update the cast to point to the local content: URI for it.
	 *
	 * @param context
	 * @param cast Local URI to the cast.
	 * @param pubUri public URI to the media file.
	 * @return local URI if it exists.
	 * @throws SyncException
	 */
	public static String checkForMediaEntry(Context context, Uri cast, Uri pubUri) throws SyncException{
		String newLocUri = null;
		final File destfile = getFilePath(pubUri);
		final String[] projection = {Media._ID, Media.DATA};
		final String selection = Media.DATA + "=?";
		final String[] selectionArgs = {destfile.getAbsolutePath()};
		final Cursor mediaEntry = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
		if (mediaEntry.moveToFirst()){
			newLocUri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, mediaEntry.getLong(mediaEntry.getColumnIndex(Media._ID))).toString();
		}else{
			Log.e(TAG, "The media provider doesn't seem to know about "+destfile.getAbsolutePath()+" which is on the filesystem. Strange...");
		}
		mediaEntry.close();


		if (newLocUri != null){
			final ContentValues cvCast = new ContentValues();
			cvCast.put(_MEDIA_LOCAL_URI, newLocUri);
			cvCast.put(MediaProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

			Log.d("Locast", "new local uri " + newLocUri + " for cast "+cast);
			context.getContentResolver().update(cast, cvCast, null, null);
		}
		return newLocUri;
	}
}
