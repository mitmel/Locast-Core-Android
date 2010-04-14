package edu.mit.mel.locast.mobile.data;

import java.io.IOException;
import java.util.Arrays;
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

import android.app.Notification;
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
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.MainActivity;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.StreamUtils;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkProtocolException;

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
	
	private void sync(Uri toSync) throws SyncException, IOException {
		JsonSyncableItem syncItem = null;
		final String contentType = getApplicationContext().getContentResolver().getType(toSync);
		if (MediaProvider.TYPE_COMMENT_DIR.equals(contentType)
				|| MediaProvider.TYPE_COMMENT_ITEM.equals(contentType)){
			syncItem = new Comment();
			
		}else if (MediaProvider.TYPE_CONTENT_DIR.equals(contentType)
				|| MediaProvider.TYPE_CONTENT_ITEM.equals(contentType)){
			syncItem = new Cast();
			
		}else if (MediaProvider.TYPE_PROJECT_DIR.equals(contentType)
				|| MediaProvider.TYPE_PROJECT_ITEM.equals(contentType)){
			syncItem = new Project();
		}
		sync(toSync, syncItem);
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
	private void sync(Uri toSync, JsonSyncableItem sync) throws SyncException, IOException{
		JSONArray remObjs;
		syncdItems = new TreeSet<Uri>();
		
		final String contentType = getApplicationContext().getContentResolver().getType(toSync);
		if (contentType.startsWith("vnd.android.cursor.dir")){
		
			try {
				// special case for FeatureCollections
				if (sync instanceof Cast){
					final JSONObject featureCollection = nc.getObject(MediaProvider.getPublicPath(cr, toSync));
					remObjs = featureCollection.getJSONArray("features");
				}else {
					remObjs = nc.getArray(MediaProvider.getPublicPath(cr, toSync));
				}
				for (int i = 0; i < remObjs.length(); i++){
					syncItem(toSync, null, JsonSyncableItem.fromJSON(getApplicationContext(), null, remObjs.getJSONObject(i), sync.getSyncMap()), sync);
				}
			} catch (final SyncException se){
				throw se;
			} catch (final Exception e1) {
				final SyncException se = new SyncException("Sync error: " + e1.getLocalizedMessage());
				se.initCause(e1);
				throw se;
			}
		}
		
		final Cursor c = cr.query(toSync, sync.getFullProjection(), null, null, null);
		Log.d("LocastSync", "have " + c.getCount() + " items to sync");
		for (c.moveToFirst(); ! c.isAfterLast(); c.moveToNext()){
			try {
				syncItem(toSync, c, null, sync);
				
			}catch (final SyncItemDeletedException side){
				side.printStackTrace();
				continue;
			}
		}
		c.close();
	}

	/**
	 * Given a live cursor pointing to a data item and/or a set of contentValues loaded from the network,
	 * attempt to sync. 
	 * Either c or cvNet can be null, but not both. 
	 * 
	 * @param c A cursor pointing to the data item. Null is OK here.
	 * @param cvNet Values of the data item as it exists on the network side. Null is OK here.
	 * @param sync An empty JsonSyncableItem object.
	 * @return True if the item has been modified on either end.
	 * @throws IOException 
	 */
	private boolean syncItem(Uri toSync, Cursor c, ContentValues cvNet, JsonSyncableItem sync) throws SyncException, IOException {
		boolean modified = false;
		boolean needToCloseCursor = false;

		Uri locUri = null;

		final String contentType = getApplicationContext().getContentResolver().getType(toSync);
		
		if (c != null) {
			if (contentType.startsWith("vnd.android.cursor.dir")){
				locUri = ContentUris.withAppendedId(toSync, 
						c.getLong(c.getColumnIndex(JsonSyncableItem._ID)));
			}else{
				locUri = toSync;
			}

			// skip any items already sync'd
			if (syncdItems.contains(locUri)) {
				return false;
			}
			sync.onPreSyncItem(cr, locUri, c);
		}

		// when the PUBLIC_ID is null, that means it's only local
		final int pubIdColumn = (c != null) ? c.getColumnIndex(JsonSyncableItem.PUBLIC_ID) : -1;
		if (c != null && (c.isNull(pubIdColumn) || c.getInt(pubIdColumn) == 0)){
			// new content on the local side only. Gotta publish.

			try { 
				Uri postHereUri;
				if (contentType.startsWith("vnd.android.cursor.item")){
					postHereUri = sync.getContentUri();
				}else{
					postHereUri = toSync;
				}
				final HttpResponse hr = nc.post(MediaProvider.getPublicPath(cr, postHereUri), JsonSyncableItem.toJSON(getApplicationContext(), locUri, c, sync.getSyncMap()).toString());
					
				if (! hr.containsHeader("Content-Type")  
						|| ! hr.getHeaders("Content-Type")[0].getValue().equals(NetworkClient.JSON_MIME_TYPE)) {
					throw new NetworkProtocolException("Got wrong response content-type from posting a sync'd item. Got "+(hr.containsHeader("Content-Type")? "'" + hr.getHeaders("Content-Type")[0].getValue() + "'" : "no header")+"; was expecting "+NetworkClient.JSON_MIME_TYPE, hr);
				}
				
				// The response from a post to create a new item should be the newly created item,
				// which contains the public ID that we need.
				final HttpEntity entity = hr.getEntity();
				final ContentValues cvUpdate = JsonSyncableItem.fromJSON(getApplicationContext(), locUri, new JSONObject(StreamUtils.inputStreamToString(entity.getContent())), sync.getSyncMap());
				// as there is no public ID yet, the item can't be updated using the URI
				cr.update(locUri, cvUpdate, null, null);

				modified = true;

			} catch (final Exception e) {
				final SyncException se = new SyncException("Problem posting new sync'd object.");
				se.initCause(e);
				throw se;
			}

			// only on the remote side, so pull it in.
		}else if (c == null && cvNet != null) {
			c = cr.query(toSync, 
					sync.getFullProjection(), 
					JsonSyncableItem.PUBLIC_ID+"="+cvNet.getAsLong(JsonSyncableItem.PUBLIC_ID), null, null);
			c.moveToFirst();
			needToCloseCursor = true;
			if (c.getCount() == 0){
				locUri = cr.insert(toSync, cvNet);
				modified = true;
			}else {
				locUri = ContentUris.withAppendedId(toSync, 
						c.getLong(c.getColumnIndex(JsonSyncableItem._ID)));
				sync.onPreSyncItem(cr, locUri, c);
			}
		}
		
		// we've now found data on both sides, so sync them.
		if (! modified && c != null){
			final long pubId = c.getLong(c.getColumnIndex(JsonSyncableItem.PUBLIC_ID));
			
			final String path = MediaProvider.getPublicPath(cr, toSync, pubId);
			try {
				
				if (cvNet == null){
					try{
						cvNet = JsonSyncableItem.fromJSON(getApplicationContext(), locUri, nc.getObject(path), sync.getSyncMap());
					}catch (final HttpResponseException hre){
						if (hre.getStatusCode() == HttpStatus.SC_NOT_FOUND){
							final SyncItemDeletedException side = new SyncItemDeletedException(locUri);
							side.initCause(hre);
							throw side;
						}
					}
				}
				final Date netLastModified = new Date(cvNet.getAsLong(JsonSyncableItem.MODIFIED_DATE));
				final Date locLastModified = new Date(c.getLong(c.getColumnIndex(JsonSyncableItem.MODIFIED_DATE)));

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
					nc.putJson(path, JsonSyncableItem.toJSON(getApplicationContext(), locUri, c, sync.getSyncMap()));
					Log.d("LocastSync", cvNet + " is older than "+locUri);
					modified = true;
				}

			} catch (final JSONException e) {
				final SyncException se = new SyncException("Item sync error for path "+path+": invalid JSON.");
				se.initCause(e);
				throw se;
			} catch (final NetworkProtocolException e) {
				final SyncException se = new SyncException("Item sync error for path "+path+": "+ e.getHttpResponseMessage());
				se.initCause(e);
				throw se;
			}
		}
		
		if (needToCloseCursor) {
			c.close();
		}
		
		if (locUri == null) {
			throw new RuntimeException("Never got a local URI for a sync'd item.");
		}
		
		if (modified){
			sync.onUpdateItem(getApplicationContext(), locUri);
		}
		
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
    

    
    public void startSync(Uri... uri){
    	syncQueue.addAll(Arrays.asList(uri));
    	if (currentSyncTask == null || currentSyncTask.getStatus() == AsyncTask.Status.FINISHED){
    		currentSyncTask = new SyncTask();
    		currentSyncTask.execute(uri);
    	}
    }
    
	private class SyncTask extends AsyncTask<Uri, Integer, Boolean> {
		Exception e;
		Notification notification;
		NotificationManager nm;
		PendingIntent showMainScreen;
		private Date startTime;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			startTime = new Date();
			notification = new Notification(R.drawable.ico_notification, getText(R.string.sync_notification), System.currentTimeMillis());
			showMainScreen = PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT);
			notification.setLatestEventInfo(getApplicationContext(), getText(R.string.sync_notification), "Starting sync...", showMainScreen);
			
			nm = (NotificationManager) getApplication().getSystemService(NOTIFICATION_SERVICE);
			nm.notify(NOTIFICATION_SYNC, notification);
		}
		
		@Override
		protected Boolean doInBackground(Uri... params) {
			
			try {
				while (!syncQueue.isEmpty()){
					final Uri toSync = syncQueue.remove();
					notification.setLatestEventInfo(getApplicationContext(), getText(R.string.sync_notification), "Syncing " + toSync.getPath(), showMainScreen);
					
					nm.notify(NOTIFICATION_SYNC, notification);
					Sync.this.sync(toSync);
					
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
	}
}
