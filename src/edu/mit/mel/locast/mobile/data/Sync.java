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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
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
import edu.mit.mel.locast.mobile.StreamUtils;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkClient;
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
public class Sync extends Service implements OnSharedPreferenceChangeListener {
	public final static String TAG = "LocastSync";

	public final static String ACTION_CANCEL_SYNC = "edu.mit.mel.locast.mobile.ACTION_CANCEL_SYNC";

	private final IBinder mBinder = new LocalBinder();
	private AndroidNetworkClient nc;
	private ContentResolver cr;
	private Set<Uri> syncdItems;
	protected final ConcurrentLinkedQueue<Uri> syncQueue = new ConcurrentLinkedQueue<Uri>();
	private SyncTask currentSyncTask = null;

	private static int NOTIFICATION_SYNC = 0;



	@Override
	public void onStart(Intent intent, int startId) {
		if (intent != null){
			if (Intent.ACTION_SYNC.equals(intent.getAction())){

				startSync(intent.getData());
			}else if (ACTION_CANCEL_SYNC.equals(intent.getAction())){
				if(currentSyncTask != null){
					currentSyncTask.cancel(true);
					Toast.makeText(getApplicationContext(), "Sync cancelled.", Toast.LENGTH_LONG).show();
				}else{
					Toast.makeText(getApplicationContext(), "Does not appear to currently be syncing", Toast.LENGTH_LONG).show();
				}
			}
		}else{
			// restarted by system.
			startSync();
		}
	}

    @Override
    public void onCreate() {
    	super.onCreate();

		nc = AndroidNetworkClient.getInstance(getApplicationContext());
		cr = getApplicationContext().getContentResolver();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
    		String key) {
    	// reload the network client.
    	nc = AndroidNetworkClient.getInstance(getApplicationContext());
    }

	private void sync(Uri toSync, SyncProgressNotifier syncProgress) throws SyncException, IOException {
		JsonSyncableItem syncItem = null;
		final String contentType = getApplicationContext().getContentResolver().getType(toSync);
		if (MediaProvider.TYPE_COMMENT_DIR.equals(contentType)
				|| MediaProvider.TYPE_COMMENT_ITEM.equals(contentType)){
			syncItem = new Comment();

		}else if (MediaProvider.TYPE_CAST_DIR.equals(contentType)
				|| MediaProvider.TYPE_CAST_ITEM.equals(contentType)){
			syncItem = new Cast();

		}else if (MediaProvider.TYPE_PROJECT_DIR.equals(contentType)
				|| MediaProvider.TYPE_PROJECT_ITEM.equals(contentType)){
			syncItem = new Project();
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
		JSONArray remObjs;
		syncdItems = new TreeSet<Uri>();

		final String contentType = getApplicationContext().getContentResolver().getType(toSync);

		final Cursor c = cr.query(toSync, sync.getFullProjection(), null, null, null);
		syncProgress.addPendingTasks(c.getCount());

		// Handle a list of items.
		if (contentType.startsWith("vnd.android.cursor.dir")){

			// load from the network first...
			try {
				remObjs = nc.getArray(MediaProvider.getPublicPath(cr, toSync));
				// TODO figure out how to use getContentResolver().bulkInsert(url, values); for this:
				syncProgress.addPendingTasks(remObjs.length());

				for (int i = 0; i < remObjs.length(); i++){
					final JSONObject jo = remObjs.getJSONObject(i);
					syncItem(toSync, null, jo, sync, syncProgress);
					syncProgress.completeTask();
				}
			} catch (final SyncException se){
				throw se;
			} catch (final Exception e1) {
				final SyncException se = new SyncException("Sync error: " + e1.getLocalizedMessage());
				se.initCause(e1);
				throw se;
			}
		}

		// then load locally.


		try {
			Log.d(TAG, "have " + c.getCount() + " local items to sync");
			for (c.moveToFirst(); ! c.isAfterLast(); c.moveToNext()){
				try {
					syncItem(toSync, c, null, sync, syncProgress);
					syncProgress.completeTask();

				}catch (final SyncItemDeletedException side){
					side.printStackTrace();
					syncProgress.completeTask();
					continue;
				}
			}
		}finally{
			c.close();
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
		ContentValues cvNet = null;
		if (jsonObject != null){
			try {
				cvNet = JsonSyncableItem.fromJSON(getApplicationContext(), null, jsonObject, syncMap);
			}catch (final Exception e){
				final SyncException se = new SyncException("Problem loading JSON object.");
				se.initCause(e);
				throw se;
			}
		}

		final String contentType = getApplicationContext().getContentResolver().getType(toSync);

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


			syncMap.onPreSyncItem(cr, locUri, c);
		}

		// when the PUBLIC_ID is null, that means it's only local
		final int pubIdColumn = (c != null) ? c.getColumnIndex(JsonSyncableItem._PUBLIC_ID) : -1;
		if (c != null && (c.isNull(pubIdColumn) || c.getInt(pubIdColumn) == 0)){
			// new content on the local side only. Gotta publish.

			try {
				jsonObject = JsonSyncableItem.toJSON(getApplicationContext(), locUri, c, syncMap);

				final String publicPath = MediaProvider.getPostPath(cr, locUri);
				Log.d(TAG, "Posting "+locUri + " to " + publicPath);
				final HttpResponse hr = nc.post(publicPath, jsonObject.toString());

				if (! hr.containsHeader("Content-Type")
						|| ! hr.getHeaders("Content-Type")[0].getValue().startsWith(NetworkClient.JSON_MIME_TYPE)) {
					throw new NetworkProtocolException("Got wrong response content-type from posting a sync'd item. Got "+(hr.containsHeader("Content-Type")? "'" + hr.getHeaders("Content-Type")[0].getValue() + "'" : "no header")+"; was expecting "+NetworkClient.JSON_MIME_TYPE, hr);
				}

				// The response from a post to create a new item should be the newly created item,
				// which contains the public ID that we need.
				final HttpEntity entity = hr.getEntity();
				try {
					jsonObject = new JSONObject(StreamUtils.inputStreamToString(entity.getContent()));
					final ContentValues cvUpdate = JsonSyncableItem.fromJSON(getApplicationContext(), locUri, jsonObject, syncMap);
					if (cr.update(locUri, cvUpdate, null, null) == 1){
						// at this point, server and client should be in sync.
						syncdItems.add(locUri);
						Log.i(TAG, "Hooray! "+ locUri + " has been posted succesfully.");

					}else{
						Log.e(TAG, "update of "+locUri+" failed");
					}
					modified = true;
				}finally{
					entity.consumeContent();
				}

			} catch (final Exception e) {
				final SyncException se = new SyncException(getString(R.string.error_sync_no_post));
				se.initCause(e);
				throw se;
			}

			// only on the remote side, so pull it in.
		}else if (c == null && cvNet != null) {
			c = cr.query(toSync,
					sync.getFullProjection(),
					JsonSyncableItem._PUBLIC_ID+"="+cvNet.getAsLong(JsonSyncableItem._PUBLIC_ID), null, null);
			c.moveToFirst();
			needToCloseCursor = true;
			if (c.getCount() == 0){
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
			final long pubId = c.getLong(c.getColumnIndex(JsonSyncableItem._PUBLIC_ID));

			final String publicPath = MediaProvider.getPublicPath(cr, toSync, pubId);
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
							cvNet = JsonSyncableItem.fromJSON(getApplicationContext(), locUri, jsonObject, syncMap);

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
					Log.e(TAG, "got null values from fromJSON() on item " + locUri +": " +jsonObject.toString());
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
					final HttpResponse hr = nc.putJson(publicPath, JsonSyncableItem.toJSON(getApplicationContext(), locUri, c, syncMap));
					hr.getEntity().consumeContent();
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

		if (modified){
			syncMap.onUpdateItem(getApplicationContext(), locUri, jsonObject);
		}
		syncMap.onPostSyncItem(getApplicationContext(), locUri, jsonObject);

		syncdItems.add(locUri);

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

    public void startSync(){

    	if (currentSyncTask == null || currentSyncTask.getStatus() == AsyncTask.Status.FINISHED){
    		currentSyncTask = new SyncTask();
    		currentSyncTask.execute();
    	}
    }

    public void startSync(Uri uri){
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
			notification.setProgress(mTaskTotal, mTaskCompleted);
			nm.notify(NOTIFICATION_SYNC, notification);
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
				// TODO Auto-generated catch block
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
