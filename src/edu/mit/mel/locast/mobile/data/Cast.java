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
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Video.Media;
import android.util.Log;
import edu.mit.mel.locast.mobile.StreamUtils;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

public class Cast extends TaggableItem implements MediaScannerConnectionClient, Favoritable.Columns, Locatable.Columns {
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
		_LOCAL_URI = "local_uri",
		_PUBLIC_URI = "public_uri",
		_CONTENT_TYPE = "content_type",
		_PROJECT_ID   = "project_id",

		_THUMBNAIL_URI = "thumbnail_uri";

	public static final String[] PROJECTION =
	{   _ID,
		_TITLE,
		_DESCRIPTION,
		_PRIVACY,
		_AUTHOR,
		_CREATED_DATE,
		_PUBLIC_ID,
		_PROJECT_ID,
		_LOCAL_URI,
		_PUBLIC_URI,
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
	private AndroidNetworkClient nc;
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


			put(_DESCRIPTION, 		new SyncFieldMap("description", SyncFieldMap.STRING));
			put(_TITLE, 			new SyncFieldMap("title", SyncFieldMap.STRING));

			put(_THUMBNAIL_URI, 	new SyncFieldMap("screenshot", SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
			put(_PUBLIC_URI,        new SyncFieldMap("file_url",   SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));


			put("_contents", new OrderedList.SyncMapItem("castvideos", new CastMedia(), CastMedia.PATH));
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri, JSONObject item, boolean updated) throws SyncException ,IOException {
			super.onPostSyncItem(context, uri, item, updated);

			final ContentResolver cr = context.getContentResolver();
			//final Cursor c = cr.query(uri, PROJECTION, null, null, null);
			//c.moveToFirst();
			final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(context);

			OrderedList.onUpdate(context, uri, item, "castvideos", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_FROM, new CastMedia(), CastMedia.PATH);
			final Uri castMediaDirUri = Uri.withAppendedPath(uri, CastMedia.PATH);
			final String pubCastMediaUri = MediaProvider.getPublicPath(cr, castMediaDirUri);

			final Cursor castMedia = cr.query(castMediaDirUri, CastMedia.PROJECTION, null, null, null);

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
				if (hasLocMediaUri && !hasPubMediaUri){
					// upload
					try {

						nc.uploadContentWithNotification(context, uri, pubCastMediaUri + castMedia.getLong(idxCol)+"/", locMediaUri, castMedia.getString(mediaContentTypeCol));
					} catch (final Exception e){
						final SyncException se = new SyncException("Error uploading content item.");
						se.initCause(e);
						throw se;
					}
				Log.d(TAG, "Cast Media #" + castMedia.getPosition() + " is " + castMedia.getString(castMedia.getColumnIndex(CastMedia._MEDIA_URL)));
				}
			}
			castMedia.close();
		}
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

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
	 * @param pubUri
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
	 * @param castUri the content:// uri of the cast
	 * @param pubUri the http:// uri of the public video
	 * @return true if anything has changed. False if this function has determined it doesn't need to do anything.
	 * @throws SyncException
	 */
	public boolean downloadCastMedia(File saveFile, Uri castUri, String pubUri) throws SyncException {
		try {
			if (saveFile.exists()){
				final HttpResponse headRes = nc.head(pubUri);
				if (saveFile.length() > 2000000){
					Log.w(TAG, "Video "+castUri+" is too large. Not downloading.");
					return false;
				}
				if (saveFile.length() == Long.valueOf(headRes.getFirstHeader("Content-Length").getValue())){
					Log.i(TAG, "Local copy of cast "+saveFile+" seems to be the same as the one on the server. Not re-downloading.");
					return false;
				}else{
					//Log.i(TAG, )
				}
				headRes.getEntity().consumeContent();
			}
			final HttpResponse res = nc.get(pubUri);
			final HttpEntity ent = res.getEntity();
			final InputStream is = ent.getContent();
			try {
				final FileOutputStream fos = new FileOutputStream(saveFile);
				StreamUtils.inputStreamToOutputStream(is, fos);
				fos.close();
				is.close();

				final String filePath = saveFile.getAbsolutePath();
				final String contentType = ent.getContentType().getValue();


				if (msc == null){
					this.msc = new MediaScannerConnection(context, this);
					this.msc.connect();

				}else if (msc.isConnected()){
					msc.scanFile(filePath, contentType);

				}else{
					scanMap.put(filePath, new ScanQueueItem(castUri, contentType));
					toScan.add(filePath);
				}
			}finally{
				ent.consumeContent();
			}

		} catch (final Exception e) {
			final SyncException se = new SyncException("Error downloading content item.");
			se.initCause(e);
			throw se;
		}
		return true;
	}

	public void onScanCompleted(String path, Uri uri) {
		if (uri == null){
			Log.e(TAG, "Scan failed for newly downloaded content: "+path);
			return;
		}


		final ContentValues cvCast = new ContentValues();
		cvCast.put(_LOCAL_URI, uri.toString());
		final ScanQueueItem item = scanMap.get(path);
		Log.d("Locast", "new local uri " + uri + " for cast "+item.castUri);
		// TODO should be passing in the modified date here to prevent marking the cast as dirty.
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
	 * Scans the media database to see if the given item is there.
	 *
	 * @param context
	 * @param uri Local URI to the cast.
	 * @param pubUri public URI to the media file.
	 * @return local URI if it exists.
	 * @throws SyncException
	 */
	public static String checkForMediaEntry(Context context, Uri uri, Uri pubUri) throws SyncException{
		String newLocUri = null;
		final File destfile = getFilePath(pubUri);
		final String[] projection = {Media.DATA, Media._ID};
		final String selection = Media.DATA + "=?";
		final String[] selectionArgs = {destfile.getAbsolutePath()};
		final Cursor mediaEntry = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
		if (mediaEntry.getCount() > 0){
			mediaEntry.moveToFirst();
			newLocUri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, mediaEntry.getLong(mediaEntry.getColumnIndex(Media._ID))).toString();
		}else{
			Log.e(TAG, "The media provider doesn't seem to know about "+destfile.getAbsolutePath()+" which is on the filesystem. Strange...");
		}
		mediaEntry.close();


		if (newLocUri != null){
			final ContentValues cvCast = new ContentValues();
			cvCast.put(_LOCAL_URI, newLocUri);
			final String[] castProjection = {_ID, _MODIFIED_DATE};
			final Cursor castCursor = context.getContentResolver().query(uri, castProjection, null, null, null);
			castCursor.moveToFirst();
			cvCast.put(_MODIFIED_DATE, castCursor.getLong(castCursor.getColumnIndex(_MODIFIED_DATE)));
			castCursor.close();

			Log.d("Locast", "new local uri " + newLocUri + " for cast "+uri);
			context.getContentResolver().update(uri, cvCast, null, null);
		}
		return newLocUri;
	}
}
