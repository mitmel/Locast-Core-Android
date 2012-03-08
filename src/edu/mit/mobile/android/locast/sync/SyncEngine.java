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
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.data.TaggableItem;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.utils.LastUpdatedMap;
import edu.mit.mobile.android.utils.StreamUtils;

public class SyncEngine {
	private static final String TAG = SyncEngine.class.getSimpleName();

	public final static String SYNC_STATUS_CHANGED = "edu.mit.mobile.android.locast.SYNC_STATUS_CHANGED";
	public final static String EXTRA_SYNC_STATUS = "edu.mit.mobile.android.locast.EXTRA_SYNC_STATUS";
	public final static String EXTRA_SYNC_ID = "edu.mit.mobile.android.locast.EXTRA_SYNC_ID";

	/**
	 * If syncing a server URI that is destined for a specific local URI space, add the destination
	 * URI here.
	 */
	public final static String EXTRA_DESTINATION_URI = "edu.mit.mobile.android.locast.EXTRA_DESTINATION_URI";

	private static final String CONTENT_TYPE_PREFIX_DIR = "vnd.android.cursor.dir";

	private static final boolean DEBUG = Constants.DEBUG;

	private final Context mContext;
	private final NetworkClient mNetworkClient;

	/**
	 * in nanoseconds
	 */
	private static final long TIMEOUT_MAX_ITEM_WAIT = (long) (30 * 1e9);

	/**
	 * in nanoseconds
	 */
	static final long TIMEOUT_AUTO_SYNC_MINIMUM = (long) (60 * 1e9);

	private final LastUpdatedMap<Uri> mLastUpdated = new LastUpdatedMap<Uri>(
			TIMEOUT_AUTO_SYNC_MINIMUM);

	// the amount of time by which an age can differ and still be considered
	// equal
	private static final long AGE_EQUALITY_SLOP = 1000; // ms

	private static final String[] SYNC_PROJECTION = new String[] {

	JsonSyncableItem._ID,

	JsonSyncableItem._PUBLIC_URI,

	JsonSyncableItem._MODIFIED_DATE,

	JsonSyncableItem._SERVER_MODIFIED_DATE,

	JsonSyncableItem._CREATED_DATE

	};

	private static final String SELECTION_UNPUBLISHED = JsonSyncableItem._PUBLIC_URI
			+ " ISNULL AND " + TaggableItem.SELECTION_NOT_DRAFT;

	final String[] PUB_URI_PROJECTION = new String[] { JsonSyncableItem._ID,
			JsonSyncableItem._PUBLIC_URI };

	private static int FORMAT_ARGS_DEBUG = android.text.format.DateUtils.FORMAT_SHOW_TIME
			| android.text.format.DateUtils.FORMAT_SHOW_YEAR
			| android.text.format.DateUtils.FORMAT_SHOW_DATE;

	public SyncEngine(Context context, NetworkClient networkClient) {
		mContext = context;
		mNetworkClient = networkClient;
	}

	/**
	 * @param toSync
	 * @param account
	 * @param extras
	 * @param provider
	 * @param syncResult
	 * @return true if the item was sync'd successfully. Soft errors will cause this to return
	 *         false.
	 * @throws RemoteException
	 * @throws SyncException
	 * @throws JSONException
	 * @throws IOException
	 * @throws NetworkProtocolException
	 * @throws NoPublicPath
	 * @throws OperationApplicationException
	 * @throws InterruptedException
	 */
	public boolean sync(Uri toSync, Account account, Bundle extras, ContentProviderClient provider,
			SyncResult syncResult) throws RemoteException, SyncException, JSONException,
			IOException, NetworkProtocolException, NoPublicPath, OperationApplicationException,
			InterruptedException {

		String pubPath = null;

		//
		// Handle http or https uris separately. These require the
		// destination uri.
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
			if (DEBUG) {
				Log.d(TAG, "not syncing " + toSync + " as it's been updated recently");
			}
			syncResult.stats.numSkippedEntries++;
			return false;
		}

		// the sync map will convert the json data to ContentValues
		final SyncMap syncMap = MediaProvider.getSyncMap(provider, toSync);

		final Uri toSyncWithoutQuerystring = toSync.buildUpon().query(null).build();

		final HashMap<String, SyncStatus> syncStatuses = new HashMap<String, SyncEngine.SyncStatus>();
		final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();
		final LinkedList<String> cpoPubUris = new LinkedList<String>();

		//
		// first things first, upload any content that needs to be
		// uploaded.
		//

		try {
			uploadUnpublished(toSync, account, provider, syncMap, syncStatuses, syncResult);

			if (Thread.interrupted()) {
				throw new InterruptedException();
			}

			// this should ensure that all items have a pubPath when we
			// query it below.

			if (pubPath == null) {
				// we should avoid calling this too much as it
				// can be expensive
				pubPath = MediaProvider.getPublicPath(mContext, toSync);
			}
		} catch (final NoPublicPath e) {
			// TODO this is a special case and this is probably not the best place to handle this.
			// Ideally, this should be done in such a way as to reduce any extra DB queries -
			// perhaps by doing a join with the parent.
			if (syncMap.isFlagSet(SyncMap.FLAG_PARENT_MUST_SYNC_FIRST)) {
				if (DEBUG) {
					Log.d(TAG, "skipping " + toSync + " whose parent hasn't been sync'd first");
				}
				syncResult.stats.numSkippedEntries++;
				return false;
			}

			// if it's an item, we can handle it.
			if (isDir) {
				throw e;
			}
		}

		if (pubPath == null) {

			// this should have been updated already by the initial
			// upload, so something must be wrong
			throw new SyncException("never got a public path for " + toSync);
		}

		if (DEBUG) {
			Log.d(TAG, "sync(toSync=" + toSync + ", account=" + account + ", extras=" + extras
					+ ", manualSync=" + manualSync + ",...)");
			Log.d(TAG, "pubPath: " + pubPath);
		}

		final long request_time = System.currentTimeMillis();

		HttpResponse hr = mNetworkClient.get(pubPath);

		final long response_time = System.currentTimeMillis();

		// the time compensation below allows a time-based synchronization to function even if the
		// local clock is entirely wrong. The server's time is extracted using the Date header and
		// all are compared relative to the respective clock reference. Any data that's stored on
		// the mobile should be stored relative to the local clock and the server will respect the
		// same.
		long serverTime;

		try {
			serverTime = getServerTime(hr);
		} catch (final DateParseException e) {
			Log.w(TAG,
					"could not retrieve date from server. Using local time, which may be incorrect.",
					e);
			serverTime = System.currentTimeMillis();
		}

		// TODO check out
		// http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
		final long response_delay = response_time - request_time;
		if (DEBUG) {
			Log.d(TAG, "request took " + response_delay + "ms");
		}
		final long localTime = request_time;

		// add this to the server time to get the local time
		final long localOffset = (localTime - serverTime);

		if (Math.abs(localOffset) > 30 * 60 * 1000) {
			Log.w(TAG, "local clock is off by " + localOffset + "ms");
		}

		if (Thread.interrupted()) {
			throw new InterruptedException();
		}

		final HttpEntity ent = hr.getEntity();

		String selection;
		String selectionInverse;
		String[] selectionArgs;

		if (isDir) {

			final JSONArray ja = new JSONArray(StreamUtils.inputStreamToString(ent.getContent()));
			ent.consumeContent();

			final int len = ja.length();
			selectionArgs = new String[len];

			// build the query to see which items are already in the
			// database
			final StringBuilder sb = new StringBuilder();

			sb.append("(");

			for (int i = 0; i < len; i++) {
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}

				final SyncStatus syncStatus = loadItemFromJsonObject(ja.getJSONObject(i), syncMap,
						serverTime);

				syncStatuses.put(syncStatus.remote, syncStatus);

				selectionArgs[i] = syncStatus.remote;

				// add in a placeholder for the query
				sb.append('?');
				if (i != (len - 1)) {
					sb.append(',');
				}

			}
			sb.append(")");

			final String placeholders = sb.toString();
			selection = JsonSyncableItem._PUBLIC_URI + " IN " + placeholders;
			selectionInverse = JsonSyncableItem._PUBLIC_URI + " NOT IN " + placeholders;
		} else {

			final JSONObject jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
			ent.consumeContent();
			final SyncStatus syncStatus = loadItemFromJsonObject(jo, syncMap, serverTime);

			syncStatuses.put(syncStatus.remote, syncStatus);

			selection = JsonSyncableItem._PUBLIC_URI + "=?";
			selectionInverse = JsonSyncableItem._PUBLIC_URI + "!=?";
			selectionArgs = new String[] { syncStatus.remote };
		}

		// first check without the querystring. This will ensure that we
		// properly mark things that we already have in the database.
		final Cursor check = provider.query(toSyncWithoutQuerystring, SYNC_PROJECTION, selection,
				selectionArgs, null);

		// these items are on both sides
		try {
			final int pubUriCol = check.getColumnIndex(JsonSyncableItem._PUBLIC_URI);
			final int idCol = check.getColumnIndex(JsonSyncableItem._ID);

			// All the items in this cursor should be found on both
			// the client and the server.
			for (check.moveToFirst(); !check.isAfterLast(); check.moveToNext()) {
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}

				final long id = check.getLong(idCol);
				final Uri localUri = ContentUris.withAppendedId(toSync, id);

				final String pubUri = check.getString(pubUriCol);

				final SyncStatus itemStatus = syncStatuses.get(pubUri);

				itemStatus.state = SyncState.BOTH_UNKNOWN;

				itemStatus.local = localUri;

				// make the status searchable by both remote and
				// local uri
				syncStatuses.put(localUri.toString(), itemStatus);
			}
		} finally {
			check.close();
		}

		Cursor c = provider.query(toSync, SYNC_PROJECTION, selection, selectionArgs, null);

		// these items are on both sides
		try {
			final int pubUriCol = c.getColumnIndex(JsonSyncableItem._PUBLIC_URI);
			final int localModifiedCol = c.getColumnIndex(JsonSyncableItem._MODIFIED_DATE);
			final int serverModifiedCol = c.getColumnIndex(JsonSyncableItem._SERVER_MODIFIED_DATE);
			final int idCol = c.getColumnIndex(JsonSyncableItem._ID);

			// All the items in this cursor should be found on both
			// the client and the server.
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}

				final long id = c.getLong(idCol);
				final Uri localUri = ContentUris.withAppendedId(toSync, id);

				final String pubUri = c.getString(pubUriCol);

				final SyncStatus itemStatus = syncStatuses.get(pubUri);

				if (itemStatus.state == SyncState.ALREADY_UP_TO_DATE
						|| itemStatus.state == SyncState.NOW_UP_TO_DATE) {
					if (DEBUG) {
						Log.d(TAG, localUri + "(" + pubUri + ")" + " is already up to date.");
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
						Log.d(TAG, pubUri + " is up to date.");
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

					// requeries to ensure that when it converts it to JSON, it has all the columns.
					final Cursor uploadCursor = provider.query(localUri, null, null, null, null);
					try {
						mNetworkClient.putJson(pubPath,
								JsonSyncableItem.toJSON(mContext, localUri, uploadCursor, syncMap));
					} finally {
						uploadCursor.close();
					}
				}

				mLastUpdated.markUpdated(localUri);

				syncResult.stats.numEntries++;
			} // end for
		} finally {

			c.close();
		}

		/*
		 * Apply updates in bulk
		 */
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
				syncMap.onPostSyncItem(mContext, account, ss.local, ss.remoteJson,
						res.count != null ? res.count == 1 : true);

				ss.state = SyncState.NOW_UP_TO_DATE;
			}

			if (DEBUG) {
				Log.d(TAG, "done running postSync handler.");
			}

			cpo.clear();
			cpoPubUris.clear();
		}

		if (Thread.interrupted()) {
			throw new InterruptedException();
		}

		/*
		 * Look through the SyncState.state values and find ones that need to be stored.
		 */

		for (final Map.Entry<String, SyncStatus> entry : syncStatuses.entrySet()) {
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}

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
			if (DEBUG) {
				Log.d(TAG, "bulk inserting " + cpo.size() + " items...");
			}
			final ContentProviderResult[] r = provider.applyBatch(cpo);
			if (DEBUG) {
				Log.d(TAG, "applyBatch completed. Processing results...");
			}

			int successful = 0;
			for (int i = 0; i < r.length; i++) {
				final ContentProviderResult res = r[i];
				if (res.uri == null) {
					syncResult.stats.numSkippedEntries++;
					Log.e(TAG, "result from content provider bulk operation returned null");
					continue;
				}
				final String pubUri = cpoPubUris.get(i);
				final SyncStatus ss = syncStatuses.get(pubUri);

				if (ss == null) {
					syncResult.stats.numSkippedEntries++;
					Log.e(TAG, "could not find sync status for " + cpoPubUris.get(i));
					continue;
				}

				ss.local = res.uri;
				if (DEBUG) {
					Log.d(TAG, "onPostSyncItem(" + res.uri + ", ...); pubUri: " + pubUri);
				}

				syncMap.onPostSyncItem(mContext, account, res.uri, ss.remoteJson,
						res.count != null ? res.count == 1 : true);

				ss.state = SyncState.NOW_UP_TO_DATE;
				successful++;
			}
			if (DEBUG) {
				Log.d(TAG, successful + " batch inserts successfully applied.");
			}
		} else {
			if (DEBUG) {
				Log.d(TAG, "no updates to perform.");
			}
		}

		/**
		 * Look through all the items that we didn't already find on the server side, but which
		 * still have a public uri. They should be checked to make sure they're not deleted.
		 */
		c = provider.query(
				toSync,
				SYNC_PROJECTION,
				ProviderUtils.addExtraWhere(selectionInverse, JsonSyncableItem._PUBLIC_URI
						+ " NOT NULL"), selectionArgs, null);

		try {
			final int idCol = c.getColumnIndex(JsonSyncableItem._ID);
			final int pubUriCol = c.getColumnIndex(JsonSyncableItem._PUBLIC_URI);

			cpo.clear();

			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
				final String pubUri = c.getString(pubUriCol);
				SyncStatus ss = syncStatuses.get(pubUri);

				final Uri item = isDir ? ContentUris.withAppendedId(toSyncWithoutQuerystring,
						c.getLong(idCol)) : toSync;

				if (ss == null) {
					ss = syncStatuses.get(item.toString());
				}

				if (DEBUG) {
					Log.d(TAG, item + " was not found in the main list of items on the server ("
							+ pubPath + "), but appears to be a child of " + toSync);

					if (ss != null) {
						Log.d(TAG, "found sync status for " + item + ": " + ss);
					}
				}

				if (ss != null) {
					switch (ss.state) {
						case ALREADY_UP_TO_DATE:
						case NOW_UP_TO_DATE:
							Log.d(TAG, item
									+ " is already up to date. No need to see if it was deleted.");
							continue;

						case BOTH_UNKNOWN:
							Log.d(TAG,
									item
											+ " was found on both sides, but has an unknown sync status. Skipping...");
							continue;

						default:

							Log.w(TAG, "got an unexpected state for " + item + ": " + ss);
					}

				} else {
					ss = new SyncStatus(pubUri, SyncState.LOCAL_ONLY);
					ss.local = item;

					hr = mNetworkClient.head(pubUri);

					switch (hr.getStatusLine().getStatusCode()) {
						case 200:
							Log.d(TAG, "HEAD " + pubUri + " returned 200");
							ss.state = SyncState.BOTH_UNKNOWN;
							break;

						case 404:
							Log.d(TAG, "HEAD " + pubUri + " returned 404. Deleting locally...");
							ss.state = SyncState.DELETED_REMOTELY;
							final ContentProviderOperation deleteOp = ContentProviderOperation
									.newDelete(
											ContentUris.withAppendedId(toSyncWithoutQuerystring,
													c.getLong(idCol))).build();
							cpo.add(deleteOp);

							break;

						default:
							syncResult.stats.numIoExceptions++;
							Log.w(TAG,
									"HEAD " + pubUri + " got unhandled result: "
											+ hr.getStatusLine());
					}
				}
				syncStatuses.put(pubUri, ss);
			} // for cursor

			if (cpo.size() > 0) {
				final ContentProviderResult[] results = provider.applyBatch(cpo);

				for (final ContentProviderResult result : results) {
					if (result.count != 1) {
						throw new SyncException("Error deleting item");
					}
				}
			}

		} finally {
			c.close();
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
	 * @param toSync
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
	 * @throws InterruptedException
	 */
	private int uploadUnpublished(Uri toSync, Account account, ContentProviderClient provider,
			SyncMap syncMap, HashMap<String, SyncEngine.SyncStatus> syncStatuses,
			SyncResult syncResult) throws JSONException, NetworkProtocolException, IOException,
			NoPublicPath, RemoteException, OperationApplicationException, SyncException,
			InterruptedException {
		int count = 0;

		final String type = provider.getType(toSync);
		final boolean isDir = type.startsWith(CONTENT_TYPE_PREFIX_DIR);

		final Cursor uploadMe = provider.query(toSync, null, SELECTION_UNPUBLISHED, null, null);

		if (uploadMe == null) {
			throw new SyncException("could not query " + toSync);
		}

		final int idCol = uploadMe.getColumnIndex(JsonSyncableItem._ID);

		try {
			for (uploadMe.moveToFirst(); !uploadMe.isAfterLast(); uploadMe.moveToNext()) {
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}

				final long id = uploadMe.getLong(idCol);

				final Uri localUri = isDir ? ContentUris.withAppendedId(toSync,
						id) : toSync;
				final String postUri = MediaProvider.getPostPath(mContext, localUri);

				Intent intent = new Intent(SYNC_STATUS_CHANGED);
				intent.putExtra(EXTRA_SYNC_STATUS, "castBegin");
				intent.putExtra(EXTRA_SYNC_ID, id);
				mContext.sendStickyBroadcast(intent);

				try {
					final JSONObject jo = JsonSyncableItem.toJSON(mContext,
							localUri, uploadMe, syncMap);

					if (DEBUG) {
						Log.d(TAG, "uploading " + localUri + " to " + postUri);
					}

					// Upload! Any non-successful responses are handled by
					// exceptions.
					final HttpResponse res = mNetworkClient.post(postUri,
							jo.toString());

					long serverTime;
					try {
						serverTime = getServerTime(res);
						// We should never get a corrupted date from the server,
						// but if it does happen,
						// using the local time is a sane fallback.
					} catch (final DateParseException e) {
						serverTime = System.currentTimeMillis();
					}

					// newly-created items return the JSON serialization of the
					// object as the server
					// knows it, so the local database needs to be updated to
					// reflect that.
					final JSONObject newJo = NetworkClient.toJsonObject(res);
					try {
						final SyncStatus ss = loadItemFromJsonObject(newJo,
								syncMap, serverTime);

						// update immediately, so that any cancellation or
						// interruption of the sync
						// keeps the local state in sync with what's on the
						// server
						final int updates = provider.update(localUri,
								ss.remoteCVs, null, null);

						final String locUriString = localUri.toString();

						if (updates == 1) {
							ss.state = SyncState.NOW_UP_TO_DATE;
							ss.local = localUri;

							// ensure that it's findable by local URI too
							syncStatuses.put(locUriString, ss);

							syncMap.onPostSyncItem(mContext, account, ss.local,
									ss.remoteJson, true);

							count++;
							syncResult.stats.numUpdates++;

						} else {
							Log.e(TAG, "error updating " + locUriString);

							syncResult.stats.numSkippedEntries++;
						}

						syncResult.stats.numEntries++;

					} catch (final JSONException e) {
						if (DEBUG) {
							Log.e(TAG, "result was " + newJo.toString());
						}
						throw e;
					}
				} finally {
					intent = new Intent(SYNC_STATUS_CHANGED);
					intent.putExtra(EXTRA_SYNC_STATUS, "castEnd");
					intent.putExtra(EXTRA_SYNC_ID, id);
					mContext.sendStickyBroadcast(intent);
				}
			}
		} finally {
			uploadMe.close();
		}

		return count;
	}

	/**
	 * Gets the server's time.
	 *
	 * @param hr
	 * @return the time that the response was generated, according to the server
	 * @throws DateParseException
	 *             if the header is missing or if it can't be parsed.
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
	 * @throws InterruptedException
	 */
	public int uploadUnpublished(Uri itemDir, Account account, Bundle extras,
			ContentProviderClient provider, SyncResult syncResult) throws RemoteException,
			SyncException, JSONException, NetworkProtocolException, IOException, NoPublicPath,
			OperationApplicationException, InterruptedException {

		return uploadUnpublished(itemDir, account, provider,
				MediaProvider.getSyncMap(provider, itemDir),
				new HashMap<String, SyncEngine.SyncStatus>(), syncResult);
	}

	/**
	 * Loads the an item from a JSONObject into a SyncStatus object.
	 *
	 * Sets {@link SyncStatus#remoteCVs}, {@link SyncStatus#remoteModifiedTime},
	 * {@link SyncStatus#remoteJson}, {@link SyncStatus#remote}
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
	 * The mobile needs to store the modified date in its own timescale, so it can tell if a local
	 * update is newer than that of the server.
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

	private enum SyncState {
		/**
		 * The item was up to date before the sync began.
		 */
		ALREADY_UP_TO_DATE,

		/**
		 * The item is now up to date, as a result of the sync.
		 */
		NOW_UP_TO_DATE,

		BOTH_UNKNOWN,

		/**
		 * The item exists both remotely and locally, but has been changed on the local side.
		 */
		LOCAL_DIRTY,

		/**
		 * The item exists both remotely and locally, but has been changed on the remote side.
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

		public boolean isUpToDate() {
			return state == SyncState.ALREADY_UP_TO_DATE || state == SyncState.NOW_UP_TO_DATE;
		}

		@Override
		public String toString() {

			return "[state: " + state + ", local uri: " + local + ", remote uri: " + remote
					+ ", ...]";
		}
	}
}
