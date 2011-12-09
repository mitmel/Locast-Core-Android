package edu.mit.mobile.android.locast.sync;

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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.Comment;
import edu.mit.mobile.android.locast.data.Event;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.utils.LastUpdatedMap;
import edu.mit.mobile.android.utils.StreamUtils;

public class SyncEngine {
	private static final String TAG = SyncEngine.class.getSimpleName();

	/**
	 * If syncing a server URI that is destined for a specific local URI space,
	 * add the destination URI here.
	 */
	public final static String EXTRA_DESTINATION_URI = "edu.mit.mobile.android.locast.EXTRA_DESTINATION_URI";

	private static final String CONTENT_TYPE_PREFIX_DIR = "vnd.android.cursor.dir";

	private static final HashMap<String, Class<? extends JsonSyncableItem>> TYPE_MAP = new HashMap<String, Class<? extends JsonSyncableItem>>();

	private static final boolean DEBUG = Constants.DEBUG;

	static {
		TYPE_MAP.put(MediaProvider.TYPE_CAST_DIR, Cast.class);
		TYPE_MAP.put(MediaProvider.TYPE_CAST_ITEM, Cast.class);

		TYPE_MAP.put(MediaProvider.TYPE_CASTMEDIA_DIR, CastMedia.class);
		TYPE_MAP.put(MediaProvider.TYPE_CASTMEDIA_ITEM, CastMedia.class);

		TYPE_MAP.put(MediaProvider.TYPE_COMMENT_DIR, Comment.class);
		TYPE_MAP.put(MediaProvider.TYPE_COMMENT_ITEM, Comment.class);

		TYPE_MAP.put(MediaProvider.TYPE_ITINERARY_DIR, Itinerary.class);
		TYPE_MAP.put(MediaProvider.TYPE_ITINERARY_ITEM, Itinerary.class);

		TYPE_MAP.put(MediaProvider.TYPE_EVENT_DIR, Event.class);
		TYPE_MAP.put(MediaProvider.TYPE_EVENT_ITEM, Event.class);
	}

	private final Context mContext;
	private final NetworkClient mNetworkClient;

	private static final long TIMEOUT_MAX_ITEM_WAIT = (long) (30 * 1e9), // nanoseconds
			TIMEOUT_AUTO_SYNC_MINIMUM = (long) (60 * 1e9); // nanoseconds

	private final LastUpdatedMap<Uri> mLastUpdated = new LastUpdatedMap<Uri>(
			TIMEOUT_AUTO_SYNC_MINIMUM);

	private static final long AGE_EQUALITY_SLOP = 1000; // ms; the amount of
														// time that an age can
														// differ by and still
														// be considered equal.

	private static final String[] SYNC_PROJECTION = new String[] {

	JsonSyncableItem._ID,

	JsonSyncableItem._PUBLIC_URI,

	JsonSyncableItem._MODIFIED_DATE,

	JsonSyncableItem._SERVER_MODIFIED_DATE,

	JsonSyncableItem._CREATED_DATE

	};

	private static final String SELECTION_UNPUBLISHED = JsonSyncableItem._PUBLIC_URI + " ISNULL";

	final String[] PUB_URI_PROJECTION = new String[] { JsonSyncableItem._ID,
			JsonSyncableItem._PUBLIC_URI };

	private static int FORMAT_ARGS_DEBUG = android.text.format.DateUtils.FORMAT_SHOW_TIME
			| android.text.format.DateUtils.FORMAT_SHOW_YEAR
			| android.text.format.DateUtils.FORMAT_SHOW_DATE;

	public SyncEngine(Context context, NetworkClient networkClient) {
		mContext = context;
		mNetworkClient = networkClient;
	}

	public boolean sync(Uri toSync, Account account, Bundle extras, ContentProviderClient provider,
			SyncResult syncResult) throws RemoteException, SyncException, JSONException,
			IOException, NetworkProtocolException, NoPublicPath, OperationApplicationException {

		String pubPath = null;

		//
		// Handle http or https uris separately. These require the destination uri.
		//

		if ("http".equals(toSync.getScheme()) || "https".equals(toSync.getScheme())) {
			pubPath = toSync.toString();

			if (!extras.containsKey(EXTRA_DESTINATION_URI)) {
				throw new IllegalArgumentException(
						"missing EXTRA_DESTINATION_URI when syncing HTTP URIs");
			}
			toSync = Uri.parse(extras.getString(EXTRA_DESTINATION_URI));
		}

		final String type = provider.getType(toSync);
		final boolean isDir = type.startsWith(CONTENT_TYPE_PREFIX_DIR);

		final boolean manualSync = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);

		// skip any items already sync'd
		if (!manualSync && mLastUpdated.isUpdatedRecently(toSync)) {
			if (DEBUG){
				Log.d(TAG, "not syncing "+toSync+" as it's been updated recently");
			}
			return false;
		}

		// the sync map will convert the json data to ContentValues
		final SyncMap syncMap = getSyncMap(provider, toSync);



		final HashMap<String, SyncStatus> syncStatuses = new HashMap<String, SyncEngine.SyncStatus>();
		final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();
		final LinkedList<String> cpoPubUris = new LinkedList<String>();

		//
		// first things first, upload any content that needs to be uploaded.
		//

		uploadUnpublished(toSync, provider, syncMap, syncStatuses, syncResult);

		// this should ensure that all items have a pubPath when we query it below.

		try {
			if (pubPath == null) {
				// we should avoid calling this too much as it can be expensive
				pubPath = MediaProvider.getPublicPath(mContext, toSync);
			}
		} catch (final NoPublicPath e) {
			// if it's an item, we can handle it.
			if (isDir) {
				throw e;
			}
		}

		if (pubPath == null){

			// this should have been updated already by the initial upload, so something must be wrong
			throw new SyncException("never got a public path for "+ toSync);
		}

		if (DEBUG){
			Log.d(TAG, "sync(toSync="+toSync+", account="+account+", extras="+extras+", manualSync="+manualSync+",...)");
			Log.d(TAG, "pubPath: "+pubPath);
		}

		final long request_time = System.currentTimeMillis();

		final HttpResponse hr = mNetworkClient.get(pubPath);

		final long response_time = System.currentTimeMillis();

		// the time compensation below allows a time-based synchronization to
		// function even if the local clock is entirely wrong. The server's time
		// is extracted using the Date header and all are compared relative to
		// the respective clock reference. Any data that's stored on the mobile
		// should be stored relative to the local clock and the server will
		// respect the same.
		long serverTime;

		try {
			serverTime = getServerTime(hr);
		} catch (final DateParseException e) {
			Log.w(TAG,
					"could not retrieve date from server. Using local time, which may be incorrect.",
					e);
			serverTime = System.currentTimeMillis();
		}


		// TODO check out http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
		final long response_delay = response_time - request_time;
		if (DEBUG) {
			Log.d(TAG, "request took " + response_delay + "ms");
		}
		final long localTime = request_time;

		final long localOffset = (localTime - serverTime); // add this to the
															// server time to
															// get the local
															// time

		if (Math.abs(localOffset) > 30 * 60 * 1000) {
			Log.w(TAG, "local clock is off by " + localOffset + "ms");
		}

		final HttpEntity ent = hr.getEntity();

		Cursor c;
		if (isDir) {

			final JSONArray ja = new JSONArray(StreamUtils.inputStreamToString(ent.getContent()));
			ent.consumeContent();

			final int len = ja.length();
			final String[] selectionArgs = new String[len];

			// build the query to see which items are already in the database
			final StringBuilder selection = new StringBuilder();
			selection.append(JsonSyncableItem._PUBLIC_URI);
			selection.append(" in (");

			for (int i = 0; i < len; i++) {
				final SyncStatus syncStatus = loadItemFromJsonObject(ja.getJSONObject(i), syncMap,
						serverTime);

				syncStatuses.put(syncStatus.remote, syncStatus);

				selectionArgs[i] = syncStatus.remote;

				// add in a placeholder for the query
				selection.append('?');
				if (i != (len - 1)) {
					selection.append(',');
				}
			}
			selection.append(")");

			c = provider.query(toSync, SYNC_PROJECTION, selection.toString(), selectionArgs, null);
		} else {

			final JSONObject jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
			ent.consumeContent();
			final SyncStatus syncStatus = loadItemFromJsonObject(jo, syncMap, serverTime);

			syncStatuses.put(syncStatus.remote, syncStatus);

			c = provider.query(toSync, SYNC_PROJECTION, JsonSyncableItem._PUBLIC_URI + "=?",
					new String[] { syncStatus.remote }, null);
		}

		// these items are on both sides
		try {
			final int pubUriCol = c.getColumnIndex(JsonSyncableItem._PUBLIC_URI);
			final int localModifiedCol = c.getColumnIndex(JsonSyncableItem._MODIFIED_DATE);
			final int serverModifiedCol = c.getColumnIndex(JsonSyncableItem._SERVER_MODIFIED_DATE);
			final int idCol = c.getColumnIndex(JsonSyncableItem._ID);

			// All the items in this cursor should be found on both the client
			// and the server.
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
				final long id = c.getLong(idCol);
				final Uri localUri = ContentUris.withAppendedId(toSync, id);

				final String pubUri = c.getString(pubUriCol);

				final SyncStatus itemStatus = syncStatuses.get(pubUri);

				if (itemStatus.state == SyncState.ALREADY_UP_TO_DATE || itemStatus.state == SyncState.NOW_UP_TO_DATE){
					if (DEBUG){
						Log.d(TAG, pubUri + " is already up to date");
					}
					continue;
				}

				itemStatus.local = localUri;

				// make the status searchable by both remote and local uri
				syncStatuses.put(localUri.toString(), itemStatus);

				// last modified as stored in the DB, in phone time
				final long itemLocalModified = c.getLong(localModifiedCol);

				// last modified as stored in the DB, in server time
				final long itemServerModified = c.getLong(serverModifiedCol);
				final long localAge = localTime - itemLocalModified;

				final long remoteAge = serverTime - itemStatus.remoteModifiedTime;

				final long ageDifference = Math.abs(localAge - remoteAge);

				// up to date, as far remote -> local goes
				if (itemServerModified == itemStatus.remoteModifiedTime) {
					itemStatus.state = SyncState.ALREADY_UP_TO_DATE;
					if (DEBUG) {
						Log.d(TAG, pubUri + " is up to date");
					}

					// need to download
				} else if (localAge > remoteAge) {
					if (DEBUG) {
						final long serverModified = itemStatus.remoteModifiedTime;

						Log.d(TAG,
								pubUri
										+ " : local is "
										+ ageDifference
										+ "ms older ("
										+ android.text.format.DateUtils.formatDateTime(mContext,
												itemLocalModified, FORMAT_ARGS_DEBUG)
										+ ") than remote ("
										+ android.text.format.DateUtils.formatDateTime(mContext,
												serverModified, FORMAT_ARGS_DEBUG)
										+ "); updating local copy...");
					}

					itemStatus.state = SyncState.REMOTE_DIRTY;

					final ContentProviderOperation.Builder b = ContentProviderOperation
							.newUpdate(localUri);

					// update this so it's in the local timescale
					correctServerOffset(itemStatus.remoteCVs, JsonSyncableItem._CREATED_DATE,
							JsonSyncableItem._CREATED_DATE, localOffset);
					correctServerOffset(itemStatus.remoteCVs,
							JsonSyncableItem._SERVER_MODIFIED_DATE,
							JsonSyncableItem._MODIFIED_DATE, localOffset);

					b.withValues(itemStatus.remoteCVs);
					b.withExpectedCount(1);

					cpo.add(b.build());
					cpoPubUris.add(pubUri);

					syncResult.stats.numUpdates++;

					// need to upload
				} else if (localAge < remoteAge) {
					if (DEBUG) {
						final long serverModified = itemStatus.remoteModifiedTime;

						Log.d(TAG,
								pubUri
										+ " : local is "
										+ ageDifference
										+ "ms newer ("
										+ android.text.format.DateUtils.formatDateTime(mContext,
												itemLocalModified, FORMAT_ARGS_DEBUG)
										+ ") than remote ("
										+ android.text.format.DateUtils.formatDateTime(mContext,
												serverModified, FORMAT_ARGS_DEBUG)
										+ "); publishing to server...");
					}
					itemStatus.state = SyncState.LOCAL_DIRTY;

					mNetworkClient.putJson(pubPath, JsonSyncableItem.toJSON(mContext, localUri, c, syncMap));
				}

				mLastUpdated.markUpdated(localUri);

				syncResult.stats.numEntries++;
			} // end for
		} finally {

			c.close();
		}

		// apply bulk updates
		if (cpo.size() > 0) {
			if (DEBUG) {
				Log.d(TAG, "applying " + cpo.size() + " bulk updates...");
			}

			final ContentProviderResult[] r = provider.applyBatch(cpo);
			if (DEBUG) {
				Log.d(TAG, "Done applying updates. Running postSync handler...");
			}

			for (int i = 0; i < r.length; i++) {
				final ContentProviderResult res = r[i];
				final SyncStatus ss = syncStatuses.get(cpoPubUris.get(i));
				if (ss == null) {
					Log.e(TAG, "can't get sync status for " + res.uri);
					continue;
				}
				syncMap.onPostSyncItem(mContext, ss.local, ss.remoteJson,
						res.count != null ? res.count == 1 : true);

				ss.state = SyncState.NOW_UP_TO_DATE;
			}

			if (DEBUG) {
				Log.d(TAG, "done running postSync handler.");
			}

			cpo.clear();
			cpoPubUris.clear();
		}


		/*
		 * Look through the SyncState.state values and find ones that need to be
		 * stored.
		 */

		for (final Map.Entry<String, SyncStatus> entry : syncStatuses.entrySet()) {
			final String pubUri = entry.getKey();
			final SyncStatus status = entry.getValue();
			if (status.state == SyncState.REMOTE_ONLY) {
				if (DEBUG) {
					Log.d(TAG, pubUri + " is not yet stored locally, adding...");
				}

				// update this so it's in the local timescale
				correctServerOffset(status.remoteCVs, JsonSyncableItem._CREATED_DATE,
						JsonSyncableItem._CREATED_DATE, localOffset);
				correctServerOffset(status.remoteCVs, JsonSyncableItem._SERVER_MODIFIED_DATE,
						JsonSyncableItem._MODIFIED_DATE, localOffset);

				final ContentProviderOperation.Builder b = ContentProviderOperation
						.newInsert(toSync);
				b.withValues(status.remoteCVs);

				cpo.add(b.build());
				cpoPubUris.add(pubUri);
				syncResult.stats.numInserts++;

			}
		}

		/*
		 * Execute the content provider operations in bulk.
		 */
		if (cpo.size() > 0) {
			if (DEBUG){
				Log.d(TAG, "bulk inserting "+ cpo.size() + " items...");
			}
			final ContentProviderResult[] r = provider.applyBatch(cpo);
			if (DEBUG) {
				Log.d(TAG, "applyBatch completed. Processing results...");
			}

			for (int i = 0; i < r.length; i++) {
				final ContentProviderResult res = r[i];
				if (res.uri == null) {
					syncResult.stats.numSkippedEntries++;
					Log.e(TAG, "result from content provider bulk operation returned null");
					continue;
				}
				final SyncStatus ss = syncStatuses.get(cpoPubUris.get(i));

				if (ss == null) {
					syncResult.stats.numSkippedEntries++;
					Log.e(TAG, "could not find sync status for " + cpoPubUris.get(i));
					continue;
				}

				ss.local = res.uri;

				syncMap.onPostSyncItem(mContext, res.uri, ss.remoteJson,
						res.count != null ? res.count == 1 : true);

				ss.state = SyncState.NOW_UP_TO_DATE;
			}
			if (DEBUG) {
				Log.d(TAG, "batch updates successfully applied.");
			}
		} else {
			if (DEBUG) {
				Log.d(TAG, "no updates to perform.");
			}
		}

		syncStatuses.clear();

		mLastUpdated.markUpdated(toSync);

		return true;
	}

	/**
	 * Uploads any unpublished casts.
	 *
	 * This is the method that does all the hard work.
	 *
	 * @param itemDir
	 * @param provider
	 * @param syncMap
	 * @param syncResult
	 * @return the number of casts uploaded.
	 * @throws JSONException
	 * @throws NetworkProtocolException
	 * @throws IOException
	 * @throws NoPublicPath
	 * @throws RemoteException
	 * @throws OperationApplicationException
	 * @throws SyncException
	 */
	private int uploadUnpublished(Uri itemDir, ContentProviderClient provider, SyncMap syncMap, HashMap<String, SyncEngine.SyncStatus> syncStatuses, SyncResult syncResult) throws JSONException, NetworkProtocolException, IOException, NoPublicPath, RemoteException, OperationApplicationException, SyncException{
		int count = 0;
		final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();

		final Cursor uploadMe = provider.query(itemDir, null, SELECTION_UNPUBLISHED, null, null);

		final int idCol = uploadMe.getColumnIndex(JsonSyncableItem._ID);

		final int toUpload = uploadMe.getCount();

		final String[] localUris = new String[toUpload];

		int i = 0;

		try {
			for (uploadMe.moveToFirst(); !uploadMe.isAfterLast(); uploadMe.moveToNext()){
				final Uri localUri = ContentUris.withAppendedId(itemDir, uploadMe.getLong(idCol));
				final String postUri = MediaProvider.getPostPath(mContext, localUri);

				final JSONObject jo = JsonSyncableItem.toJSON(mContext, localUri, uploadMe, syncMap);

				if (DEBUG){
					Log.d(TAG, "uploading " + localUri + " to "+ postUri);
				}
				final HttpResponse res = mNetworkClient.post(postUri, jo.toString());

				mNetworkClient.checkStatusCode(res, true);

				long serverTime;
				try {
					serverTime = getServerTime(res);
				}catch (final DateParseException e){
					serverTime = System.currentTimeMillis();
				}

				final JSONObject newJo = NetworkClient.toJsonObject(res);
				try {
					final SyncStatus ss = loadItemFromJsonObject(newJo, syncMap, serverTime);

					final Builder update = ContentProviderOperation.newUpdate(localUri);
					update.withValues(ss.remoteCVs);
					cpo.add(update.build());
					localUris[i] = localUri.toString();

					syncStatuses.put(localUris[i], ss);

					i++;
					count++;
					syncResult.stats.numEntries++;
					syncResult.stats.numUpdates++;

				}catch (final JSONException e){
					if (DEBUG){
						Log.e(TAG, "result was "+newJo.toString());
					}
					throw e;
				}
			}
		}finally{
			uploadMe.close();
		}

		final ContentProviderResult[] cpr = provider.applyBatch(cpo);

		for (i = 0; i < cpr.length; i++){
			if (cpr[i].count != 1){
				Log.e(TAG, "error updating "+ localUris[i]);
				syncResult.stats.numSkippedEntries++;
				continue;
			}

			final SyncStatus ss = syncStatuses.get(localUris[i]);

			syncMap.onPostSyncItem(mContext, itemDir, ss.remoteJson, true);
		}

		return count;
	}

	/**
	 * Gets the server's time.
	 *
	 * @param hr
	 * @return the time that the response was generated, according to the server
	 * @throws DateParseException if the header is missing or if it can't be parsed.
	 */
	private long getServerTime(HttpResponse hr) throws DateParseException {
		final Header hDate = hr.getFirstHeader("Date");
		if (hDate == null) {
			throw new DateParseException("No Date header in http response");
		}
		return DateUtils.parseDate(hDate.getValue()).getTime();
	}

	/**
	 * Uploads any unpublished items.
	 *
	 * @param itemDir
	 * @param account
	 * @param extras
	 * @param provider
	 * @param syncResult
	 * @return the number of casts uploaded.
	 * @throws RemoteException
	 * @throws SyncException
	 * @throws JSONException
	 * @throws NetworkProtocolException
	 * @throws IOException
	 * @throws NoPublicPath
	 * @throws OperationApplicationException
	 */
	public int uploadUnpublished(Uri itemDir, Account account, Bundle extras, ContentProviderClient provider,
			SyncResult syncResult) throws RemoteException, SyncException, JSONException, NetworkProtocolException, IOException, NoPublicPath, OperationApplicationException {

		return uploadUnpublished(itemDir, provider, getSyncMap(provider, itemDir), new HashMap<String, SyncEngine.SyncStatus>(), syncResult);
	}

	/**
	 * Loads the an item from a JSONObject into a SyncStatus object.
	 *
	 * Sets {@link SyncStatus#remoteCVs}, {@link SyncStatus#remoteModifiedTime}, {@link SyncStatus#remoteJson}, {@link SyncStatus#remote}
	 *
	 * @param jo
	 * @param syncMap
	 * @param serverTime
	 * @return
	 * @throws JSONException
	 * @throws IOException
	 * @throws NetworkProtocolException
	 */
	private SyncStatus loadItemFromJsonObject(JSONObject jo, SyncMap syncMap, long serverTime)
			throws JSONException, IOException, NetworkProtocolException {
		final ContentValues cv = JsonSyncableItem.fromJSON(mContext, null, jo, syncMap);

		final String remoteUri = cv.getAsString(JsonSyncableItem._PUBLIC_URI);
		final long remoteModified = cv.getAsLong(JsonSyncableItem._SERVER_MODIFIED_DATE);

		// the status starts out based on this knowledge and gets filled in
		// as the sync progresses
		final SyncStatus syncStatus = new SyncStatus(remoteUri, SyncState.REMOTE_ONLY);
		syncStatus.remoteModifiedTime = remoteModified;
		syncStatus.remoteCVs = cv;
		syncStatus.remoteJson = jo;
		syncStatus.remote = remoteUri;

		return syncStatus;
	}

	/**
	 * The mobile needs to store the modified date in its own timescale, so it
	 * can tell if a local update is newer than that of the server.
	 *
	 * @param cv
	 * @param fromKey
	 * @param localOffset
	 */
	private void correctServerOffset(ContentValues cv, String fromKey, String destKey,
			long localOffset) {
		final long serverModified = cv.getAsLong(fromKey);
		cv.put(destKey, serverModified + localOffset);
	}

	/**
	 * Retrieves the class which maps to the given type of the specified uri.
	 * The map is statically defined in this class.
	 *
	 * @param provider
	 *            a provider which can retrieve the type for the given uri
	 * @param data
	 *            a uri to a dir or item which the engine knows how to handle
	 * @return the class which contains the sync map (and other helpers) for the
	 *         specified uri
	 * @throws SyncException
	 *             if the map cannot be found
	 * @throws RemoteException
	 *             if there is an error communicating with the provider
	 */
	public Class<? extends JsonSyncableItem> getSyncClass(ContentProviderClient provider, Uri data)
			throws SyncException, RemoteException {
		final String type = provider.getType(data);

		final Class<? extends JsonSyncableItem> syncable = TYPE_MAP.get(type);
		if (syncable == null) {
			throw new SyncException("cannot find " + data + ", which has type " + type
					+ ", in the SyncEngine's sync map");
		}
		return syncable;
	}

	/**
	 * Retrieves the sync map from the class that maps to the given uri
	 *
	 * @param provider
	 * @param toSync
	 * @return
	 * @throws RemoteException
	 * @throws SyncException
	 */
	public SyncMap getSyncMap(ContentProviderClient provider, Uri toSync) throws RemoteException,
			SyncException {
		final Class<? extends JsonSyncableItem> syncable = getSyncClass(provider, toSync);

		try {
			final Field syncMap = syncable.getField("SYNC_MAP");
			final int modifiers = syncMap.getModifiers();
			if (!Modifier.isStatic(modifiers)) {
				throw new SyncException("sync map for " + syncable + " is not static");
			}
			return (SyncMap) syncMap.get(null);

		} catch (final SecurityException e) {
			final SyncException se = new SyncException("error extracting sync map");
			se.initCause(e);
			throw se;
		} catch (final NoSuchFieldException e) {
			final SyncException se = new SyncException("SYNC_MAP static field missing from "
					+ syncable);
			se.initCause(e);
			throw se;
		} catch (final IllegalArgumentException e) {
			final SyncException se = new SyncException("error extracting sync map");
			se.initCause(e);
			throw se;
		} catch (final IllegalAccessException e) {
			final SyncException se = new SyncException("error extracting sync map");
			se.initCause(e);
			throw se;
		}
	}

	private enum SyncState {
		/**
		 * The item was up to date before the sync began.
		 */
		ALREADY_UP_TO_DATE,

		/**
		 * The item is now up to date, as a result of the sync.
		 */
		NOW_UP_TO_DATE,

		/**
		 * The item exists both remotely and locally, but has been changed on
		 * the local side.
		 */
		LOCAL_DIRTY,

		/**
		 * The item exists both remotely and locally, but has been changed on
		 * the remote side.
		 */
		REMOTE_DIRTY,

		/**
		 * The item is only stored locally.
		 */
		LOCAL_ONLY,

		/**
		 * Item is only stored on the remote side.
		 */
		REMOTE_ONLY,

		DELETED_LOCALLY, DELETED_REMOTELY
	}

	private static class SyncStatus {
		public SyncStatus(String remote, SyncState state) {
			this.remote = remote;
			this.state = state;
		}

		Uri local;
		String remote;
		SyncState state;
		long remoteModifiedTime;
		JSONObject remoteJson;
		ContentValues remoteCVs;
	}
}
