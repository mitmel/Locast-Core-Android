package edu.mit.mobile.android.locast.data;

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
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkClient.InputStreamWatcher;
import edu.mit.mobile.android.locast.net.NotificationProgressListener;
import edu.mit.mobile.android.locast.notifications.ProgressNotification;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.utils.StreamUtils;

public class MediaSync extends Service implements MediaScannerConnectionClient{
	private final static String TAG = MediaSync.class.getSimpleName();

	private final Map<String, ScanQueueItem> scanMap = new TreeMap<String, ScanQueueItem>();
	private MediaScannerConnection msc;
	private final Queue<String> toScan = new LinkedList<String>();

	public static final String ACTION_SYNC_RESOURCES = "edu.mit.mobile.android.locast.ACTION_SYNC_RESOURCES";

	private final IBinder mBinder = new LocalBinder();

	public static final long TIMEOUT_LAST_SYNC = 10 * 1000 * 1000; // nanoseconds

	public final static String DEVICE_EXTERNAL_MEDIA_PATH = "/locast/";

	protected final ConcurrentLinkedQueue<SyncQueueItem> mSyncQueue = new ConcurrentLinkedQueue<SyncQueueItem>();

	private MessageDigest mDigest;

	private final HashMap<Uri, Long> mRecentlySyncd = new HashMap<Uri, Long>();

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

	private final Handler mDoneTimeout = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
			case MSG_DONE:
				stopIfQueuesEmpty();
				break;
			}
		}
	};

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	final Uri data = intent.getData();
    	Log.d(TAG, "onStartCommand()");
    	final SyncQueueItem syncQueueItem = new SyncQueueItem(data, intent.getExtras());
    	if (!mSyncQueue.contains(syncQueueItem) && ! checkRecentlySyncd(data)){
    		Log.d(TAG, "enqueueing " + syncQueueItem);
    		mSyncQueue.add(syncQueueItem);
    	}else{
    		Log.d(TAG, syncQueueItem.toString() + " already in the queue. Skipping.");
    	}

    	maybeStartTask();

    	return START_NOT_STICKY;
    }

    private SyncTask mSyncTask;

    private synchronized void maybeStartTask(){
    	if (mSyncTask == null){
    		mSyncTask = new SyncTask();
    		mSyncTask.execute();
    	}
    }

    @Override
    public void onDestroy() {
    	Log.d(TAG, "onDestroy()");
    	super.onDestroy();

    	if (mSyncTask != null){
    		mSyncTask.cancel(true);
    	}

    	if (msc != null){
    		msc.disconnect();
    	}
    }

    /**
     * @param uri
     * @return true if the item has been synchronized recently
     */
    private boolean checkRecentlySyncd(Uri uri){
    	synchronized (mRecentlySyncd) {
    		final Long lastSyncd = mRecentlySyncd.get(uri);
			if (lastSyncd != null){
				return (System.nanoTime() - lastSyncd) < TIMEOUT_LAST_SYNC;
			}else{
				return false;
			}
		}
    }

    private void addUriToRecentlySyncd(Uri uri){
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
    private class SyncTask extends AsyncTask<Void, Void, Void>{

		@Override
		protected Void doInBackground(Void... params) {
			while (!mSyncQueue.isEmpty()){
				try {
					final SyncQueueItem qi = mSyncQueue.remove();

					syncItemMedia(qi.uri);
					addUriToRecentlySyncd(qi.uri);

				}catch (final SyncException se){
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
			return o == null ? false :
				(this.uri == null ? false : this.uri.equals(o2.uri)
					&& (( this.extras == null && o2.extras == null )
							|| this.extras == null ? false : this.extras.equals(o2.extras)));
		}

		@Override
		public String toString(){
			return SyncQueueItem.class.getSimpleName() + ": "+ uri.toString() + ((extras != null) ? " with extras " + extras : "");
		}
	}

	/**
	 * Synchronize the media of the given castMedia. It will download or upload as needed.
	 *
	 * @param castMediaUri
	 * @throws SyncException
	 */
	public void syncItemMedia(Uri castMediaUri) throws SyncException{
		final ContentResolver cr = getContentResolver();

		final String[] PROJECTION = {CastMedia._ID, CastMedia._MIME_TYPE, CastMedia._LOCAL_URI, CastMedia._MEDIA_URL};
		final Cursor castMedia = cr.query(castMediaUri, PROJECTION, null, null, null);
		if (!castMedia.moveToFirst()){
			throw new IllegalArgumentException("uri "+castMediaUri+" has no content");
		}

		try {


			// cache the column numbers
			final int mediaUrlCol = castMedia.getColumnIndex(CastMedia._MEDIA_URL);
			final int localUriCol = castMedia.getColumnIndex(CastMedia._LOCAL_URI);

			final String mimeType = castMedia.getString(castMedia.getColumnIndex(CastMedia._MIME_TYPE));

			// we don't need to sync this
			if ("text/html".equals(mimeType)){
				return;
			}


			final Uri locMedia = castMedia.isNull(localUriCol) ? null : Uri.parse(castMedia.getString(localUriCol));
			final String pubMedia = castMedia.getString(mediaUrlCol);
			final boolean hasLocMedia = locMedia != null && new File(locMedia.getPath()).exists();
			final boolean hasPubMedia = pubMedia != null && pubMedia.length() > 0;

			if (hasLocMedia && !hasPubMedia){
				uploadMedia(castMediaUri, mimeType, locMedia);

			}else if (!hasLocMedia && hasPubMedia){
					// only have a public copy, so download it and store locally.
				final Uri pubMediaUri = Uri.parse(pubMedia);
				final File destfile = getFilePath(pubMediaUri);

				final boolean anythingChanged = downloadMediaFile(pubMedia, destfile, castMediaUri);
				if (!anythingChanged){
					checkForMediaEntry(castMediaUri, pubMediaUri, mimeType);
				}
			}
		}finally{
			castMedia.close();
		}
	}

	private void uploadMedia(Uri castMediaUri, String contentType, final Uri locMedia) throws SyncException {
		// upload
		try {
			final String uploadPath = "XXX broken";
			final NetworkClient nc = NetworkClient.getInstance(this);
			nc.uploadContentWithNotification(this,
					CastMedia.getCast(castMediaUri),
					uploadPath,
					locMedia,
					contentType);
		} catch (final Exception e){
			final SyncException se = new SyncException(getString(R.string.error_uploading_cast_video));
			se.initCause(e);
			throw se;
		}
		//Log.d(TAG, "Cast Media " + castMediaUri + " is " + castMedia.getString(mediaUrlCol));
	}

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
	 * This uses the last-modified header and file length to determine if a media resource is up to date.
	 *
	 * This method blocks for the course of the download, but shows a progress notification.
	 *
	 * @param pubUri the http:// uri of the public resource
	 * @param saveFile the file that the resource will be saved to
	 * @param castMediaUri the content:// uri of the cast
	 * @return true if anything has changed. False if this function has determined it doesn't need to do anything.
	 * @throws SyncException
	 */
	public boolean downloadMediaFile(String pubUri, File saveFile, Uri castMediaUri) throws SyncException {
		final NetworkClient nc = NetworkClient.getInstance(this);
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
				if (saveFile.length() == serverLength && saveFile.lastModified() >= remoteLastModified){
					Log.i(TAG, "Local copy of cast "+saveFile+" seems to be the same as the one on the server. Not re-downloading.");

					dirty = false;
				}
				// fall through and re-download, as we have a different size file locally.
			}
			if (dirty){
				final Uri castUri = CastMedia.getCast(castMediaUri);
				String castTitle = Cast.getTitle(this, castUri);
				if (castTitle == null){
					castTitle = "untitled";
				}

				final HttpResponse res = nc.get(pubUri);
				final HttpEntity ent = res.getEntity();
				final ProgressNotification notification = new ProgressNotification(this,
						getString(R.string.sync_downloading_cast, castTitle),
						ProgressNotification.TYPE_DOWNLOAD,
						PendingIntent.getActivity(this, 0, new Intent(Intent.ACTION_VIEW, castUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0),
						false);

				final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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

				final String filePath = saveFile.getAbsolutePath();
				scanMediaItem(castMediaUri, filePath, contentType);
				return true;
			}
		} catch (final Exception e) {
			final SyncException se = new SyncException("Error downloading content item.");
			se.initCause(e);
			throw se;
		}
		return false;
	}

	/**
	 * Enqueues a media item to be scanned.
	 * onScanComplete() will be called once the item has been scanned successfully.
	 *
	 * @param castMediaUri
	 * @param filePath
	 * @param contentType
	 */
	public void scanMediaItem(Uri castMediaUri, String filePath, String contentType){
		scanMap.put(filePath, new ScanQueueItem(castMediaUri, contentType));

		if (msc == null){
			toScan.add(filePath);
			this.msc = new MediaScannerConnection(this, this);
			this.msc.connect();

		}else if (msc.isConnected()){
			msc.scanFile(filePath, contentType);

		// if we're not connected yet, we need to remember what we want scanned,
		// so that we can queue it up once connected.
		}else{
			toScan.add(filePath);
		}
	}

	public void onScanCompleted(String path, Uri locMediaUri) {
		if (locMediaUri == null){
			Log.e(TAG, "Scan failed for newly downloaded content: "+path);
			return;
		}

		final ScanQueueItem item = scanMap.get(path);
		if (item == null){
			Log.e(TAG, "Couldn't find media item ("+path+") in scan map, so we couldn't update any casts.");
			return;
		}

		updateCastMediaLocalUri(item.castMediaUri, locMediaUri.toString(), item.contentType);
	}



	private String sha1Sum(String data){
		if (mDigest == null){
			throw new RuntimeException("no message digest available");
		}
		mDigest.reset();
		mDigest.update(data.getBytes());
		return new BigInteger(mDigest.digest()).toString(16);
	}

	// TODO this should probably look to see if the thumbnail file already exists for the given media, but should check for updates too.
	private String generateThumbnail(Uri castMedia, String mimeType, String locMedia) throws IOException {
		final long locId = ContentUris.parseId(Uri.parse(locMedia));

		Bitmap thumb = null;
		if (mimeType.startsWith("image/")){
			thumb = Images.Thumbnails.getThumbnail(getContentResolver(), locId, Images.Thumbnails.MINI_KIND, null);

		}else if (mimeType.startsWith("video/")){
			thumb = Video.Thumbnails.getThumbnail(getContentResolver(), locId, Video.Thumbnails.MINI_KIND, null);

		}else{
			throw new IllegalArgumentException("cannot generate thumbnail for item with MIME type: '"+mimeType+"'");
		}

		//Cursor getContentResolver().query(uri, projection, null, null, null);

		final File outFile = new File(getCacheDir(), "thumb" + sha1Sum(locMedia) + ".jpg");
		//final File outFile = File.createTempFile("thumb", ".jpg", getCacheDir());
		if (!outFile.exists()){
			if (!outFile.createNewFile()){
				throw new IOException("cannot create new file");
			}
			Log.d(TAG, "attempting to save thumb in "+outFile);
			final FileOutputStream fos = new FileOutputStream(outFile);
			thumb.compress(CompressFormat.JPEG, 75, fos);
			thumb.recycle();
			fos.close();

			Log.d(TAG, "generated thumbnail for "+locMedia + " and saved it in "+ outFile.getAbsolutePath());
		}

		return Uri.fromFile(outFile).toString();

	}

	private void updateCastMediaLocalUri(Uri castMedia, String locMedia, String mimeType){

		final ContentValues cvCastMedia = new ContentValues();
		cvCastMedia.put(CastMedia._LOCAL_URI, locMedia);
		cvCastMedia.put(MediaProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

		try {
			final String locThumb = generateThumbnail(castMedia, mimeType, locMedia);
			if (locThumb != null){
				cvCastMedia.put(CastMedia._THUMB_LOCAL, locThumb);
			}

		}catch (final IOException e) {
			Log.e(TAG,"could not generate thumbnail for " + locMedia + ": "+ e.getLocalizedMessage());
			e.printStackTrace();
		}


		Log.d(TAG, "new local uri " + locMedia + " for cast media " + castMedia);
		getContentResolver().update(castMedia, cvCastMedia, null, null);
	}

	// TODO should this be on a separate thread?
	public void onMediaScannerConnected() {
		while (!toScan.isEmpty()){
			final String scanme = toScan.remove();
			final ScanQueueItem item = scanMap.get(scanme);
			this.msc.scanFile(scanme, item.contentType);
		}
		scheduleSelfDestruct();
	}

	private void scheduleSelfDestruct(){
		mDoneTimeout.removeMessages(MSG_DONE);
		mDoneTimeout.sendEmptyMessageDelayed(MSG_DONE, 5000);
	}

	private void stopIfQueuesEmpty(){
		if (mSyncQueue.isEmpty() && toScan.isEmpty()){
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
	 * Scans the media database to see if the given item is currently there.
	 * If it is, update the cast media to point to the local content: URI for it.
	 *
	 * @param context
	 * @param castMedia Local URI to the cast.
	 * @param pubUri public URI to the media file.
	 * @return local URI if it exists.
	 * @throws SyncException
	 */
	public String checkForMediaEntry(Uri castMedia, Uri pubUri, String mimeType) throws SyncException {
		final ContentResolver cr = getContentResolver();

		String newLocUri = null;
		final File destfile = getFilePath(pubUri);

		if (mimeType == null){
			throw new SyncException("missing MIME type");
		}

		String[] projection;
		String selection;
		Uri contentUri;

		if (mimeType.startsWith("image/")){
			projection = new String[]{Images.Media._ID, Images.Media.DATA};
			selection = Images.Media.DATA + "=?";
			contentUri = Images.Media.EXTERNAL_CONTENT_URI;

		}else if (mimeType.startsWith("video/")){
			projection = new String[]{Video.Media._ID, Video.Media.DATA};
			selection = Video.Media.DATA + "=?";
			contentUri = Video.Media.EXTERNAL_CONTENT_URI;

		}else{
			throw new SyncException("unknown MIME type: '"+mimeType+"'");
		}

		final String[] selectionArgs = {destfile.getAbsolutePath()};

		final Cursor mediaEntry = cr.query(contentUri, projection, selection, selectionArgs, null);
		try {
			if (mediaEntry.moveToFirst()){
				newLocUri = ContentUris.withAppendedId(contentUri, mediaEntry.getLong(mediaEntry.getColumnIndex(BaseColumns._ID))).toString();
			}
		}finally{
			mediaEntry.close();
		}

		if (newLocUri != null){
			updateCastMediaLocalUri(castMedia, newLocUri, mimeType);
		}else{
			Log.e(TAG, "The media provider doesn't seem to know about "+destfile.getAbsolutePath()+" which is on the filesystem. Strange...");
		}
		return newLocUri;
	}


    public class LocalBinder extends Binder {
        MediaSync getService(){
                return MediaSync.this;
        }
    }
}
