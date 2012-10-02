package edu.mit.mobile.android.locast.sync;

/*
 * Copyright (C) 2011-2012  MIT Mobile Experience Lab
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
import org.json.JSONObject;

import android.accounts.Account;
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
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticator;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.data.Titled;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkClient.InputStreamWatcher;
import edu.mit.mobile.android.locast.net.NotificationProgressListener;
import edu.mit.mobile.android.locast.notifications.ProgressNotification;
import edu.mit.mobile.android.utils.StreamUtils;

public abstract class AbsMediaSync extends Service implements MediaScannerConnectionClient {
    private final static String TAG = AbsMediaSync.class.getSimpleName();

    /*
     * public interface
     */

    /**
     * Syncs the media resources of the item specified by the data uri. If data is null, then all
     * unpublished media will be sync'd.
     */
    public static final String ACTION_SYNC_RESOURCES = "edu.mit.mobile.android.locast.ACTION_SYNC_RESOURCES";

    /*
     * Constants that control this class.
     */

    private final boolean DEBUG = Constants.DEBUG;

    public static final long TIMEOUT_LAST_SYNC = 10 * 1000 * 1000; // nanoseconds

    private static final long SELF_DESTRUCT_TIMEOUT = 5000;

    /**
     * The location to store media on the device. This will be hidden from being indexed for the
     * gallery.
     */
    public final static String DEVICE_EXTERNAL_MEDIA_PATH = "/locast/";

    public final static String NO_MEDIA = ".nomedia";

    /**
     * If this many errors are encountered, media sync gives up.
     */
    private static final int TOO_MANY_ERRORS = 5;

    /*
     * Private state
     */

    private final Map<String, ScanQueueItem> mScanMap = new TreeMap<String, ScanQueueItem>();
    private MediaScannerConnection mMsc;
    private final Queue<String> mToScan = new LinkedList<String>();

    private final IBinder mBinder = new LocalBinder();

    private final ConcurrentLinkedQueue<SyncQueueItem> mSyncQueue = new ConcurrentLinkedQueue<SyncQueueItem>();

    private MessageDigest mDigest;

    private final HashMap<Uri, Long> mRecentlySyncd = new HashMap<Uri, Long>();

    private ContentResolver mCr;

    /**
     * Creates a new Media Sync engine.
     */
    public AbsMediaSync() {
        super();

        try {
            mDigest = MessageDigest.getInstance("SHA-1");
        } catch (final NoSuchAlgorithmException e) {
            e.printStackTrace();
            mDigest = null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mCr = getContentResolver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mSyncTask != null) {
            mSyncTask.cancel(true);
            mSyncTask = null;
        }

        if (mMsc != null) {
            mMsc.disconnect();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
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
        if (ACTION_SYNC_RESOURCES.equals(intent.getAction())) {
            final Uri data = intent.getData();
            if (data == null) {
                try {
                    enqueueUnpublishedMedia();
                } catch (final SyncException e) {
                    Log.e(TAG, "cannot sync request URL", e);
                }

            } else {
                enqueueItem(data, intent.getExtras());
            }
        } else {
            Log.e(TAG, "Media Sync was told to start with an unhandled intent: " + intent);
        }

        return START_REDELIVER_INTENT;
    }

    private SyncableProvider getSyncableProvider(Uri uri) {
        return (SyncableProvider) mCr.acquireContentProviderClient(uri).getLocalContentProvider();
    }

    public abstract void enqueueUnpublishedMedia() throws SyncException;

    public String[] getCastMediaProjection() {
        return CASTMEDIA_PROJECTION;
    }

    private static final String SELECTION_UNPUBLISHED_CAST_MEDIA = CastMedia.COL_MEDIA_URL
            + " ISNULL AND " + CastMedia.COL_PUBLIC_URL + " NOT NULL AND "
            + CastMedia.COL_LOCAL_URL + " NOT NULL";

    public void enqueueUnpublishedMedia(Uri castMedia) throws SyncException {

        final SyncableProvider provider = getSyncableProvider(castMedia);

        if (provider == null) {
            throw new SyncException("provider must run from same thread as media sync");
        }

        final JsonSyncableItem c = provider.getWrappedContentItem(castMedia, mCr.query(castMedia,
                getCastMediaProjection(), SELECTION_UNPUBLISHED_CAST_MEDIA, null, null));

        try {
            if (!(c instanceof CastMedia)) {
                throw new SyncException(castMedia + " cannot be synchronized with MediaSync");
            }
            c.moveToFirst();

            if (Constants.DEBUG) {
                Log.d(TAG, "enqueue unpublished media");
                Log.d(TAG, "there are " + c.getCount() + " unpublished media");
            }

            for (; !c.isAfterLast(); c.moveToNext()) {
                final Uri item = c.getCanonicalUri();
                enqueueItem(item, null);
            }
        } finally {
            c.close();
        }
    }

    private void enqueueItem(Uri item, Bundle extras) {
        final SyncQueueItem syncQueueItem = new SyncQueueItem(item, extras);
        if (!mSyncQueue.contains(syncQueueItem) && !checkRecentlySyncd(item)) {
            if (DEBUG) {
                Log.d(TAG, "enqueueing " + syncQueueItem);
            }
            mSyncQueue.add(syncQueueItem);
        } else {
            if (DEBUG) {
                Log.d(TAG, syncQueueItem.toString() + " already in the queue. Skipping.");
            }
        }

        maybeStartSyncTask();
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

    private SyncTask mSyncTask;

    /**
     * Starts a sync task if there isn't one already going.
     */
    private synchronized void maybeStartSyncTask() {
        if (mSyncTask == null || mSyncTask.isCancelled()) {
            mSyncTask = new SyncTask();
            mSyncTask.execute();
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
            int errorCount = 0;
            try {
                while (!mSyncQueue.isEmpty()) {
                    final SyncQueueItem qi = mSyncQueue.remove();
                    try {
                        syncItemMedia(qi.uri);
                        addUriToRecentlySyncd(qi.uri);

                    } catch (final SyncException se) {
                        Log.e(TAG, se.getLocalizedMessage(), se);
                        errorCount++;
                        // re-add failed items to retry
                        mSyncQueue.add(qi);

                    } catch (final IllegalArgumentException e) {
                        Log.e(TAG, e.getLocalizedMessage(), e);
                        errorCount++;
                        // re-add failed items to retry
                        mSyncQueue.add(qi);
                    }

                    if (errorCount >= TOO_MANY_ERRORS) {
                        Log.e(TAG, "Too many errors. Stopping sync.");
                        break;
                    }
                }

            } finally {
                scheduleSelfDestruct();
                mSyncTask = null;
            }
            return null;
        }

        @Override
        protected void onCancelled() {
            mSyncTask = null;
        }
    }

    public abstract boolean getKeepOffline(Uri castMediaUri, CastMedia castMedia);

    /**
     * Implement this to retrieve the account to use to sync.
     * {@link AbsLocastAuthenticator#getFirstAccount(Context, String)} may be useful here.
     *
     * @return
     */
    public abstract Account getAccount();

    public abstract Uri getTitledItemForCastMedia(Uri castMedia);

    final static String[] CASTMEDIA_PROJECTION = { CastMedia._ID, CastMedia.COL_MIME_TYPE,
            CastMedia.COL_LOCAL_URL, CastMedia.COL_MEDIA_URL, CastMedia.COL_KEEP_OFFLINE,
            CastMedia.COL_PUBLIC_URL, CastMedia.COL_THUMB_LOCAL };

    /**
     * Synchronize the media of the given castMedia. It will download or upload as needed.
     *
     * Blocks until the sync is complete.
     *
     * @param castMediaUri
     *            a {@link CastMedia} item uri
     * @throws SyncException
     */
    public void syncItemMedia(Uri castMediaUri) throws SyncException {

        final SyncableProvider provider = getSyncableProvider(castMediaUri);
        if (provider == null) {
            Log.e(TAG,
                    "could not sync item media: could not get local binder for syncable provider");
            return;
        }

        final CastMedia castMedia = (CastMedia) provider.getWrappedContentItem(castMediaUri,
                mCr.query(castMediaUri, getCastMediaProjection(), null, null, null));

        try {
            if (!castMedia.moveToFirst()) {
                throw new IllegalArgumentException("uri " + castMediaUri + " has no content");
            }

            // cache the column numbers
            final int mediaUrlCol = castMedia.getColumnIndex(CastMedia.COL_MEDIA_URL);
            final int localUriCol = castMedia.getColumnIndex(CastMedia.COL_LOCAL_URL);

            final boolean keepOffline = castMedia.getInt(castMedia
                    .getColumnIndex(CastMedia.COL_KEEP_OFFLINE)) != 0;

            final String mimeType = castMedia.getString(castMedia
                    .getColumnIndex(CastMedia.COL_MIME_TYPE));

            final boolean isImage = (mimeType != null) && mimeType.startsWith("image/");

            // we don't need to sync this
            if ("text/html".equals(mimeType)) {
                return;
            }

            final Uri locMedia = castMedia.isNull(localUriCol) ? null : Uri.parse(castMedia
                    .getString(localUriCol));
            final String pubMedia = castMedia.getString(mediaUrlCol);
            final boolean hasLocMedia = locMedia != null && new File(locMedia.getPath()).exists();
            final boolean hasPubMedia = pubMedia != null && pubMedia.length() > 0;

            final String localThumb = castMedia.getString(castMedia
                    .getColumnIndex(CastMedia.COL_THUMB_LOCAL));

            if (hasLocMedia && !hasPubMedia) {
                final String uploadPath = castMedia.getString(castMedia
                        .getColumnIndex(CastMedia.COL_PUBLIC_URL));
                if (uploadPath == null) {
                    Log.w(TAG, "attempted to sync " + castMediaUri + " which has a null uploadPath");
                    return;
                }
                uploadMedia(uploadPath, castMediaUri, getTitledItemForCastMedia(castMediaUri),
                        mimeType, locMedia);

            } else if (!hasLocMedia && hasPubMedia) {
                // only have a public copy, so download it and store locally.
                final Uri pubMediaUri = Uri.parse(pubMedia);
                final File destfile = getFilePath(pubMediaUri);

                // the following conditions indicate that the cast media should be downloaded.
                if (keepOffline || getKeepOffline(castMediaUri, castMedia)) {
                    final boolean anythingChanged = downloadMediaFile(pubMedia, destfile,
                            castMediaUri);

                    // the below is inverted from what seems logical, because downloadMediaFile()
                    // will actually update the castmedia if it downloads anything. We'll only be
                    // getting here if we don't have any local record of the file, so we should make
                    // the association by ourselves.
                    if (!anythingChanged) {
                        File thumb = null;
                        if (isImage && localThumb == null) {
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
            castMedia.close();
        }
    }

    /**
     * updates the metadata stored at castMediaUri with information about the local file.
     *
     * @param castMediaUri
     *            a castMedia item pointing to the metadata for the media
     * @param localFile
     *            the local copy of the media file
     * @param localThumbnail
     *            an image file representing media stored at localFile or null if there is none
     */
    private void updateLocalFile(Uri castMediaUri, File localFile, File localThumbnail) {
        final ContentValues cv = new ContentValues();
        cv.put(CastMedia.COL_LOCAL_URL, Uri.fromFile(localFile).toString());
        if (localThumbnail != null) {
            cv.put(CastMedia.COL_THUMB_LOCAL, Uri.fromFile(localFile).toString());
        }
        cv.put(SyncableProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

        getContentResolver().update(castMediaUri, cv, null, null);

    }

    /**
     * Uploads the media to the server and shows a notification in the system toolbar.
     *
     * @param uploadPath
     *            the relative path to upload
     * @param castMediaUri
     * @param contentType
     * @param locMedia
     * @throws SyncException
     */
    private void uploadMedia(String uploadPath, Uri castMediaUri, Uri titledItem,
            String contentType, final Uri locMedia) throws SyncException {
        // upload
        try {
            // TODO this should get the account info from something else.
            final NetworkClient nc = NetworkClient.getInstance(this, getAccount());

            final JSONObject updatedCastMedia = nc.uploadContentWithNotification(this, titledItem,
                    uploadPath, locMedia, contentType, NetworkClient.UploadType.RAW_POST);

            final ContentValues cv = CastMedia.fromJSON(this, castMediaUri, updatedCastMedia,
                    CastMedia.SYNC_MAP);

            cv.put(SyncableProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

            mCr.update(castMediaUri, cv, null, null);
        } catch (final Exception e) {
            final SyncException se = new SyncException(getString(R.string.error_uploading_media));
            se.initCause(e);
            throw se;
        }
    }

    /**
     * Gets, makes and verifies that the location is writable. Also checks that the special .nomedia
     * file that tells Android to not index the path is present.
     *
     * @return the location to save locast media.
     * @throws SyncException
     */
    private File getSaveLocation() throws SyncException {
        final File sdcardPath = Environment.getExternalStorageDirectory();

        final File locastSavePath = new File(sdcardPath, DEVICE_EXTERNAL_MEDIA_PATH);
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
                final SyncException se = new SyncException("cannot create " + NO_MEDIA + " file");
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
     * Checks the local file system and checks to see if the given media resource has been
     * downloaded successfully already. If not, it will download it from the server and store it in
     * the filesystem. This uses the last-modified header and file length to determine if a media
     * resource is up to date.
     *
     * This method blocks for the course of the download, but shows a progress notification.
     *
     * @param pubUri
     *            the http:// uri of the public resource
     * @param saveFile
     *            the file that the resource will be saved to
     * @param castMediaUri
     *            the content:// uri of the cast
     * @return true if anything has changed. False if this function has determined it doesn't need
     *         to do anything.
     * @throws SyncException
     */
    public boolean downloadMediaFile(String pubUri, File saveFile, Uri castMediaUri)
            throws SyncException {
        final NetworkClient nc = NetworkClient.getInstance(this, getAccount());
        try {
            boolean dirty = true;
            // String contentType = null;

            if (saveFile.exists()) {
                final HttpResponse headRes = nc.head(pubUri);
                final long serverLength = Long.valueOf(headRes.getFirstHeader("Content-Length")
                        .getValue());
                // XXX should really be checking the e-tag too, but this will be
                // fine for our application.
                final Header remoteLastModifiedHeader = headRes.getFirstHeader("last-modified");

                long remoteLastModified = 0;
                if (remoteLastModifiedHeader != null) {
                    remoteLastModified = DateUtils.parseDate(remoteLastModifiedHeader.getValue())
                            .getTime();
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
                final Uri titledItem = getTitledItemForCastMedia(castMediaUri);
                String castTitle = Titled.getTitle(this, titledItem);
                if (castTitle == null) {
                    castTitle = "untitled";
                }

                final HttpResponse res = nc.get(pubUri);
                final HttpEntity ent = res.getEntity();
                final ProgressNotification notification = new ProgressNotification(this, getString(
                        R.string.sync_downloading, castTitle),
                        ProgressNotification.TYPE_DOWNLOAD, PendingIntent.getActivity(this, 0,
                                new Intent(Intent.ACTION_VIEW, titledItem)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0), false);

                final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                final NotificationProgressListener npl = new NotificationProgressListener(nm,
                        notification, ent.getContentLength(), 0);
                final InputStreamWatcher is = new InputStreamWatcher(ent.getContent(), npl);

                try {
                    if (DEBUG) {
                        Log.d(TAG,
                                "Downloading " + pubUri + " and saving it in "
                                        + saveFile.getAbsolutePath());
                    }
                    final FileOutputStream fos = new FileOutputStream(saveFile);
                    StreamUtils.inputStreamToOutputStream(is, fos);
                    fos.close();

                    // set the file's last modified to match the remote.
                    // We can check this later to see if everything is up to
                    // date.
                    final Header lastModified = res.getFirstHeader("last-modified");
                    if (lastModified != null) {
                        saveFile.setLastModified(DateUtils.parseDate(lastModified.getValue())
                                .getTime());
                    }

                    // contentType = ent.getContentType().getValue();

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
            final SyncException se = new SyncException("Error downloading content item.");
            se.initCause(e);
            throw se;
        }
        return false;
    }

    /**
     * Enqueues a media item to be scanned. onScanComplete() will be called once the item has been
     * scanned successfully.
     *
     * @param castMediaUri
     * @param filePath
     * @param contentType
     */
    public void scanMediaItem(Uri castMediaUri, String filePath, String contentType) {
        mScanMap.put(filePath, new ScanQueueItem(castMediaUri, contentType));

        if (mMsc == null) {
            mToScan.add(filePath);
            this.mMsc = new MediaScannerConnection(this, this);
            this.mMsc.connect();

        } else if (mMsc.isConnected()) {
            mMsc.scanFile(filePath, contentType);

            // if we're not connected yet, we need to remember what we want
            // scanned,
            // so that we can queue it up once connected.
        } else {
            mToScan.add(filePath);
        }
    }

    public void onScanCompleted(String path, Uri locMediaUri) {
        if (locMediaUri == null) {
            Log.e(TAG, "Scan failed for newly downloaded content: " + path);
            return;
        }

        final ScanQueueItem item = mScanMap.get(path);
        if (item == null) {
            Log.e(TAG, "Couldn't find media item (" + path
                    + ") in scan map, so we couldn't update any casts.");
            return;
        }

        updateCastMediaLocalUri(item.castMediaUri, locMediaUri.toString(), item.contentType);
    }

    private String sha1Sum(String data) {
        if (mDigest == null) {
            throw new RuntimeException("no message digest available");
        }
        final byte[] ba;
        synchronized (mDigest) {
            mDigest.update(data.toString().getBytes());
            ba = mDigest.digest();
        }
        final BigInteger bi = new BigInteger(1, ba);
        final String result = bi.toString(16);
        if (result.length() % 2 != 0) {
            return "0" + result;
        }
        return result;
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
    private String generateThumbnail(Uri castMedia, String mimeType, String locMedia)
            throws ThumbnailException {
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
                    "cannot generate thumbnail for item with MIME type: '" + mimeType + "'");
        }

        if (thumb == null) {
            throw new ThumbnailException("Android thumbnail generator returned null");
        }

        try {
            final File outFile = new File(getCacheDir(), "thumb" + sha1Sum(locMedia) + ".jpg");
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
                    Log.d(TAG, "generated thumbnail for " + locMedia + " and saved it in "
                            + outFile.getAbsolutePath());
                }
            }

            return Uri.fromFile(outFile).toString();
        } catch (final IOException ioe) {
            final ThumbnailException te = new ThumbnailException();
            te.initCause(ioe);
            throw te;
        }
    }

    private void updateCastMediaLocalUri(Uri castMedia, String locMedia, String mimeType) {

        final ContentValues cvCastMedia = new ContentValues();
        cvCastMedia.put(CastMedia.COL_LOCAL_URL, locMedia);
        cvCastMedia.put(SyncableProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);

        try {
            final String locThumb = generateThumbnail(castMedia, mimeType, locMedia);
            if (locThumb != null) {
                cvCastMedia.put(CastMedia.COL_THUMB_LOCAL, locThumb);
            }

        } catch (final ThumbnailException e) {
            Log.e(TAG,
                    "could not generate thumbnail for " + locMedia + ": " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        if (DEBUG) {
            Log.d(TAG, "new local uri " + locMedia + " for cast media " + castMedia);
        }
        getContentResolver().update(castMedia, cvCastMedia, null, null);
    }

    // TODO should this be on a separate thread?
    public void onMediaScannerConnected() {
        while (!mToScan.isEmpty()) {
            final String scanme = mToScan.remove();
            final ScanQueueItem item = mScanMap.get(scanme);
            this.mMsc.scanFile(scanme, item.contentType);
        }
        scheduleSelfDestruct();
    }

    private void scheduleSelfDestruct() {
        mDoneTimeout.removeMessages(MSG_DONE);
        mDoneTimeout.sendEmptyMessageDelayed(MSG_DONE, SELF_DESTRUCT_TIMEOUT);
    }

    private void stopIfQueuesEmpty() {
        if (mSyncQueue.isEmpty() && mToScan.isEmpty()) {
            this.stopSelf();
        }
    }

    /**
     * Scans the media database to see if the given item is currently there. If it is, update the
     * cast media to point to the local content: URI for it.
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

        final Cursor mediaEntry = cr.query(contentUri, projection, selection, selectionArgs, null);
        try {
            if (mediaEntry.moveToFirst()) {
                newLocUri = ContentUris.withAppendedId(contentUri,
                        mediaEntry.getLong(mediaEntry.getColumnIndex(BaseColumns._ID))).toString();
            }
        } finally {
            mediaEntry.close();
        }

        if (newLocUri != null) {
            updateCastMediaLocalUri(castMedia, newLocUri, mimeType);
        } else {
            Log.e(TAG,
                    "The media provider doesn't seem to know about " + destfile.getAbsolutePath()
                            + " which is on the filesystem. Strange...");
        }
        return newLocUri;
    }

    public class LocalBinder extends Binder {
        AbsMediaSync getService() {
            return AbsMediaSync.this;
        }
    }

    /**
     * Represents an item waiting to be scanned by the media scanner.
     *
     */
    private class ScanQueueItem {
        public Uri castMediaUri;
        public String contentType;

        public ScanQueueItem(Uri castMediaUri, String contentType) {
            this.castMediaUri = castMediaUri;
            this.contentType = contentType;
        }
    }

    /**
     * Represents an item in the sync queue. Equality is checked by comparing both URI and extras.
     *
     */
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
            return o == null ? false : (this.uri == null ? false : this.uri.equals(o2.uri)
                    && ((this.extras == null && o2.extras == null) || this.extras == null ? false
                            : this.extras.equals(o2.extras)));
        }

        @Override
        public String toString() {
            return SyncQueueItem.class.getSimpleName() + ": " + uri.toString()
                    + ((extras != null) ? " with extras " + extras : "");
        }
    }
}
