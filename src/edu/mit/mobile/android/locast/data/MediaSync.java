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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.cookie.DateUtils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkClient.InputStreamWatcher;
import edu.mit.mobile.android.locast.net.NotificationProgressListener;
import edu.mit.mobile.android.locast.notifications.ProgressNotification;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.utils.StreamUtils;

public class MediaSync extends Service implements MediaScannerConnectionClient {
	private final static String TAG = MediaSync.class.getSimpleName();

	private final boolean DEBUG = Constants.DEBUG;

	private final Map<String, ScanQueueItem> scanMap = new TreeMap<String, ScanQueueItem>();
	private MediaScannerConnection msc;
	private final Queue<String> toScan = new LinkedList<String>();

	public static final String ACTION_SYNC_RESOURCES = "edu.mit.mobile.android.locast.ACTION_SYNC_RESOURCES";

	private final IBinder mBinder = new LocalBinder();

	public static final long TIMEOUT_LAST_SYNC = 10 * 1000 * 1000; // nanoseconds

	public final static String DEVICE_EXTERNAL_MEDIA_PATH = "/locast/",
			NO_MEDIA = ".nomedia";

	protected final ConcurrentLinkedQueue<SyncQueueItem> mSyncQueue = new ConcurrentLinkedQueue<SyncQueueItem>();

	private MessageDigest mDigest;

	private final HashMap<Uri, Long> mRecentlySyncd = new HashMap<Uri, Long>();

	private ContentResolver cr;

	public MediaSync() {
		super();

		try {
			mDigest = MessageDigest.getInstance("SHA-1");
		} catch (final NoSuchAlgorithmException e) {
			e.printStackTrace();
			mDigest = null;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	private static final int MSG_DONE = 0;

	private final Handler mDoneTimeout = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_DONE:
				stopIfQueuesEmpty();
				break;
			}
		}
	};

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final Uri data = intent.getData();
		final SyncQueueItem syncQueueItem = new SyncQueueItem(data,
				intent.getExtras());
		if (!mSyncQueue.contains(syncQueueItem) && !checkRecentlySyncd(data)) {
			if (DEBUG) {
				Log.d(TAG, "enqueueing " + syncQueueItem);
			}
			mSyncQueue.add(syncQueueItem);
		} else {
			if (DEBUG) {
				Log.d(TAG, syncQueueItem.toString()
						+ " already in the queue. Skipping.");
			}
		}

		maybeStartTask();

		return START_REDELIVER_INTENT;
	}

	private SyncTask mSyncTask;

	private synchronized void maybeStartTask() {
		if (mSyncTask == null) {
			mSyncTask = new SyncTask();
			mSyncTask.execute();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

		cr = getContentResolver();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (mSyncTask != null) {
			mSyncTask.cancel(true);
		}

		if (msc != null) {
			msc.disconnect();
		}
	}

	/**
	 * @param uri
	 * @return true if the item has been synchronized recently
	 */
	private boolean checkRecentlySyncd(Uri uri) {
		synchronized (mRecentlySyncd) {
			final Long lastSyncd = mRecentlySyncd.get(uri);
			if (lastSyncd != null) {
				return (System.nanoTime() - lastSyncd) < TIMEOUT_LAST_SYNC;
			} else {
				return false;
			}
		}
	}

	private void addUriToRecentlySyncd(Uri uri) {
		synchronized (mRecentlySyncd) {
			mRecentlySyncd.put(uri, System.nanoTime());
		}
	}

	/**
	 * Goes through the queue and syncs all the items in it.
	 *
	 * @author steve
	 *
	 */
	private class SyncTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			while (!mSyncQueue.isEmpty()) {
				try {
					final SyncQueueItem qi = mSyncQueue.remove();

					syncItemMedia(qi.uri);
					addUriToRecentlySyncd(qi.uri);

				} catch (final SyncException se) {
					se.printStackTrace();
				}
			}
			scheduleSelfDestruct();
			mSyncTask = null;

			return null;
		}

		@Override
		protected void onCancelled() {
			mSyncTask = null;
		}

	}

	private class SyncQueueItem {
		public SyncQueueItem(Uri uri, Bundle extras) {
			this.uri = uri;
			this.extras = extras;
		}

		Uri uri;
		Bundle extras;

		@Override
		public boolean equals(Object o) {

			final SyncQueueItem o2 = (SyncQueueItem) o;
			return o == null ? false : (this.uri == null ? false : this.uri
					.equals(o2.uri)
					&& ((this.extras == null && o2.extras == null)
							|| this.extras == null ? false : this.extras
							.equals(o2.extras)));
		}

		@Override
		public String toString() {
			return SyncQueueItem.class.getSimpleName() + ": " + uri.toString()
					+ ((extras != null) ? " with extras " + extras : "");
		}
	}

	final static String[] PROJECTION = { CastMedia._ID, CastMedia._MIME_TYPE,
			CastMedia._LOCAL_URI, CastMedia._MEDIA_URL, CastMedia._KEEP_OFFLINE,
			CastMedia._PUBLIC_URI,
			CastMedia._THUMB_LOCAL};

	final static String[] CAST_PROJECTION = {Cast._ID, Cast._FAVORITED };
	/**
	 * Synchronize the media of the given castMedia. It will download or upload
	 * as needed.
	 *
	 * @param castMediaUri
	 * @throws SyncException
	 */
	public void syncItemMedia(Uri castMediaUri) throws SyncException {

		final Cursor castMedia = cr.query(castMediaUri, PROJECTION, null, null,
				null);

		final Uri castUri = CastMedia.getCast(castMediaUri);
		final Cursor cast = cr.query(castUri, CAST_PROJECTION, null, null,
				null);

		try {
			if (!castMedia.moveToFirst()) {
				throw new IllegalArgumentException("uri " + castMediaUri
						+ " has no content");
			}

			if (!cast.moveToFirst()){
				throw new IllegalArgumentException(castMediaUri + " cast " + castUri + " has no content");
			}

			// cache the column numbers
			final int mediaUrlCol = castMedia
					.getColumnIndex(CastMedia._MEDIA_URL);
			final int localUriCol = castMedia
					.getColumnIndex(CastMedia._LOCAL_URI);

			final boolean isFavorite = cast.getInt(cast.getColumnIndex(Cast._FAVORITED)) != 0;
			final boolean keepOffline = castMedia.getInt(castMedia.getColumnIndex(CastMedia._KEEP_OFFLINE)) != 0;


			final String mimeType = castMedia.getString(castMedia
					.getColumnIndex(CastMedia._MIME_TYPE));

			final boolean isImage = (mimeType != null) && mimeType.startsWith("image/");

			// we don't need to sync this
			if ("text/html".equals(mimeType)) {
				return;
			}

			final Uri locMedia = castMedia.isNull(localUriCol) ? null : Uri
					.parse(castMedia.getString(localUriCol));
			final String pubMedia = castMedia.getString(mediaUrlCol);
			final boolean hasLocMedia = locMedia != null
					&& new File(locMedia.getPath()).exists();
			final boolean hasPubMedia = pubMedia != null
					&& pubMedia.length() > 0;

			final String localThumb  = castMedia.getString(castMedia.getColumnIndex(CastMedia._THUMB_LOCAL));



			if (hasLocMedia && !hasPubMedia) {
				final String uploadPath = castMedia.getString(castMedia.getColumnIndex(CastMedia._PUBLIC_URI));
				uploadMedia(uploadPath, castMediaUri, mimeType, locMedia);

			} else if (!hasLocMedia && hasPubMedia) {
				// only have a public copy, so download it and store locally.
				final Uri pubMediaUri = Uri.parse(pubMedia);
				final File destfile = getFilePath(pubMediaUri);

				// the following conditions indicate that the cast media should be downloaded.
				if (keepOffline || isFavorite || isImage){
					final boolean anythingChanged = downloadMediaFile(pubMedia,
							destfile, castMediaUri);

					// the below is inverted from what seems logical, because downloadMediaFile()
					// will actually update the castmedia if it downloads anything. We'll only be getting
					// here if we don't have any local record of the file, so we should make the association
					// by ourselves.
					if (!anythingChanged) {
						File thumb = null;
						if (isImage && localThumb == null){
							thumb = destfile;
						}
						updateLocalFile(castMediaUri, destfile, thumb);
						// disabled to avoid spamming the user with downloaded
						// items.
						// checkForMediaEntry(castMediaUri, pubMediaUri, mimeType);
					}
				}
			}
		} finally {
			cast.close();
			castMedia.close();
		}
	}

	private void updateLocalFile(Uri castMediaUri, File localFile, File localThumbnail) {
		final ContentValues cv = new ContentValues();
		cv.put(CastMedia._LOCAL_URI, Uri.fromFile(localFile).toString());
		if (localThumbnail != null){
			cv.put(CastMedia._THUMB_LOCAL, Uri.fromFile(localFile).toString());
		}
		cv.put(MediaProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

		getContentResolver().update(castMediaUri, cv, null, null);

	}

	private void uploadMedia(String uploadPath, Uri castMediaUri,
			String contentType, final Uri locMedia) throws SyncException {
		// upload
		try {
			final NetworkClient nc = NetworkClient.getInstance(this);
			nc.uploadContentWithNotification(this,
					CastMedia.getCast(castMediaUri), uploadPath, locMedia,
					contentType, NetworkClient.UploadType.FORM_POST);
		} catch (final Exception e) {
			final SyncException se = new SyncException(
					getString(R.string.error_uploading_cast_video));
			se.initCause(e);
			throw se;
		}
	}

	/**
	 * Gets, makes and verifies that the location is writable. Also checks that
	 * the special .nomedia file that tells Android to not index the path is
	 * present.
	 *
	 * @return the location to save locast media.
	 * @throws SyncException
	 */
	private File getSaveLocation() throws SyncException {
		final File sdcardPath = Environment.getExternalStorageDirectory();

		final File locastSavePath = new File(sdcardPath,
				DEVICE_EXTERNAL_MEDIA_PATH);
		if (!locastSavePath.exists()) {
			locastSavePath.mkdirs();
		}
		if (!locastSavePath.canWrite()) {
			throw new SyncException("cannot write to external storage '"
					+ locastSavePath.getAbsolutePath() + "'");
		}

		// this special file tells Android's media framework to not index the
		// given folder.
		final File noMedia = new File(locastSavePath, NO_MEDIA);
		if (!noMedia.exists()) {
			try {
				noMedia.createNewFile();
			} catch (final IOException e) {
				final SyncException se = new SyncException("cannot create "
						+ NO_MEDIA + " file");
				se.initCause(e);
				throw se;
			}
		}
		return locastSavePath;
	}

	/**
	 * @param pubUri
	 *            public media uri
	 * @return The local path on disk for the given remote video.
	 * @throws SyncException
	 */
	public File getFilePath(Uri pubUri) throws SyncException {
		// pull off the server's name for this file.
		final String localFile = pubUri.getLastPathSegment();

		final File saveFile = new File(getSaveLocation(), localFile);
		return saveFile;
	}

	/**
	 * Checks the local file system and checks to see if the given media
	 * resource has been downloaded successfully already. If not, it will
	 * download it from the server and store it in the filesystem. This uses the
	 * last-modified header and file length to determine if a media resource is
	 * up to date.
	 *
	 * This method blocks for the course of the download, but shows a progress
	 * notification.
	 *
	 * @param pubUri
	 *            the http:// uri of the public resource
	 * @param saveFile
	 *            the file that the resource will be saved to
	 * @param castMediaUri
	 *            the content:// uri of the cast
	 * @return true if anything has changed. False if this function has
	 *         determined it doesn't need to do anything.
	 * @throws SyncException
	 */
	public boolean downloadMediaFile(String pubUri, File saveFile,
			Uri castMediaUri) throws SyncException {
		final NetworkClient nc = NetworkClient.getInstance(this);
		try {
			boolean dirty = true;
			//String contentType = null;

			if (saveFile.exists()) {
				final HttpResponse headRes = nc.head(pubUri);
				final long serverLength = Long.valueOf(headRes.getFirstHeader(
						"Content-Length").getValue());
				// XXX should really be checking the e-tag too, but this will be
				// fine for our application.
				final Header remoteLastModifiedHeader = headRes
						.getFirstHeader("last-modified");

				long remoteLastModified = 0;
				if (remoteLastModifiedHeader != null) {
					remoteLastModified = DateUtils.parseDate(
							remoteLastModifiedHeader.getValue()).getTime();
				}

				final HttpEntity entity = headRes.getEntity();
				if (entity != null) {
					entity.consumeContent();
				}
				if (saveFile.length() == serverLength
						&& saveFile.lastModified() >= remoteLastModified) {
					if (DEBUG) {
						Log.i(TAG,
								"Local copy of cast "
										+ saveFile
										+ " seems to be the same as the one on the server. Not re-downloading.");
					}

					dirty = false;
				}
				// fall through and re-download, as we have a different size
				// file locally.
			}
			if (dirty) {
				final Uri castUri = CastMedia.getCast(castMediaUri);
				String castTitle = Cast.getTitle(this, castUri);
				if (castTitle == null) {
					castTitle = "untitled";
				}

				final HttpResponse res = nc.get(pubUri);
				final HttpEntity ent = res.getEntity();
				final ProgressNotification notification = new ProgressNotification(
						this, getString(R.string.sync_downloading_cast,
								castTitle), ProgressNotification.TYPE_DOWNLOAD,
						PendingIntent.getActivity(this, 0, new Intent(
								Intent.ACTION_VIEW, castUri)
								.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0),
						false);

				final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				final NotificationProgressListener npl = new NotificationProgressListener(
						nm, notification, ent.getContentLength(), 0);
				final InputStreamWatcher is = new InputStreamWatcher(
						ent.getContent(), npl);

				try {
					if (DEBUG) {
						Log.d(TAG, "Downloading " + pubUri + " and saving it in "
								+ saveFile.getAbsolutePath());
					}
					final FileOutputStream fos = new FileOutputStream(saveFile);
					StreamUtils.inputStreamToOutputStream(is, fos);
					fos.close();

					// set the file's last modified to match the remote.
					// We can check this later to see if everything is up to
					// date.
					final Header lastModified = res
							.getFirstHeader("last-modified");
					if (lastModified != null) {
						saveFile.setLastModified(DateUtils.parseDate(
								lastModified.getValue()).getTime());
					}

					//contentType = ent.getContentType().getValue();

				} finally {
					npl.done();
					ent.consumeContent();
					is.close();
				}

				// XXX avoid this to prevent adding to local collection
				// final String filePath = saveFile.getAbsolutePath();

				// scanMediaItem(castMediaUri, filePath, contentType);
				return true;
			}
		} catch (final Exception e) {
			final SyncException se = new SyncException(
					"Error downloading content item.");
			se.initCause(e);
			throw se;
		}
		return false;
	}

	/**
	 * Enqueues a media item to be scanned. onScanComplete() will be called once
	 * the item has been scanned successfully.
	 *
	 * @param castMediaUri
	 * @param filePath
	 * @param contentType
	 */
	public void scanMediaItem(Uri castMediaUri, String filePath,
			String contentType) {
		scanMap.put(filePath, new ScanQueueItem(castMediaUri, contentType));

		if (msc == null) {
			toScan.add(filePath);
			this.msc = new MediaScannerConnection(this, this);
			this.msc.connect();

		} else if (msc.isConnected()) {
			msc.scanFile(filePath, contentType);

			// if we're not connected yet, we need to remember what we want
			// scanned,
			// so that we can queue it up once connected.
		} else {
			toScan.add(filePath);
		}
	}

	public void onScanCompleted(String path, Uri locMediaUri) {
		if (locMediaUri == null) {
			Log.e(TAG, "Scan failed for newly downloaded content: " + path);
			return;
		}

		final ScanQueueItem item = scanMap.get(path);
		if (item == null) {
			Log.e(TAG, "Couldn't find media item (" + path
					+ ") in scan map, so we couldn't update any casts.");
			return;
		}

		updateCastMediaLocalUri(item.castMediaUri, locMediaUri.toString(),
				item.contentType);
	}

	private String sha1Sum(String data) {
		if (mDigest == null) {
			throw new RuntimeException("no message digest available");
		}
		mDigest.reset();
		mDigest.update(data.getBytes());
		return new BigInteger(mDigest.digest()).toString(16);
	}

	private class ThumbnailException extends Exception {
		/**
		 *
		 */
		private static final long serialVersionUID = 4949920781556749566L;

		public ThumbnailException() {
			super();

		}

		public ThumbnailException(String message) {
			super(message);
		}
	}

	// TODO this should probably look to see if the thumbnail file already
	// exists for the given media, but should check for updates too.
	private String generateThumbnail(Uri castMedia, String mimeType,
			String locMedia) throws ThumbnailException {
		final long locId = ContentUris.parseId(Uri.parse(locMedia));

		Bitmap thumb;
		if (mimeType.startsWith("image/")) {
			thumb = Images.Thumbnails.getThumbnail(getContentResolver(), locId,
					Images.Thumbnails.MINI_KIND, null);

		} else if (mimeType.startsWith("video/")) {
			thumb = Video.Thumbnails.getThumbnail(getContentResolver(), locId,
					Video.Thumbnails.MINI_KIND, null);

		} else {
			throw new IllegalArgumentException(
					"cannot generate thumbnail for item with MIME type: '"
							+ mimeType + "'");
		}

		if (thumb == null) {
			throw new ThumbnailException(
					"Android thumbnail generator returned null");
		}

		try {
			final File outFile = new File(getCacheDir(), "thumb"
					+ sha1Sum(locMedia) + ".jpg");
			// final File outFile = File.createTempFile("thumb", ".jpg",
			// getCacheDir());
			if (!outFile.exists()) {
				if (!outFile.createNewFile()) {
					throw new IOException("cannot create new file");
				}
				if (DEBUG) {
					Log.d(TAG, "attempting to save thumb in " + outFile);
				}
				final FileOutputStream fos = new FileOutputStream(outFile);
				thumb.compress(CompressFormat.JPEG, 75, fos);
				thumb.recycle();
				fos.close();

				if (DEBUG) {
					Log.d(TAG, "generated thumbnail for " + locMedia
							+ " and saved it in " + outFile.getAbsolutePath());
				}
			}

			return Uri.fromFile(outFile).toString();
		} catch (final IOException ioe) {
			final ThumbnailException te = new ThumbnailException();
			te.initCause(ioe);
			throw te;
		}
	}

	private void updateCastMediaLocalUri(Uri castMedia, String locMedia,
			String mimeType) {

		final ContentValues cvCastMedia = new ContentValues();
		cvCastMedia.put(CastMedia._LOCAL_URI, locMedia);
		cvCastMedia.put(MediaProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

		try {
			final String locThumb = generateThumbnail(castMedia, mimeType,
					locMedia);
			if (locThumb != null) {
				cvCastMedia.put(CastMedia._THUMB_LOCAL, locThumb);
			}

		} catch (final ThumbnailException e) {
			Log.e(TAG, "could not generate thumbnail for " + locMedia + ": "
					+ e.getLocalizedMessage());
			e.printStackTrace();
		}

		if (DEBUG) {
			Log.d(TAG, "new local uri " + locMedia + " for cast media " + castMedia);
		}
		getContentResolver().update(castMedia, cvCastMedia, null, null);
	}

	// TODO should this be on a separate thread?
	public void onMediaScannerConnected() {
		while (!toScan.isEmpty()) {
			final String scanme = toScan.remove();
			final ScanQueueItem item = scanMap.get(scanme);
			this.msc.scanFile(scanme, item.contentType);
		}
		scheduleSelfDestruct();
	}

	private void scheduleSelfDestruct() {
		mDoneTimeout.removeMessages(MSG_DONE);
		mDoneTimeout.sendEmptyMessageDelayed(MSG_DONE, 5000);
	}

	private void stopIfQueuesEmpty() {
		if (mSyncQueue.isEmpty() && toScan.isEmpty()) {
			this.stopSelf();
		}
	}

	private class ScanQueueItem {
		public Uri castMediaUri;
		public String contentType;

		public ScanQueueItem(Uri castMediaUri, String contentType) {
			this.castMediaUri = castMediaUri;
			this.contentType = contentType;
		}
	}

	/**
	 * Scans the media database to see if the given item is currently there. If
	 * it is, update the cast media to point to the local content: URI for it.
	 *
	 * @param context
	 * @param castMedia
	 *            Local URI to the cast.
	 * @param pubUri
	 *            public URI to the media file.
	 * @return local URI if it exists.
	 * @throws SyncException
	 */
	public String checkForMediaEntry(Uri castMedia, Uri pubUri, String mimeType)
			throws SyncException {
		final ContentResolver cr = getContentResolver();

		String newLocUri = null;
		final File destfile = getFilePath(pubUri);

		if (mimeType == null) {
			throw new SyncException("missing MIME type");
		}

		String[] projection;
		String selection;
		Uri contentUri;

		if (mimeType.startsWith("image/")) {
			projection = new String[] { Images.Media._ID, Images.Media.DATA };
			selection = Images.Media.DATA + "=?";
			contentUri = Images.Media.EXTERNAL_CONTENT_URI;

		} else if (mimeType.startsWith("video/")) {
			projection = new String[] { Video.Media._ID, Video.Media.DATA };
			selection = Video.Media.DATA + "=?";
			contentUri = Video.Media.EXTERNAL_CONTENT_URI;

		} else {
			throw new SyncException("unknown MIME type: '" + mimeType + "'");
		}

		final String[] selectionArgs = { destfile.getAbsolutePath() };

		final Cursor mediaEntry = cr.query(contentUri, projection, selection,
				selectionArgs, null);
		try {
			if (mediaEntry.moveToFirst()) {
				newLocUri = ContentUris.withAppendedId(
						contentUri,
						mediaEntry.getLong(mediaEntry
								.getColumnIndex(BaseColumns._ID))).toString();
			}
		} finally {
			mediaEntry.close();
		}

		if (newLocUri != null) {
			updateCastMediaLocalUri(castMedia, newLocUri, mimeType);
		} else {
			Log.e(TAG, "The media provider doesn't seem to know about "
					+ destfile.getAbsolutePath()
					+ " which is on the filesystem. Strange...");
		}
		return newLocUri;
	}

	public class LocalBinder extends Binder {
		MediaSync getService() {
			return MediaSync.this;
		}
	}
}
