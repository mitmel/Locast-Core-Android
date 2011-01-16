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
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.MainActivity;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkProtocolException;
import edu.mit.mel.locast.mobile.notifications.ProgressNotification;

/**
 * A Simple Sync engine. Can do naive bi-directional sync with a server that exposes
 * a last modified or created date and a unique ID via a JSON API.
 *
 * To use, simply extend JsonSyncableItem and create the sync map. This mapping tells
 * the engine what JSON object properties sync to what local columns, as well
 *
 *
 * @author stevep
 *
 */
public class Sync extends Service {
	public final static String TAG = "LocastSync";

	public final static String ACTION_CANCEL_SYNC = "edu.mit.mel.locast.mobile.ACTION_CANCEL_SYNC";

	/**
	 * When a sync request is a result of direct user action (eg. pressing a "sync" button),
	 * set this boolean extra to true.
	 */
	public final static String
		EXTRA_EXPLICIT_SYNC = "edu.mit.mel.locast.mobile.EXTRA_EXPLICIT_SYNC";

	private final IBinder mBinder = new LocalBinder();
	private AndroidNetworkClient nc;
	private ContentResolver cr;
	private Set<Uri> syncdItems;
	private boolean mNotifiedUserAboutNetworkStatus = true;

	protected final ConcurrentLinkedQueue<Uri> syncQueue = new ConcurrentLinkedQueue<Uri>();
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
		Log.d(TAG, "Stopping sync");
		if(currentSyncTask != null){
			currentSyncTask.cancel(true);
			Toast.makeText(getApplicationContext(), R.string.error_sync_canceled, Toast.LENGTH_LONG).show();
			currentSyncTask = null;
		}
		syncQueue.clear();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		if (intent != null){
			if (Intent.ACTION_SYNC.equals(intent.getAction())){
				if (intent.getBooleanExtra(EXTRA_EXPLICIT_SYNC, false)){
					mNotifiedUserAboutNetworkStatus = true;
				}
				startSync(intent.getData());
			}else if (ACTION_CANCEL_SYNC.equals(intent.getAction())){
				stopSync();
			}
		}else{
			// restarted by system.
			startSync();
		}
	}

    @Override
    public void onCreate() {
    	super.onCreate();

		nc = AndroidNetworkClient.getInstance(this);
		cr = getApplicationContext().getContentResolver();

		registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(networkStateReceiver);
    }


	private void sync(Uri toSync, SyncProgressNotifier syncProgress) throws SyncException, IOException {
		JsonSyncableItem syncItem = null;
		if ("http".equals(toSync.getScheme()) || "https".equals(toSync.getScheme())){
			// XXX hack. This should really get the type from the server somehow.
			final List<String> path = toSync.getPathSegments();
			final int size = path.size();
			final String lastPath = size >= 1  ? path.get(size - 1): null;
			final String secondToLastPath = size >= 2 ? path.get(size - 2) : null;
			String type = null;
			try {
				Integer.parseInt(lastPath);
				type = secondToLastPath;
			}catch (final NumberFormatException ne){
				type = lastPath;
			}
			if (Cast.SERVER_PATH.startsWith(type)){
				syncItem = new Cast();
			}else if (Project.SERVER_PATH.startsWith(type)){
				syncItem = new Project();
			}else if (Comment.SERVER_PATH.startsWith(type)) {
				syncItem = new Comment();
			}else{
				throw new RuntimeException("Cannot determine the type of "+toSync);
			}
		}else{
			final String contentType = getApplicationContext().getContentResolver().getType(toSync);
			if (!MediaProvider.canSync(toSync)){
				throw new IllegalArgumentException("URI " + toSync + " is not syncable.");
			}
			if (MediaProvider.TYPE_COMMENT_DIR.equals(contentType)
					|| MediaProvider.TYPE_COMMENT_ITEM.equals(contentType)){
				syncItem = new Comment();

			}else if (MediaProvider.TYPE_CAST_DIR.equals(contentType)
					|| MediaProvider.TYPE_CAST_ITEM.equals(contentType)
					|| MediaProvider.TYPE_PROJECT_CAST_DIR.equals(contentType)
					|| MediaProvider.TYPE_PROJECT_CAST_ITEM.equals(contentType)){
				syncItem = new Cast();

			}else if (MediaProvider.TYPE_PROJECT_DIR.equals(contentType)
					|| MediaProvider.TYPE_PROJECT_ITEM.equals(contentType)){
				syncItem = new Project();

			}else{
				throw new RuntimeException("URI " + toSync + " is syncable, but don't know what type of object it is.");
			}
		}
		sync(toSync, syncItem, syncProgress);
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
	private void sync(Uri toSync, JsonSyncableItem sync, SyncProgressNotifier syncProgress) throws SyncException, IOException{
		syncdItems = new TreeSet<Uri>();

		if ("http".equals(toSync.getScheme()) || "https".equals(toSync.getScheme())){
			final List<String> path = toSync.getPathSegments();
			final int size = path.size();
			final String lastPath = size >= 1  ? path.get(size - 1): null;
			boolean isList;
			// XXX so much of a hack. OMG.
			try {
				Integer.parseInt(lastPath);
				isList = false;
			}catch (final NumberFormatException ne){
				isList = true;
			}

			if (isList){
				syncNetworkList(toSync, toSync.getPath(), sync, syncProgress);
			}else{
				syncNetworkItem(toSync, toSync.getPath(), sync, syncProgress);
			}
		}else{
			final String contentType = getApplicationContext().getContentResolver().getType(toSync);

			final Cursor c = cr.query(toSync, sync.getFullProjection(), null, null, null);
			syncProgress.addPendingTasks(c.getCount());

			// Handle a list of items.
			if (contentType.startsWith("vnd.android.cursor.dir")){
				// load from the network first...
				syncNetworkList(toSync, MediaProvider.getPublicPath(cr, toSync), sync, syncProgress);
			}

			// then load locally.


			try {
				Log.d(TAG, "have " + c.getCount() + " local items to sync");
				for (c.moveToFirst(); (currentSyncTask != null && !currentSyncTask.isCancelled()) && ! c.isAfterLast(); c.moveToNext()){
					try {
						syncItem(toSync, c, null, sync, syncProgress);
						syncProgress.completeTask();

					}catch (final SyncItemDeletedException side){
						Log.d(TAG, side.getLocalizedMessage() + " Deleting...");
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
			}
			try {
				cvNet = JsonSyncableItem.fromJSON(context, null, jsonObject, syncMap);
				// XXX remove this when the API updates
				Uri itemToGetPubPathOf = toSync;
				if (itemToGetPubPathOf == null){
					itemToGetPubPathOf = sync.getContentUri();
				}
				MediaProvider.translateIdToUri(context, cvNet, true, itemToGetPubPathOf);

			}catch (final Exception e){
				final SyncException se = new SyncException("Problem loading JSON object.");
				se.initCause(e);
				throw se;
			}
		}

		final String contentType = cr.getType(toSync);

		if (c != null) {
			if (contentType.startsWith("vnd.android.cursor.dir")){
				locUri = ContentUris.withAppendedId(toSync,
						c.getLong(c.getColumnIndex(JsonSyncableItem._ID)));
				toSyncIsIndex = true;
			}else{
				locUri = toSync;
			}

			// skip any items already sync'd
			if (syncdItems.contains(locUri)) {
				return false;
			}

			final int draftCol = c.getColumnIndex(TaggableItem._DRAFT);
			if (draftCol != -1 && c.getInt(draftCol) != 0){
				Log.d(TAG, locUri + " is marked a draft. Not syncing.");
				return false;
			}

			syncMap.onPreSyncItem(cr, locUri, c);
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

				final String publicPath = MediaProvider.getPostPath(cr, locUri);
				Log.d(TAG, "Posting "+locUri + " to " + publicPath);

				// The response from a post to create a new item should be the newly created item,
				// which contains the public ID that we need.
				jsonObject = nc.postJson(publicPath, jsonObject);

				final ContentValues cvUpdate = JsonSyncableItem.fromJSON(context, locUri, jsonObject, syncMap);
				if (cr.update(locUri, cvUpdate, null, null) == 1){
					// at this point, server and client should be in sync.
					syncdItems.add(locUri);
					Log.i(TAG, "Hooray! "+ locUri + " has been posted succesfully.");

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
						c.getLong(c.getColumnIndex(JsonSyncableItem._ID)));
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
							Log.w(TAG, "Asked to sync "+locUri+" but item wasn't in server index and cannot sync individual entries. Skipping and hoping it is up to date.");
							return false;

						}else{
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
					Log.d("LocastSync", locUri + " doesn't need to sync.");
				}else if (netLastModified.after(locLastModified)){
					// remote is more up to date, update!
					cr.update(locUri, cvNet, null, null);
					Log.d("LocastSync", cvNet + " is newer than "+locUri);
					modified = true;

				}else if (netLastModified.before(locLastModified)){
					// local is more up to date, propagate!
					jsonObject = nc.putJson(publicPath, JsonSyncableItem.toJSON(context, locUri, c, syncMap));

					Log.d("LocastSync", cvNet + " is older than "+locUri);
					modified = true;
				}

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

		syncMap.onPostSyncItem(context, sync, locUri, jsonObject, modified);

		syncdItems.add(locUri);

		// needed for things that may have requested a sync with a different URI than what was eventually produced.
		if (origToSync != locUri){
			syncdItems.add(origToSync);
			cr.notifyChange(origToSync, null);
		}

		return modified;
	}

	public static void loadItemFromServer(Context context, String path, JsonSyncableItem sync) throws SyncException, IOException {
		ContentValues cvNet;
		final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(context);
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
    		Log.d(TAG, "not synchronizing, as it appears that there's no network connection");
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
    public void startSync(Uri uri){
    	if (!isDataConnected()){
    		return;
    	}

    	if (! syncQueue.contains(uri)){
    		syncQueue.add(uri);
    		Log.d(TAG, "enqueing " + uri + " to sync queue");
    	}else{
    		Log.d(TAG, "NOT enqueing " + uri + " to sync queue, as it's already present");
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
					new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
					Log.d(TAG, syncQueue.size() + " items in the sync queue");
					final Uri toSync = syncQueue.remove();
					Sync.this.sync(toSync, this);

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
				if ((new Date().getTime() - startTime.getTime()) > 10000){
					Toast.makeText(getApplicationContext(), R.string.sync_success, Toast.LENGTH_LONG).show();
				}
			}else{
				if (e instanceof SyncException){
					Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();

				}else if (e instanceof IOException){
					Toast.makeText(getApplicationContext(), R.string.error_sync, Toast.LENGTH_LONG).show();
				}
			}

			nm.cancel(NOTIFICATION_SYNC);
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
}
