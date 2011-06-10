package edu.mit.mobile.android.locast.data;
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
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.notifications.ProgressNotification;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.browser.BrowserHome;
import edu.mit.mobile.android.utils.LastUpdatedMap;

/**
 * A Simple Sync engine. Can do naive bi-directional sync with a server that exposes
 * a last modified or created date and a unique ID via a JSON API.
 *
 * To use, simply extend JsonSyncableItem and create the sync map. This mapping tells
 * the engine what JSON object properties sync to what local columns, as well as how
 * to recreate any objects for updating/creation.
 *
 *
 * @author stevep
 *
 */
public class Sync extends Service {
	public final static String TAG = "LocastSync";

	private static final boolean DEBUG = Constants.DEBUG;

	public final static String ACTION_CANCEL_SYNC = "edu.mit.mobile.android.locast.ACTION_CANCEL_SYNC";

	/**
	 * When a sync request is a result of direct user action (eg. pressing a "sync" button),
	 * set this boolean extra to true.
	 */
	public final static String
		EXTRA_EXPLICIT_SYNC = "edu.mit.mobile.android.locast.EXTRA_EXPLICIT_SYNC";

	/**
	 * If syncing a server URI that is destined for a specific local URI space, add the destination URI here.
	 */
	public final static String
		EXTRA_DESTINATION_URI = "edu.mit.mobile.android.locast.EXTRA_DESTINATION_URI";

	public final static String
		EXTRA_DESTINATION_TYPE = "edu.mit.mobile.android.locast.EXTRA_DESTINATION_TYPE";

	private static final String CONTENT_TYPE_PREFIX_DIR = "vnd.android.cursor.dir";

	private static final long
		TIMEOUT_MAX_ITEM_WAIT = (long) (30 * 1e9), // nanoseconds
		TIMEOUT_AUTO_SYNC_MINIMUM = (long) (30 * 1e9); // nanoseconds

	private static final int
		TIMEOUT_SERVICE_AUTODESTRUCT = 60 * 1000; // milliseconds

	private final IBinder mBinder = new LocalBinder();
	private NetworkClient nc;
	private ContentResolver cr;
	//private Set<Uri> syncdItems;
	private final LastUpdatedMap<Uri> mLastUpdated = new LastUpdatedMap<Uri>(TIMEOUT_AUTO_SYNC_MINIMUM);
	private boolean mNotifiedUserAboutNetworkStatus = true;
	private boolean mShouldAlertUserOnSuccess = false;

	protected final PriorityBlockingQueue<SyncQueueItem> syncQueue = new PriorityBlockingQueue<SyncQueueItem>();

	private SyncTask currentSyncTask = null;

	private static int NOTIFICATION_SYNC = 0;

	private final BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())){
				final NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

				// reset this when the network status changes.
				mNotifiedUserAboutNetworkStatus = true;
				switch(networkInfo.getState()){
				case DISCONNECTED:
				case DISCONNECTING:
					if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)){
						stopSync();
					}

					break;

				case CONNECTED:
					startSync();
					break;

				}
			}
		}
	};

	/**
	 * Stops the synchronization.
	 */
	private void stopSync(){
		if (DEBUG) {
			Log.d(TAG, "Stopping sync");
		}
		if(currentSyncTask != null){
			currentSyncTask.cancel(true);
			Toast.makeText(getApplicationContext(), R.string.error_sync_canceled, Toast.LENGTH_LONG).show();
			currentSyncTask = null;
		}
		syncQueue.clear();
		scheduleSelfDestruct();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null){
			if (Intent.ACTION_SYNC.equals(intent.getAction())){
				if (intent.getBooleanExtra(EXTRA_EXPLICIT_SYNC, false)){
					mNotifiedUserAboutNetworkStatus = true;
					mShouldAlertUserOnSuccess = true;
				}
				startSync(intent.getData(), intent.getExtras());
			}else if (ACTION_CANCEL_SYNC.equals(intent.getAction())){
				stopSync();
			}
		}else{
			// restarted by system.
			startSync();
		}

		return START_NOT_STICKY;
	}

    @Override
    public void onCreate() {
    	super.onCreate();

		nc = NetworkClient.getInstance(this);
		cr = getApplicationContext().getContentResolver();

		registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(networkStateReceiver);
    }

    private JsonSyncableItem getSyncItem(Uri toSync){
    	JsonSyncableItem syncItem;
    	// XXX the below system needs to be reworked.
    	final String contentType = getApplicationContext().getContentResolver().getType(toSync);
		if (MediaProvider.TYPE_COMMENT_DIR.equals(contentType)
				|| MediaProvider.TYPE_COMMENT_ITEM.equals(contentType)){
			syncItem = new Comment();

		}else if (MediaProvider.TYPE_CAST_DIR.equals(contentType)
				|| MediaProvider.TYPE_CAST_ITEM.equals(contentType)){
			syncItem = new Cast();

		}else if (MediaProvider.TYPE_ITINERARY_DIR.equals(contentType)
				|| MediaProvider.TYPE_ITINERARY_ITEM.equals(contentType)){
			syncItem = new Itinerary();

		}else if (MediaProvider.TYPE_CASTMEDIA_DIR.equals(contentType)
				|| MediaProvider.TYPE_CASTMEDIA_ITEM.equals(contentType)){
			syncItem = new CastMedia();

		}else if (MediaProvider.TYPE_EVENT_DIR.equals(contentType)
				|| MediaProvider.TYPE_EVENT_ITEM.equals(contentType)){
			syncItem = new Event();

		}else{
			throw new RuntimeException("URI " + toSync + " is syncable, but there is no type mapping for it in getSyncItem().");
		}
		return syncItem;
    }

	private void sync(Uri toSync, SyncProgressNotifier syncProgress, Bundle extras) throws SyncException, IOException {
		Uri localUri;
		if ("http".equals(toSync.getScheme()) || "https".equals(toSync.getScheme())){
			if (!extras.containsKey(EXTRA_DESTINATION_URI)){
				throw new IllegalArgumentException("missing EXTRA_DESTINATION_URI when syncing HTTP URIs");
			}

			localUri = (Uri) extras.get(EXTRA_DESTINATION_URI);
			if (!MediaProvider.canSync(localUri)){
				throw new IllegalArgumentException("URI " + toSync + " is not syncable.");
			}
		}else{
			localUri = toSync;
		}
		if (!MediaProvider.canSync(localUri)){
			throw new IllegalArgumentException("URI " + toSync + " is not syncable.");
		}

		sync(toSync, getSyncItem(localUri), syncProgress, extras);
	}
	/**
	 * Sync the given URI to the server. Will compare dates and do a two-way sync.
	 * Does not yet handle the case where both server and and local are modified
	 * (collisions)
	 *
	 * @param toSync URI of the object to sync. Generally, this should be the dir URI
	 * @param sync A new object that extends JsonSyncableItem. This is needed for the JSON
	 * serialization routine in it, as well as the sync map.
	 * @throws IOException
	 */
	private void sync(Uri toSync, JsonSyncableItem sync, SyncProgressNotifier syncProgress, Bundle extras) throws SyncException, IOException{

		if ("http".equals(toSync.getScheme()) || "https".equals(toSync.getScheme())){
			final String netPath = toSync.getPath();
			toSync = (Uri) extras.get(EXTRA_DESTINATION_URI);
			final String contentType = getContentResolver().getType(toSync);

			if (contentType.startsWith(CONTENT_TYPE_PREFIX_DIR)){
				syncNetworkList(toSync, netPath, sync, syncProgress);
			}else{
				syncNetworkItem(toSync, netPath, sync, syncProgress);
			}
		}else{
			final String contentType = getApplicationContext().getContentResolver().getType(toSync);

			final Cursor c = cr.query(toSync, sync.getFullProjection(), null, null, null);
			try {
				syncProgress.addPendingTasks(c.getCount());

				// Handle a list of items.
				if (contentType.startsWith(CONTENT_TYPE_PREFIX_DIR)){
					// load from the network first...
					syncNetworkList(toSync, MediaProvider.getPublicPath(this, toSync), sync, syncProgress);
				}

				// then load locally.

				if (DEBUG) {
					Log.d(TAG, "have " + c.getCount() + " local items to sync");
				}
				for (c.moveToFirst(); (currentSyncTask != null && !currentSyncTask.isCancelled()) && ! c.isAfterLast(); c.moveToNext()){
					try {
						syncItem(toSync, c, null, sync, syncProgress);
						syncProgress.completeTask();

					}catch (final SyncItemDeletedException side){
						if (DEBUG) {
							Log.d(TAG, side.getLocalizedMessage() + " Deleting...");
						}
						cr.delete(side.getItem(), null, null);
						//side.printStackTrace();
						syncProgress.completeTask();
						continue;
					}
				}
			}finally{
				c.close();
			}
		}
	}

	private void syncNetworkList(Uri toSync, String netPath, JsonSyncableItem sync, SyncProgressNotifier syncProgress) throws SyncException{
		JSONArray remObjs;

		try {
			remObjs = nc.getArray(netPath);
			// TODO figure out how to use getContentResolver().bulkInsert(url, values); for this:
			syncProgress.addPendingTasks(remObjs.length());

			for (int i = 0; (currentSyncTask != null && !currentSyncTask.isCancelled()) && i < remObjs.length(); i++){
				final JSONObject jo = remObjs.getJSONObject(i);
				syncItem(toSync, null, jo, sync, syncProgress);
				syncProgress.completeTask();
			}
		} catch (final SyncException se){
			throw se;
		} catch (final Exception e1) {
			String message = e1.getLocalizedMessage();
			if (message == null){
				message = "caused by " + e1.getClass().getCanonicalName();
			}
			final SyncException se = new SyncException("Sync error: " + message);
			se.initCause(e1);
			throw se;
		}
	}

	private void syncNetworkItem(Uri toSync, String netPath, JsonSyncableItem sync, SyncProgressNotifier syncProgress) throws SyncException{
		JSONObject remObj;
		try {
			remObj = nc.getObject(netPath);
			syncProgress.addPendingTasks(1);

			syncItem(toSync, null, remObj, sync, syncProgress);
			syncProgress.completeTask();

		} catch (final SyncException se){
			throw se;
		} catch (final Exception e1) {
			String message = e1.getLocalizedMessage();
			if (message == null){
				message = "caused by " + e1.getClass().getCanonicalName();
			}
			final SyncException se = new SyncException("Sync error: " + message);
			se.initCause(e1);
			throw se;
		}
	}

	/**
	 * Given a live cursor pointing to a data item and/or a set of contentValues loaded from the network,
	 * attempt to sync.
	 * Either c or cvNet can be null, but not both.
	 *
	 * @param c A cursor pointing to the data item. Null is OK here.
	 * @param jsonObject JSON object for the item as loaded from the network. null is OK here.
	 * @param sync An empty JsonSyncableItem object.
	 * @return True if the item has been modified on either end.
	 * @throws IOException
	 */
	private boolean syncItem(Uri toSync, Cursor c, JSONObject jsonObject, JsonSyncableItem sync, SyncProgressNotifier syncProgress) throws SyncException, IOException {
		boolean modified = false;
		boolean needToCloseCursor = false;
		boolean toSyncIsIndex = false;
		final SyncMap syncMap = sync.getSyncMap();

		Uri locUri = null;
		final Uri origToSync = toSync;
		ContentValues cvNet = null;

		final Context context = getApplicationContext();
		final ContentResolver cr = context.getContentResolver();
		if (jsonObject != null){
			if ("http".equals(toSync.getScheme()) || "https".equals(toSync.getScheme())){
				// we successfully loaded it from the 'net, but toSync is really for local URIs. Erase it.

				toSync = sync.getContentUri();
				if (toSync == null){
					if (DEBUG) {
						Log.w(TAG, "cannot get local URI for "+origToSync+". Skipping...");
					}
					return false;
				}
			}
			try {
				cvNet = JsonSyncableItem.fromJSON(context, null, jsonObject, syncMap);
			}catch (final Exception e){
				final SyncException se = new SyncException("Problem loading JSON object.");
				se.initCause(e);
				throw se;
			}
		}

		final String contentType = cr.getType(toSync);

		if (c != null) {
			if (contentType.startsWith(CONTENT_TYPE_PREFIX_DIR)){
				locUri = ContentUris.withAppendedId(toSync,
						c.getLong(c.getColumnIndex(JsonSyncableItem._ID)))
						.buildUpon().query(null).build();
				toSyncIsIndex = true;
			}else{
				locUri = toSync;
			}

			// skip any items already sync'd
			if (mLastUpdated.isUpdatedRecently(locUri)) {
				return false;
			}

			final int draftCol = c.getColumnIndex(TaggableItem._DRAFT);
			if (draftCol != -1 && c.getInt(draftCol) != 0){
				if (DEBUG) {
					Log.d(TAG, locUri + " is marked a draft. Not syncing.");
				}
				return false;
			}

			syncMap.onPreSyncItem(cr, locUri, c);
		}else if (contentType.startsWith(CONTENT_TYPE_PREFIX_DIR)){
			// strip any query strings
			toSync = toSync.buildUpon().query(null).build();
		}
//		if (c != null){
//			MediaProvider.dumpCursorToLog(c, sync.getFullProjection());
//		}
		// when the PUBLIC_URI is null, that means it's only local
		final int pubUriColumn = (c != null) ? c.getColumnIndex(JsonSyncableItem._PUBLIC_URI) : -1;
		if (c != null && (c.isNull(pubUriColumn) || c.getString(pubUriColumn) == "")){
			// new content on the local side only. Gotta publish.

			try {
				jsonObject = JsonSyncableItem.toJSON(context, locUri, c, syncMap);

				final String publicPath = MediaProvider.getPostPath(this, locUri);
				if (DEBUG) {
					Log.d(TAG, "Posting "+locUri + " to " + publicPath);
				}

				// The response from a post to create a new item should be the newly created item,
				// which contains the public ID that we need.
				jsonObject = nc.postJson(publicPath, jsonObject);

				final ContentValues cvUpdate = JsonSyncableItem.fromJSON(context, locUri, jsonObject, syncMap);
				if (cr.update(locUri, cvUpdate, null, null) == 1){
					// at this point, server and client should be in sync.
					mLastUpdated.markUpdated(locUri);
					if (DEBUG) {
						Log.i(TAG, "Hooray! "+ locUri + " has been posted succesfully.");
					}

				}else{
					Log.e(TAG, "update of "+locUri+" failed");
				}
				modified = true;

			} catch (final Exception e) {
				final SyncException se = new SyncException(getString(R.string.error_sync_no_post));
				se.initCause(e);
				throw se;
			}

			// only on the remote side, so pull it in.
		}else if (c == null && cvNet != null) {
			final String[] params = {cvNet.getAsString(JsonSyncableItem._PUBLIC_URI)};
			c = cr.query(toSync,
					sync.getFullProjection(),
					JsonSyncableItem._PUBLIC_URI+"=?", params, null);
			needToCloseCursor = true;

			if (! c.moveToFirst()){
				locUri = cr.insert(toSync, cvNet);
				modified = true;
			}else {
				locUri = ContentUris.withAppendedId(toSync,
						c.getLong(c.getColumnIndex(JsonSyncableItem._ID))).buildUpon().query(null).build();
				syncMap.onPreSyncItem(cr, locUri, c);
			}
		}

		// we've now found data on both sides, so sync them.
		if (! modified && c != null){
			final String publicPath = c.getString(c.getColumnIndex(JsonSyncableItem._PUBLIC_URI));

			try {

				if (cvNet == null){
					try{
						if (publicPath == null && toSyncIsIndex && ! MediaProvider.canSync(locUri)){

							// At this point, we've already checked the index and it doesn't contain the item (otherwise it would be in the syncdItems).
							// If we can't sync individual items, it's possible that the index is paged or the item has been deleted.
							if (DEBUG) {
								Log.w(TAG, "Asked to sync "+locUri+" but item wasn't in server index and cannot sync individual entries. Skipping and hoping it is up to date.");
							}
							return false;

						}else{
							if (mLastUpdated.isUpdatedRecently(nc.getFullUri(publicPath))){
								if (DEBUG) {
									Log.d(TAG, "already sync'd! "+publicPath);
								}
								return false;
							}
							if (jsonObject == null){
								jsonObject = nc.getObject(publicPath);
							}
							cvNet = JsonSyncableItem.fromJSON(context, locUri, jsonObject, syncMap);

						}
					}catch (final HttpResponseException hre){
						if (hre.getStatusCode() == HttpStatus.SC_NOT_FOUND){
							final SyncItemDeletedException side = new SyncItemDeletedException(locUri);
							side.initCause(hre);
							throw side;
						}
					}
				}
				if (cvNet == null){
					Log.e(TAG, "got null values from fromJSON() on item " + locUri +": " +(jsonObject != null ? jsonObject.toString(): "<< no json object >>"));
					return false;
				}
				final Date netLastModified = new Date(cvNet.getAsLong(JsonSyncableItem._MODIFIED_DATE));
				final Date locLastModified = new Date(c.getLong(c.getColumnIndex(JsonSyncableItem._MODIFIED_DATE)));

				if (netLastModified.equals(locLastModified)){
					// same! yay! We don't need to do anything.
					if (DEBUG) {
						Log.d("LocastSync", locUri + " doesn't need to sync.");
					}
				}else if (netLastModified.after(locLastModified)){
					// remote is more up to date, update!
					cr.update(locUri, cvNet, null, null);
					if (DEBUG) {
						Log.d("LocastSync", cvNet + " is newer than "+locUri);
					}
					modified = true;

				}else if (netLastModified.before(locLastModified)){
					// local is more up to date, propagate!
					jsonObject = nc.putJson(publicPath, JsonSyncableItem.toJSON(context, locUri, c, syncMap));

					if (DEBUG) {
						Log.d("LocastSync", cvNet + " is older than "+locUri);
					}
					modified = true;
				}
				mLastUpdated.markUpdated(nc.getFullUri(publicPath));
			} catch (final JSONException e) {
				final SyncException se = new SyncException("Item sync error for path "+publicPath+": invalid JSON.");
				se.initCause(e);
				throw se;
			} catch (final NetworkProtocolException e) {
				final SyncException se = new SyncException("Item sync error for path "+publicPath+": "+ e.getHttpResponseMessage());
				se.initCause(e);
				throw se;
			}finally{
				if (needToCloseCursor) {
					c.close();
					needToCloseCursor = false;
				}
			}
		}

		if (needToCloseCursor) {
			c.close();
		}

		if (locUri == null) {
			throw new RuntimeException("Never got a local URI for a sync'd item.");
		}

		// two calls are made in two different contexts. Which context you use depends on the application.
		syncMap.onPostSyncItem(context, locUri, jsonObject, modified);
		sync.onPostSyncItem(context, locUri, jsonObject, modified);

		mLastUpdated.markUpdated(locUri);

		// needed for things that may have requested a sync with a different URI than what was eventually produced.
		if (origToSync != locUri){
			mLastUpdated.markUpdated(origToSync);
			cr.notifyChange(origToSync, null);
		}

		return modified;
	}

	/**
	 * Retrieve an object from the server
	 * @param context
	 * @param path
	 * @param sync
	 * @throws SyncException
	 * @throws IOException
	 */
	public static void loadItemFromServer(Context context, String path, JsonSyncableItem sync) throws SyncException, IOException {
		ContentValues cvNet;
		final NetworkClient nc = NetworkClient.getInstance(context);
		try {
			cvNet = JsonSyncableItem.fromJSON(context, null, nc.getObject(path), sync.getSyncMap());
		} catch (final JSONException e) {
			final SyncException se = new SyncException("Item sync error for path "+path+": invalid JSON.");
			se.initCause(e);
			throw se;
		} catch (final NetworkProtocolException e) {
			final SyncException se = new SyncException("Item sync error for path "+path+": "+ e.getHttpResponseMessage());
			se.initCause(e);
			throw se;
		}

		context.getContentResolver().insert(sync.getContentUri(), cvNet);
	}

	@Override
	public IBinder onBind(Intent intent) {

		return mBinder;
	}

    public class LocalBinder extends Binder {
        Sync getService(){
                return Sync.this;
        }
    }

    private boolean isDataConnected(){
    	// a check is done here as well as startSync() to avoid the queue getting filled when the network is down.
    	final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    	final NetworkInfo ni = cm.getActiveNetworkInfo();
    	if (ni == null || !ni.isConnected()){
    		if (mNotifiedUserAboutNetworkStatus){
    			Toast.makeText(this, R.string.error_sync_no_data_network, Toast.LENGTH_LONG).show();
    			mNotifiedUserAboutNetworkStatus = false;
    		}
    		if (DEBUG) {
				Log.d(TAG, "not synchronizing, as it appears that there's no network connection");
			}
    		return false;
    	}else{
    		return ni.isConnected();
    	}

    }

    /**
     * Start the sync, if it isn't already started.
     */
    public void startSync(){
    	if (!isDataConnected()){
    		return;
    	}

    	if (currentSyncTask == null || currentSyncTask.getStatus() == AsyncTask.Status.FINISHED){
    		currentSyncTask = new SyncTask();
    		currentSyncTask.execute();
    	}
    }

    /**
     * Adds the given URI to the sync queue and starts the sync.
     *
     * @param uri
     */
    public void startSync(Uri uri, Bundle extras){
    	if (!isDataConnected()){
    		return;
    	}

    	if (! syncQueue.contains(uri)){
    		if (!mLastUpdated.isUpdatedRecently(uri) || (extras != null && extras.containsKey(EXTRA_EXPLICIT_SYNC))){
    			if (DEBUG) {
					Log.d(TAG, "enqueing " + uri + " to sync queue");
				}
    			syncQueue.add(new SyncQueueItem(uri, extras));
    		}else{
    			if (DEBUG) {
					Log.d(TAG, "NOT enqueing "+ uri + " to sync queue, as it's been updated recently");
				}
    		}

    	}else{
    		if (DEBUG) {
				Log.d(TAG, "NOT enqueing " + uri + " to sync queue, as it's already present");
			}
    	}

    	startSync();
    }

	private class SyncTask extends AsyncTask<Void, Void, Boolean> implements SyncProgressNotifier {
		private Exception e;
		private ProgressNotification notification;
		private NotificationManager nm;
		private Date startTime;

		private int mTaskTotal = 0;
		private int mTaskCompleted = 0;

		private static final int
			MSG_PROGRESS_ADD_TASKS      = 0,
			MSG_PROGRESS_COMPLETE_TASKS = 1;

		// this is used, as publishProgress is not very good about garbage collector churn.
		private final Handler mProgressHandler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				switch(msg.what){
				case MSG_PROGRESS_ADD_TASKS:
					mTaskTotal += msg.arg1;
					break;

				case MSG_PROGRESS_COMPLETE_TASKS:
					mTaskCompleted += msg.arg1;
					break;
				}
				updateProgressBars();
			};
		};

		private void updateProgressBars(){
			if (!isCancelled()){
				notification.setProgress(mTaskTotal, mTaskCompleted);
				nm.notify(NOTIFICATION_SYNC, notification);
			}
		}


		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			startTime = new Date();
			final Context context = getApplicationContext();
			notification = new ProgressNotification(getApplicationContext(), R.drawable.stat_notify_sync);

			notification.contentIntent = PendingIntent.getActivity(context, 0,
					new Intent(context, BrowserHome.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
					PendingIntent.FLAG_UPDATE_CURRENT);
			notification.setProgress(0, 0);
			notification.setTitle(getText(R.string.sync_notification));
			nm = (NotificationManager) getApplication().getSystemService(NOTIFICATION_SERVICE);
			nm.notify(NOTIFICATION_SYNC, notification);

		}

		@Override
		protected Boolean doInBackground(Void... params) {

			try {
				while (!syncQueue.isEmpty()){
					if (DEBUG) {
						Log.d(TAG, syncQueue.size() + " items in the sync queue");
					}
					final SyncQueueItem toSync = syncQueue.remove();
					if (toSync.isStale()){
						if (DEBUG) {
							Log.i(TAG, toSync + " is stale; skipping");
						}
						continue;
					}
					Sync.this.sync(toSync.uri, this, toSync.extras);

				}
			} catch (final SyncException e) {
				e.printStackTrace();
				this.e = e;
				return false;
			} catch (final IOException e) {
				this.e = e;
				e.printStackTrace();
				return false;
			}

			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result){
				// only show a notification if the user has been waiting for a bit.
				if (mShouldAlertUserOnSuccess && (new Date().getTime() - startTime.getTime()) > 10000){
					Toast.makeText(getApplicationContext(), R.string.sync_success, Toast.LENGTH_LONG).show();
				}
			}else{
				if (e instanceof SyncException){
					Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();

				}else if (e instanceof IOException){
					Toast.makeText(getApplicationContext(), R.string.error_sync, Toast.LENGTH_LONG).show();
				}
			}
			mShouldAlertUserOnSuccess = false;

			nm.cancel(NOTIFICATION_SYNC);
			scheduleSelfDestruct();
		}

		public void addPendingTasks(int taskCount) {
			mProgressHandler.obtainMessage(MSG_PROGRESS_ADD_TASKS, taskCount, 0).sendToTarget();
		}

		public void completeTask() {
			mProgressHandler.obtainMessage(MSG_PROGRESS_COMPLETE_TASKS, 1, 0).sendToTarget();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			if (nm != null){
				nm.cancel(NOTIFICATION_SYNC);
			}
		}
	}

	private void stopIfQueuesEmpty(){
		if (syncQueue.isEmpty()){
			stopSelf();
		}
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

	private void scheduleSelfDestruct() {
		mDoneTimeout.removeMessages(MSG_DONE);
		mDoneTimeout.sendEmptyMessageDelayed(MSG_DONE, TIMEOUT_SERVICE_AUTODESTRUCT);
	}

	public interface SyncProgressNotifier {
		/**
		 * Mark an added task as completed.
		 */
		public void completeTask();
		/**
		 * Adds a number of tasks to be completed.
		 * @param taskCount
		 */
		public void addPendingTasks(int taskCount);
	}

	private class SyncQueueItem implements Comparable<SyncQueueItem>{
		public SyncQueueItem(Uri uri, Bundle extras) {
			this.uri = uri;
			this.extras = extras;
			this.when = System.nanoTime();
		}

		public boolean isStale(){
			return (System.nanoTime() - when) >= TIMEOUT_MAX_ITEM_WAIT;
		}

		final Uri uri;
		final Bundle extras;
		final long when;

		/**
		 * This compare function orders newest items first.
		 *  (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(SyncQueueItem another) {
			return -Long.valueOf(when).compareTo(Long.valueOf(another.when));
		}

	}
}
