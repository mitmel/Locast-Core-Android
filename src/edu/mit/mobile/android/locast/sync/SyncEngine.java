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



	private static final String[] EXIST_QUERY_PROJECTION = new String[] { JsonSyncableItem._ID,
			JsonSyncableItem._PUBLIC_URI, JsonSyncableItem._MODIFIED_DATE,
			JsonSyncableItem._CREATED_DATE };

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

		String pubPath;
		if ("http".equals(toSync.getScheme()) || "https".equals(toSync.getScheme())) {
			pubPath = toSync.toString();

			if (!extras.containsKey(EXTRA_DESTINATION_URI)) {
				throw new IllegalArgumentException(
						"missing EXTRA_DESTINATION_URI when syncing HTTP URIs");
			}
			toSync = Uri.parse(extras.getString(EXTRA_DESTINATION_URI));
		} else {
			pubPath = MediaProvider.getPublicPath(mContext, toSync);
		}

		final boolean manualSync = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);

		// skip any items already sync'd
		if (!manualSync && mLastUpdated.isUpdatedRecently(toSync)) {
			return false;
		}

		final HashMap<String, SyncStatus> mSyncStatus = new HashMap<String, SyncEngine.SyncStatus>();

		final String type = provider.getType(toSync);
		final boolean isDir = type.startsWith(CONTENT_TYPE_PREFIX_DIR);

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
			final Header hDate = hr.getFirstHeader("Date");
			if (hDate == null) {
				throw new DateParseException("No Date header in http response");
			}
			serverTime = DateUtils.parseDate(hDate.getValue()).getTime();

		} catch (final DateParseException e) {
			Log.w(TAG,
					"could not retrieve date from server. Using local time, which may be incorrect.",
					e);
			serverTime = System.currentTimeMillis();
		}

		// TODO check out http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
		final long response_delay = response_time - request_time;
		if (DEBUG){
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

		// the sync map will convert the json data to ContentValues
		final SyncMap syncMap = getSyncMap(provider, toSync);

		final HttpEntity ent = hr.getEntity();

		final Uri existsToSync = toSync.buildUpon().query(null).build();

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
				final SyncStatus syncStatus = loadItemFromServer(ja.getJSONObject(i), syncMap,
						serverTime);

				mSyncStatus.put(syncStatus.remote, syncStatus);

				selectionArgs[i] = syncStatus.remote;

				// add in a placeholder for the query
				selection.append('?');
				if (i != (len - 1)) {
					selection.append(',');
				}
			}
			selection.append(")");

			c = provider.query(existsToSync, EXIST_QUERY_PROJECTION, selection.toString(), selectionArgs,
					null);
		} else {

			final JSONObject jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
			ent.consumeContent();
			final SyncStatus syncStatus = loadItemFromServer(jo, syncMap, serverTime);

			mSyncStatus.put(syncStatus.remote, syncStatus);

			c = provider.query(existsToSync, EXIST_QUERY_PROJECTION, JsonSyncableItem._PUBLIC_URI + "=?",
					new String[] { syncStatus.remote }, null);
		}

		// ///////////////////////////////////////////////////////////////

		final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();

		try {
			final int pubUriCol = c.getColumnIndex(JsonSyncableItem._PUBLIC_URI);
			final int modifiedCol = c.getColumnIndex(JsonSyncableItem._MODIFIED_DATE);
			final int idCol = c.getColumnIndex(JsonSyncableItem._ID);

			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
				final String pubUri = c.getString(pubUriCol);
				final long id = c.getLong(idCol);
				final Uri localUri = ContentUris.withAppendedId(toSync, id);
				final SyncStatus itemStatus = mSyncStatus.get(pubUri);

				// make the status searchable by both remote and local uri
				mSyncStatus.put(localUri.toString(), itemStatus);

				final long itemModified = c.getLong(modifiedCol);
				final long localAge = localTime - itemModified;

				itemStatus.localAge = localAge;

				final long ageDifference = Math.abs(localAge - itemStatus.remoteAge);

				if (ageDifference <= AGE_EQUALITY_SLOP) {
					itemStatus.state = SyncState.UP_TO_DATE;
					if (DEBUG){
						Log.d(TAG, pubUri + " is up to date");
					}

				} else if (localAge > itemStatus.remoteAge) {
					if (DEBUG) {
						final long serverModified = itemStatus.remoteCVs
								.getAsLong(JsonSyncableItem._MODIFIED_DATE);

						Log.d(TAG,
								pubUri
										+ " : local is "
										+ ageDifference
										+ "ms older ("
										+ android.text.format.DateUtils.formatDateTime(mContext,
												itemModified, FORMAT_ARGS_DEBUG)
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
							localOffset);
					correctServerOffset(itemStatus.remoteCVs, JsonSyncableItem._MODIFIED_DATE,
							localOffset);

					b.withValues(itemStatus.remoteCVs);
					cpo.add(b.build());

				} else if (localAge < itemStatus.remoteAge) {
					if (DEBUG) {
						final long serverModified = itemStatus.remoteCVs
								.getAsLong(JsonSyncableItem._MODIFIED_DATE);

						Log.d(TAG,
								pubUri
										+ " : local is "
										+ ageDifference
										+ "ms newer ("
										+ android.text.format.DateUtils.formatDateTime(mContext,
												itemModified, FORMAT_ARGS_DEBUG)
										+ ") than remote ("
										+ android.text.format.DateUtils.formatDateTime(mContext,
												serverModified, FORMAT_ARGS_DEBUG)
										+ "); publishing to server...");
					}
					itemStatus.state = SyncState.LOCAL_DIRTY;

					// publish the local
				}

				mLastUpdated.markUpdated(localUri);
			}
		} finally {

			c.close();
		}

		for (final Map.Entry<String, SyncStatus> entry : mSyncStatus.entrySet()) {
			final String pubUri = entry.getKey();
			final SyncStatus status = entry.getValue();
			if (status.state == SyncState.REMOTE_ONLY) {
				if (DEBUG){
					Log.d(TAG, pubUri + " is not yet stored locally, adding...");
				}

				// update this so it's in the local timescale
				correctServerOffset(status.remoteCVs, JsonSyncableItem._CREATED_DATE, localOffset);
				correctServerOffset(status.remoteCVs, JsonSyncableItem._MODIFIED_DATE, localOffset);

				final ContentProviderOperation.Builder b = ContentProviderOperation
						.newInsert(toSync);
				b.withValues(status.remoteCVs);

				cpo.add(b.build());
			}
		}

		final String[] PUB_URI_PROJECTION = new String[] { JsonSyncableItem._ID,
				JsonSyncableItem._PUBLIC_URI };

		if (cpo.size() > 0) {
			final ContentProviderResult[] r = provider.applyBatch(cpo);
			for (final ContentProviderResult res : r) {
				SyncStatus ss = mSyncStatus.get(res.uri.toString());

				if (ss == null) {
					final Cursor c2 = provider.query(res.uri, PUB_URI_PROJECTION, null, null, null);
					try {
						if (c2.moveToFirst()) {
							final String pubUri = c2.getString(c2
									.getColumnIndex(JsonSyncableItem._PUBLIC_URI));
							ss = mSyncStatus.get(pubUri);
						}
					} finally {
						c2.close();
					}
				}

				if (ss != null) {
					syncMap.onPostSyncItem(mContext, res.uri, ss.remoteJson,
							res.count != null ? res.count == 1 : true);
				} else {
					Log.e(TAG, "can't get sync status for " + res.uri);
				}
			}
			if (DEBUG){
				Log.d(TAG, "batch updates successfully applied.");
			}
		} else {
			if (DEBUG){
				Log.d(TAG, "no updates to perform.");
			}
		}

		mSyncStatus.clear();

		mLastUpdated.markUpdated(toSync);

		return true;
	}

	private SyncStatus loadItemFromServer(JSONObject jo, SyncMap syncMap, long serverTime)
			throws JSONException, IOException, NetworkProtocolException {
		final ContentValues cv = JsonSyncableItem.fromJSON(mContext, null, jo, syncMap);

		final String remoteUri = cv.getAsString(JsonSyncableItem._PUBLIC_URI);
		final long remoteModified = cv.getAsLong(JsonSyncableItem._MODIFIED_DATE);

		// the status starts out based on this knowledge and gets filled in
		// as the sync progresses
		final SyncStatus syncStatus = new SyncStatus(null, remoteUri, SyncState.REMOTE_ONLY);
		syncStatus.remoteAge = serverTime - remoteModified;
		syncStatus.remoteCVs = cv;
		syncStatus.remoteJson = jo;
		syncStatus.remote = remoteUri;

		return syncStatus;
	}

	private void correctServerOffset(ContentValues cv, String key, long localOffset) {
		final long serverModified = cv.getAsLong(key);
		cv.put(key, serverModified + localOffset);
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
		UP_TO_DATE, LOCAL_DIRTY, REMOTE_DIRTY, LOCAL_ONLY, REMOTE_ONLY, DELETED_LOCALLY, DELETED_REMOTELY
	}

	private static class SyncStatus {
		public SyncStatus(String local, String remote, SyncState state) {
			this.local = local;
			this.remote = remote;
			this.state = state;
		}

		String local;
		String remote;
		SyncState state;
		long localAge;
		long remoteAge;
		JSONObject remoteJson;
		ContentValues remoteCVs;
	}
}
