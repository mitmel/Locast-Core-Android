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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ContentProvider;
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
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.net.ClientResponseException;
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
    static final long TIMEOUT_AUTO_SYNC_MINIMUM = (long) (60 * 1e9);

    private final LastUpdatedMap<Uri> mLastUpdated = new LastUpdatedMap<Uri>(
            TIMEOUT_AUTO_SYNC_MINIMUM);

    private static final String[] SYNC_PROJECTION = new String[] {

    JsonSyncableItem._ID,

    JsonSyncableItem.COL_PUBLIC_URL,

    JsonSyncableItem.COL_MODIFIED_DATE,

    JsonSyncableItem.COL_SERVER_MODIFIED_DATE,

    JsonSyncableItem.COL_CREATED_DATE,

    JsonSyncableItem.COL_DELETED,

    JsonSyncableItem.COL_DIRTY,

    SyncableProvider.QUERY_RETURN_DELETED

    };

    /**
     * Items that are ready to publish, but haven't been published before. The selection for items
     * that aren't deleted is implied by the underlying content provider.
     */
    private static final String SELECTION_UNPUBLISHED = JsonSyncableItem.COL_PUBLIC_URL
            + " ISNULL AND " + JsonSyncableItem.SELECTION_NOT_DRAFT;

    final String[] PUB_URI_PROJECTION = new String[] { JsonSyncableItem._ID,
            JsonSyncableItem.COL_PUBLIC_URL };

    private final SyncableProvider mProvider;

    private static int FORMAT_ARGS_DEBUG = android.text.format.DateUtils.FORMAT_SHOW_TIME
            | android.text.format.DateUtils.FORMAT_SHOW_YEAR
            | android.text.format.DateUtils.FORMAT_SHOW_DATE;

    public SyncEngine(Context context, NetworkClient networkClient, SyncableProvider provider) {
        mContext = context;
        mNetworkClient = networkClient;
        mProvider = provider;
    }

    /**
     * <p>
     * This performs the data synchronization.
     * </p>
     * <p>
     * Provide this method with a URL to synch from and it will do all the resolution and
     * introspection necessary to make it happen. content:// URLs must have all the columns defined
     * in {@link JsonSyncableItem} for sync to function properly. Additionally, the
     * {@link ContentProvider} that backs them must implement {@link SyncableProvider}.
     * </p>
     *
     * <p>
     * It starts off by sorting out all the URLs, determining if all the information is provided for
     * synchronization.
     * </p>
     *
     * @param toSync
     *            a {@code content://}, {@code http://}, or {@code https://} URL
     * @param account
     *            the account that is used for synchronizing. This can be null if the sync is
     *            anonymous.
     * @param extras
     * @param provider
     * @param syncResult
     *            an interface to report back sync stats to the calling class
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
            InterruptedException, HttpResponseException {

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
        final SyncMap syncMap = mProvider.getSyncMap(provider, toSync);
        if (DEBUG) {
            Log.d(TAG, "using " + syncMap + " to sync " + toSync);
        }

        final Uri toSyncWithoutQuerystring = toSync.buildUpon().query(null).build();

        final HashMap<String, SyncStatus> syncStatuses = new HashMap<String, SyncEngine.SyncStatus>();

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
                pubPath = mProvider.getPublicPath(mContext, toSync, mNetworkClient);
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

        //
        // uploading unpublished content has been finished. Next, proceed to figuring out what needs
        // to be done with the rest of the content.
        //

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

        // Retrieve the content from the server using the public path. This also times it, for
        // reporting as well as computing offsets for time-based synchronization.

        final long request_time = System.currentTimeMillis();

        final HttpResponse hr = mNetworkClient.get(pubPath);

        final long response_time = System.currentTimeMillis();

        // the time compensation below allows a time-based synchronization to function even if the
        // local clock is entirely wrong. The server's time is extracted using the Date header and
        // all are compared relative to the respective clock reference. Any data that's stored on
        // the mobile should be stored relative to the local clock and the server will respect the
        // same.
        final long serverTime = getServerTime(hr);

        // TODO check out
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
        final long response_delay = response_time - request_time;
        if (DEBUG) {
            Log.d(TAG, "request took " + response_delay + "ms");
        }
        final long localTime = request_time;

        // add this to the server time to get the local time
        final long localOffset = (localTime - serverTime);

        // Sometimes local clocks are way off. This happens most often on dev phones that don't have
        // time synch working properly, but there are many other reasons why the local clock may be
        // wrong.

        if (Math.abs(localOffset) > 30 * 60 * 1000) {
            Log.w(TAG, "local clock is off by " + localOffset + "ms");
        }

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        final HttpEntity ent = hr.getEntity();

        // The JSON loaders below constructs a selection which matches all the public URLs retrieved
        // from server. It also constructs an inverse selection which matches all the items that are
        // NOT on the server (at least according to the results from the provided public URL).
        String selection;
        String selectionInverse;
        String[] selectionArgs;

        // The JSON type (object or list) is processed based on what the local content URL is.
        // Eventually, this should detect the JSON type from the server's response (perhaps MIME
        // type) and handle it accordingly.
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
            selection = JsonSyncableItem.COL_PUBLIC_URL + " IN " + placeholders;
            selectionInverse = JsonSyncableItem.COL_PUBLIC_URL + " NOT IN " + placeholders;

            // handle individual content items.
        } else {

            final JSONObject jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
            ent.consumeContent();
            final SyncStatus syncStatus = loadItemFromJsonObject(jo, syncMap, serverTime);

            syncStatuses.put(syncStatus.remote, syncStatus);

            selection = JsonSyncableItem.COL_PUBLIC_URL + "=?";
            selectionInverse = JsonSyncableItem.COL_PUBLIC_URL + "!=?";
            selectionArgs = new String[] { syncStatus.remote };
        }

        // At this point, all the data is loaded from the server into the syncStatuses data
        // structure. Now it needs to be processed to determine what to do with it.
        checkForExistingItems(provider, toSyncWithoutQuerystring, isDir, selection, selectionArgs,
                syncStatuses);

        processUpdates(toSync, provider, account, syncMap, selection, serverTime, localTime,
                localOffset, selectionArgs, syncStatuses, syncResult);

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        processInserts(toSync, provider, account, syncMap, localOffset, syncStatuses, syncResult);

        processDeletes(provider, toSync, toSyncWithoutQuerystring, selectionArgs, selectionInverse,
                pubPath, isDir, syncStatuses, syncResult);

        processRemainingOnPostSync(provider, toSync, account, syncMap, selection, selectionArgs,
                syncStatuses);

        syncStatuses.clear();

        mLastUpdated.markUpdated(toSync);

        return true;
    }

    private void processRemainingOnPostSync(ContentProviderClient provider, Uri toSync,
            Account account, SyncMap syncMap, String selection, String[] selectionArgs,
            HashMap<String, SyncStatus> syncStatuses) throws SyncException, IOException,
            RemoteException, JSONException, NetworkProtocolException {

        final Cursor c = provider.query(toSync, SYNC_PROJECTION, selection, selectionArgs, null);
        final int pubUriCol = c.getColumnIndex(JsonSyncableItem.COL_PUBLIC_URL);

        try {
            while (c.moveToNext()) {
                final String pubUri = c.getString(pubUriCol);
                final SyncStatus ss = syncStatuses.get(pubUri);

                if (!ss.onPostSyncComplete) {
                    if (ss.remoteJson == null) {
                        ss.remoteJson = JsonSyncableItem.toJSON(mContext, ss.local, c, syncMap);
                    }
                    syncMap.onPostSyncItem(mContext, account, ss.local, ss.remoteJson, false);
                    ss.onPostSyncComplete = true;
                }
            }
        } finally {
            c.close();
        }
    }

    /**
     * Look through all the items that we didn't already find on the server side, but which still
     * have a public uri. They should be checked to make sure they're not deleted.
     *
     * @param provider
     * @param toSync
     * @param toSyncWithoutQuerystring
     * @param selectionArgs
     * @param selectionInverse
     * @param pubPath
     * @param isDir
     * @param cpo
     * @param syncStatuses
     * @param syncResult
     * @throws RemoteException
     * @throws IOException
     * @throws JSONException
     * @throws NetworkProtocolException
     * @throws OperationApplicationException
     * @throws SyncException
     */
    private void processDeletes(ContentProviderClient provider, Uri toSync,
            final Uri toSyncWithoutQuerystring, String[] selectionArgs, String selectionInverse,
            String pubPath, final boolean isDir, final HashMap<String, SyncStatus> syncStatuses,
            SyncResult syncResult) throws RemoteException, IOException, JSONException,
            NetworkProtocolException, OperationApplicationException, SyncException {
        final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();

        HttpResponse hr;
        Cursor c;

        c = provider.query(
                toSync,
                SYNC_PROJECTION,
                ProviderUtils.addExtraWhere(selectionInverse, JsonSyncableItem.COL_PUBLIC_URL
                        + " NOT NULL"), selectionArgs, null);

        try {
            final int idCol = c.getColumnIndex(JsonSyncableItem._ID);
            final int pubUriCol = c.getColumnIndex(JsonSyncableItem.COL_PUBLIC_URL);

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
                            if (DEBUG) {
                                Log.d(TAG,
                                        item
                                                + " is already up to date. No need to see if it was deleted.");
                            }
                            continue;

                        case BOTH_UNKNOWN:
                            if (DEBUG) {
                                Log.d(TAG,
                                        item
                                                + " was found on both sides, but has an unknown sync status. Skipping...");
                            }
                            continue;

                        default:

                            Log.w(TAG, "got an unexpected state for " + item + ": " + ss);
                    }

                    // Up to this point, SyncStatus was created by loading from JSON.
                } else {
                    ss = new SyncStatus(pubUri, SyncState.LOCAL_ONLY);
                    ss.local = item;

                    hr = mNetworkClient.head(pubUri);

                    switch (hr.getStatusLine().getStatusCode()) {
                        case 200:
                            if (DEBUG) {
                                Log.d(TAG, "HEAD " + pubUri + " returned 200");
                            }
                            ss.state = SyncState.BOTH_UNKNOWN;
                            break;

                        case 404:
                            if (DEBUG) {
                                Log.d(TAG, "HEAD " + pubUri + " returned 404. Deleting locally...");
                            }
                            ss.state = SyncState.DELETED_REMOTELY;
                            final ContentProviderOperation deleteOp = ContentProviderOperation
                                    .newDelete(
                                            isDir ? ContentUris.withAppendedId(
                                                    toSyncWithoutQuerystring, c.getLong(idCol))
                                                    : toSyncWithoutQuerystring).build();
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
    }

    /**
     * @param toSync
     * @param provider
     * @param account
     * @param syncMap
     * @param localOffset
     * @param cpo
     * @param cpoPubUris
     * @param syncStatuses
     * @param syncResult
     * @throws InterruptedException
     * @throws RemoteException
     * @throws OperationApplicationException
     * @throws SyncException
     * @throws IOException
     */
    private void processInserts(Uri toSync, ContentProviderClient provider, Account account,
            final SyncMap syncMap, final long localOffset,
            final HashMap<String, SyncStatus> syncStatuses, SyncResult syncResult)
            throws InterruptedException, RemoteException, OperationApplicationException,
            SyncException, IOException {

        final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();

        // this will be in lockstep with the cpo above
        final LinkedList<String> cpoPubUris = new LinkedList<String>();

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
                correctServerOffset(status.remoteCVs, JsonSyncableItem.COL_CREATED_DATE,
                        JsonSyncableItem.COL_CREATED_DATE, localOffset);
                correctServerOffset(status.remoteCVs, JsonSyncableItem.COL_SERVER_MODIFIED_DATE,
                        JsonSyncableItem.COL_MODIFIED_DATE, localOffset);

                status.remoteCVs.put(JsonSyncableItem.COL_DIRTY,
                        SyncableProvider.FLAG_DO_NOT_CHANGE_DIRTY);

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

                ss.onPostSyncComplete = true;

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
    }

    /**
     * @param toSync
     * @param provider
     * @param account
     * @param syncMap
     * @param selection
     * @param serverTime
     * @param localTime
     * @param localOffset
     * @param cpo
     * @param cpoPubUris
     * @param selectionArgs
     * @param syncStatuses
     * @param syncResult
     * @throws RemoteException
     * @throws InterruptedException
     * @throws ClientProtocolException
     * @throws IOException
     * @throws NetworkProtocolException
     * @throws JSONException
     * @throws SyncException
     * @throws NoPublicPath
     * @throws OperationApplicationException
     */
    private void processUpdates(Uri toSync, ContentProviderClient provider, Account account,
            final SyncMap syncMap, String selection, final long serverTime, final long localTime,
            final long localOffset, String[] selectionArgs,
            final HashMap<String, SyncStatus> syncStatuses, SyncResult syncResult)
            throws RemoteException, InterruptedException, ClientProtocolException, IOException,
            NetworkProtocolException, JSONException, SyncException, NoPublicPath,
            OperationApplicationException {
        final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();

        // this will be in lockstep with the cpo above
        final LinkedList<String> cpoPubUris = new LinkedList<String>();

        // at this point, everything that was loaded from the server will have a matching local
        // content item (if it exists) whose state is stored in syncStatuses. New content has not
        // yet been stored locally and only exists in the JSON objects stored in the syncStatuses.

        // The selection below still only matches items that have already been stored locally.

        final Cursor c = provider.query(toSync, SYNC_PROJECTION, selection, selectionArgs, null);

        try {
            final int pubUriCol = c.getColumnIndex(JsonSyncableItem.COL_PUBLIC_URL);
            final int localModifiedCol = c.getColumnIndex(JsonSyncableItem.COL_MODIFIED_DATE);
            final int serverModifiedCol = c
                    .getColumnIndex(JsonSyncableItem.COL_SERVER_MODIFIED_DATE);
            final int dirtyCol = c.getColumnIndex(JsonSyncableItem.COL_DIRTY);
            final int deletedCol = c.getColumnIndexOrThrow(JsonSyncableItem.COL_DELETED);

            // All the items in this cursor should be found on both
            // the client and the server.
            for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                // as public URLs are unique, we can key off them.
                final String pubUri = c.getString(pubUriCol);

                final SyncStatus itemStatus = syncStatuses.get(pubUri);
                // itemStatus is guaranteed to not be null, as it was created in the check above

                final Uri localUri = itemStatus.local;

                if (itemStatus.state == SyncState.ALREADY_UP_TO_DATE
                        || itemStatus.state == SyncState.NOW_UP_TO_DATE) {
                    if (DEBUG) {
                        Log.d(TAG, localUri + "(" + pubUri + ")" + " is already up to date.");
                    }
                    continue;
                }

                // make the status searchable by both remote and local uri
                syncStatuses.put(localUri.toString(), itemStatus);

                // last modified as stored in the DB, in phone time
                final long itemLocalModified = c.getLong(localModifiedCol);

                // last modified as stored in the DB, in server time
                final long itemServerModified = c.getLong(serverModifiedCol);

                // how long ago, in ms, the item was updated. This is normalized according to the
                // local clock
                final long localAge = localTime - itemLocalModified;

                final long remoteAge = serverTime - itemStatus.remoteModifiedTime;

                final long ageDifference = Math.abs(localAge - remoteAge);

                final boolean localDirty = (!c.isNull(dirtyCol) && (c.getInt(dirtyCol) != 0))
                        || c.getInt(deletedCol) == 1;

                if (itemStatus.state == SyncState.BOTH_UNKNOWN) {
                    if (itemServerModified != itemStatus.remoteModifiedTime) {
                        itemStatus.state = SyncState.REMOTE_DIRTY;
                    }

                    if (localDirty) {
                        itemStatus.state = itemStatus.state == SyncState.REMOTE_DIRTY ? SyncState.BOTH_DIRTY
                                : SyncState.LOCAL_DIRTY;
                    }

                    if (itemStatus.state == SyncState.BOTH_UNKNOWN) {
                        itemStatus.state = SyncState.ALREADY_UP_TO_DATE;
                    }
                }

                // after this point, there should be no instances of BOTH_UNKNOWN

                switch (itemStatus.state) {
                    case ALREADY_UP_TO_DATE:
                    case NOW_UP_TO_DATE:
                        // yeeeeeeeaaaaaahh
                        break;

                    case DELETED_LOCALLY:
                        deleteItem(localUri, pubUri, cpo, cpoPubUris);

                        break;

                    case REMOTE_DIRTY:
                        updateLocalItem(localUri, pubUri, cpo, cpoPubUris, localOffset, itemStatus,
                                syncResult);
                        break;

                    case LOCAL_DIRTY:
                        uploadUpdate(provider, syncMap, localUri, itemStatus, cpo, cpoPubUris);

                        break;

                    case BOTH_DIRTY: {
                        Log.w(TAG,
                                pubUri
                                        + " seems to have been modified both locally and remotely. Resolvingy by comparing age...");

                        // local is older; need to load from remote
                        if (localAge > remoteAge) {
                            if (DEBUG) {
                                final long serverModified = itemStatus.remoteModifiedTime;

                                Log.d(TAG,
                                        pubUri
                                                + " : local is "
                                                + ageDifference
                                                + "ms older ("
                                                + android.text.format.DateUtils.formatDateTime(
                                                        mContext, itemLocalModified,
                                                        FORMAT_ARGS_DEBUG)
                                                + ") than remote ("
                                                + android.text.format.DateUtils
                                                        .formatDateTime(mContext, serverModified,
                                                                FORMAT_ARGS_DEBUG)
                                                + "); updating local copy...");
                            }

                            updateLocalItem(localUri, pubUri, cpo, cpoPubUris, localOffset,
                                    itemStatus, syncResult);

                            // local is younger; need to upload
                        } else if (localAge < remoteAge) {
                            if (DEBUG) {
                                final long serverModified = itemStatus.remoteModifiedTime;

                                Log.d(TAG,
                                        pubUri
                                                + " : local is "
                                                + ageDifference
                                                + "ms newer ("
                                                + android.text.format.DateUtils.formatDateTime(
                                                        mContext, itemLocalModified,
                                                        FORMAT_ARGS_DEBUG)
                                                + ") than remote ("
                                                + android.text.format.DateUtils
                                                        .formatDateTime(mContext, serverModified,
                                                                FORMAT_ARGS_DEBUG)
                                                + "); publishing to server...");
                            }

                            uploadUpdate(provider, syncMap, localUri, itemStatus, cpo, cpoPubUris);

                        }
                    }
                        break;

                    default:
                        Log.w(TAG, "sync state for " + localUri + " is " + itemStatus.state
                                + " when it shouldn't have been");
                        break;
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

            // due to all the withExpectedCount() above, this will throw an
            // OperationApplicationException if there's a problem
            final ContentProviderResult[] r = provider.applyBatch(cpo);
            if (DEBUG) {
                Log.d(TAG, "Done applying updates. Running postSync handler...");
            }

            for (int i = 0; i < r.length; i++) {
                final ContentProviderResult res = r[i];
                final SyncStatus ss = syncStatuses.get(cpoPubUris.get(i));
                if (ss == null) {
                    Log.e(TAG, "can't get sync status for " + res);
                    continue;
                }

                if (ss.state == SyncState.DELETED_LOCALLY) {
                    ss.state = SyncState.NOW_UP_TO_DATE;
                    continue;
                }

                syncMap.onPostSyncItem(mContext, account, ss.local, ss.remoteJson,
                        res.count != null ? res.count == 1 : true);

                ss.onPostSyncComplete = true;

                ss.state = SyncState.NOW_UP_TO_DATE;
            }

            if (DEBUG) {
                Log.d(TAG, "done running postSync handler.");
            }

            cpo.clear();
            cpoPubUris.clear();
        }
    }

    /**
     * Checks without the querystring. This will ensure that we properly mark things that we already
     * have in the database for items that could potentially match the query string, but haven't
     * been added to the local DB yet. All the items matching here are known to have a public URL
     * matching the data that were just received from the server.
     *
     * @param provider
     * @param toSyncWithoutQuerystring
     * @param isDir
     * @param selection
     * @param selectionArgs
     * @param syncStatuses
     * @throws RemoteException
     * @throws InterruptedException
     */
    private void checkForExistingItems(ContentProviderClient provider,
            final Uri toSyncWithoutQuerystring, final boolean isDir, String selection,
            String[] selectionArgs, final HashMap<String, SyncStatus> syncStatuses)
            throws RemoteException, InterruptedException {

        final Cursor check = provider.query(toSyncWithoutQuerystring, SYNC_PROJECTION, selection,
                selectionArgs, null);

        try {
            final int pubUriCol = check.getColumnIndex(JsonSyncableItem.COL_PUBLIC_URL);
            final int idCol = check.getColumnIndex(JsonSyncableItem._ID);
            final int deletedCol = check.getColumnIndex(JsonSyncableItem.COL_DELETED);

            // All the items in this cursor should be found on both
            // the client and the server.
            for (check.moveToFirst(); !check.isAfterLast(); check.moveToNext()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                final long id = check.getLong(idCol);
                final Uri localUri = isDir ? ContentUris.withAppendedId(toSyncWithoutQuerystring,
                        id) : toSyncWithoutQuerystring;
                final boolean deletedLocally = check.getInt(deletedCol) == 1;

                final String pubUri = check.getString(pubUriCol);

                final SyncStatus itemStatus = syncStatuses.get(pubUri);

                itemStatus.state = deletedLocally ? SyncState.DELETED_LOCALLY
                        : SyncState.BOTH_UNKNOWN;

                itemStatus.local = localUri;

                // make the status searchable by both remote and
                // local uri
                syncStatuses.put(localUri.toString(), itemStatus);
            }
        } finally {
            check.close();
        }
    }

    /**
     * Deletes an item from the server and then actually deletes it from the local store (instead of
     * just marking it deleted).
     *
     * @param localUri
     * @param pubUri
     * @param cpo
     * @param cpoPubUris
     * @throws ClientProtocolException
     * @throws IOException
     * @throws NetworkProtocolException
     */
    private void deleteItem(final Uri localUri, final String pubUri,
            final ArrayList<ContentProviderOperation> cpo, final LinkedList<String> cpoPubUris)
            throws ClientProtocolException, IOException, NetworkProtocolException {
        if (DEBUG) {
            Log.i(TAG, pubUri + " was deleted locally. Deleting from server...");
        }
        mNetworkClient.delete(pubUri);
        // delete would have thrown an exception if there was an issue. Now tell the
        // engine to actually delete it locally.
        if (DEBUG) {
            Log.i(TAG, pubUri + " has been deleted on the server. Deleting from local DB...");
        }
        final ContentProviderOperation.Builder b = ContentProviderOperation.newDelete(localUri);

        b.withExpectedCount(1);

        cpo.add(b.build());
        cpoPubUris.add(pubUri);
    }

    /**
     * Updates the local item with the information from the server.
     *
     * @param localUri
     * @param pubUri
     * @param cpo
     * @param cpoPubUris
     * @param localOffset
     * @param itemStatus
     * @param syncResult
     */
    private void updateLocalItem(final Uri localUri, final String pubUri,
            final ArrayList<ContentProviderOperation> cpo, final LinkedList<String> cpoPubUris,
            final long localOffset, final SyncStatus itemStatus, SyncResult syncResult) {
        itemStatus.state = SyncState.REMOTE_DIRTY;

        final ContentProviderOperation.Builder b = ContentProviderOperation.newUpdate(localUri);

        // update this so it's in the local timescale
        correctServerOffset(itemStatus.remoteCVs, JsonSyncableItem.COL_CREATED_DATE,
                JsonSyncableItem.COL_CREATED_DATE, localOffset);
        correctServerOffset(itemStatus.remoteCVs, JsonSyncableItem.COL_SERVER_MODIFIED_DATE,
                JsonSyncableItem.COL_MODIFIED_DATE, localOffset);

        // mark the item not dirty so it won't be considered locally modified
        itemStatus.remoteCVs.put(JsonSyncableItem.COL_DIRTY, false);
        b.withValues(itemStatus.remoteCVs);
        b.withExpectedCount(1);

        cpo.add(b.build());
        cpoPubUris.add(pubUri);

        syncResult.stats.numUpdates++;
    }

    /**
     * Publishes an update to the server.
     *
     * @param provider
     * @param syncMap
     * @param localUri
     * @param itemStatus
     * @param cpo
     *            TODO
     * @param cpoPubUris
     *            TODO
     * @param pubPath
     * @throws RemoteException
     * @throws IOException
     * @throws NetworkProtocolException
     * @throws JSONException
     * @throws SyncException
     * @throws NoPublicPath
     */
    private void uploadUpdate(ContentProviderClient provider, final SyncMap syncMap,
            final Uri localUri, final SyncStatus itemStatus,
            ArrayList<ContentProviderOperation> cpo, LinkedList<String> cpoPubUris)
            throws RemoteException, IOException, NetworkProtocolException, JSONException,
            SyncException, NoPublicPath {

        final String itemPubPath = itemStatus.remote != null ? itemStatus.remote : mProvider
                .getPublicPath(mContext, localUri, mNetworkClient);

        // requeries to ensure that when it converts it to JSON, it has all the columns.
        // The QUERY_RETURN_DELETED flag will be removed and this will be treated as a null
        // projection.
        final Cursor uploadCursor = provider.query(localUri,
                new String[] { SyncableProvider.QUERY_RETURN_DELETED }, null, null, null);
        try {
            if (uploadCursor.moveToFirst()) {
                mNetworkClient.putJson(itemPubPath,
                        JsonSyncableItem.toJSON(mContext, localUri, uploadCursor, syncMap));

                // now that the local content has been published, clear the dirty bit.
                cpo.add(ContentProviderOperation.newUpdate(localUri)
                        .withValue(JsonSyncableItem.COL_DIRTY, false).build());
                cpoPubUris.add(itemPubPath);
            } else {
                throw new SyncException("couldn't find local item upon requerying");
            }
        } finally {
            uploadCursor.close();
        }
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

                final Uri localUri = isDir ? ContentUris.withAppendedId(toSync, id) : toSync;
                final String postUri = mProvider.getPostPath(mContext, localUri, mNetworkClient);

                if (postUri == null) {
                    throw new SyncException(
                            "Error: no post path provided. Perhaps a child is being sync'd before its parent.");
                }

                Intent intent = new Intent(SYNC_STATUS_CHANGED);
                intent.putExtra(EXTRA_SYNC_STATUS, "castBegin");
                intent.putExtra(EXTRA_SYNC_ID, id);
                mContext.sendStickyBroadcast(intent);

                JSONObject jo;
                try {
                    jo = JsonSyncableItem.toJSON(mContext, localUri, uploadMe, syncMap);

                    if (DEBUG) {
                        Log.d(TAG, "uploading " + localUri + " to " + postUri);
                    }

                    // Upload! Any non-successful responses are handled by
                    // exceptions.
                    final HttpResponse res = mNetworkClient.post(postUri, jo.toString());

                    final long serverTime = getServerTime(res);

                    // newly-created items return the JSON serialization of the
                    // object as the server
                    // knows it, so the local database needs to be updated to
                    // reflect that.
                    final JSONObject newJo = NetworkClient.toJsonObject(res);
                    try {
                        final SyncStatus ss = loadItemFromJsonObject(newJo, syncMap, serverTime);

                        ss.remoteCVs.put(JsonSyncableItem.COL_DIRTY, false);

                        // update immediately, so that any cancellation or
                        // interruption of the sync
                        // keeps the local state in sync with what's on the
                        // server
                        final int updates = provider.update(localUri, ss.remoteCVs, null, null);

                        final String locUriString = localUri.toString();

                        if (updates == 1) {
                            ss.state = SyncState.NOW_UP_TO_DATE;
                            ss.local = localUri;

                            // ensure that it's findable by local URI too
                            syncStatuses.put(locUriString, ss);

                            syncMap.onPostSyncItem(mContext, account, ss.local, ss.remoteJson, true);

                            ss.onPostSyncComplete = true;

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

                    // the client can handle some 400-series errors gracefully.
                } catch (final ClientResponseException e) {
                    if (HttpStatus.SC_CONFLICT == e.getStatusCode()) {
                        handleConflict(provider, syncMap, syncStatuses, syncResult, localUri, e);
                    } else if (HttpStatus.SC_BAD_REQUEST == e.getStatusCode()) {
                        if (DEBUG) {
                            Log.w(TAG, "Got bad request from server when uploading " + postUri
                                    + " skipping...");
                        }
                        syncResult.stats.numSkippedEntries++;
                    } else {
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
     * Handles a 409 conflict.
     *
     * @param provider
     * @param syncMap
     * @param syncStatuses
     * @param syncResult
     * @param localUri
     *            the local URI of the item
     * @param e
     *            the 409 conflict exception returned
     * @throws IOException
     * @throws JSONException
     * @throws NetworkProtocolException
     * @throws ClientResponseException
     * @throws RemoteException
     */
    private void handleConflict(ContentProviderClient provider, SyncMap syncMap,
            HashMap<String, SyncEngine.SyncStatus> syncStatuses, SyncResult syncResult,
            final Uri localUri, final ClientResponseException e) throws IOException, JSONException,
            NetworkProtocolException, ClientResponseException, RemoteException {
        final Bundle data = e.getData();

        if (DEBUG) {
            Log.w(TAG, "Got a CONFLICT response from server. Attempting to recover...");
        }

        // TODO codify uuid somehow
        // if it's a conflict in the uuid, we can handle it.
        if (data != null && data.containsKey("uuid") && data.containsKey("uri")) {

            // at this point, the client has an item it thinks hasn't been posted,
            // but apparently it had posted it before and never updated its local
            // database. This means that it doesn't know which side is more up to
            // date (either could have been modified between when the first POST
            // happened). What *is* known is that the local item now maps to a given
            // public URI.

            final ContentValues cv = new ContentValues();
            final String pubUri = data.getString("uri");

            final HttpResponse res = mNetworkClient.get(pubUri);

            final SyncStatus ss = loadItemFromJsonObject(mNetworkClient.getObject(pubUri), syncMap,
                    getServerTime(res));

            // update the database. The SyncStatus will be updated when it loads the
            // content from the server
            cv.put(JsonSyncableItem.COL_PUBLIC_URL, pubUri);
            cv.put(JsonSyncableItem.COL_DIRTY, SyncableProvider.FLAG_DO_NOT_CHANGE_DIRTY);
            provider.update(localUri, cv, null, null);

            ss.local = localUri;
            ss.state = SyncState.BOTH_UNKNOWN;

            syncStatuses.put(pubUri, ss);

        } else {
            if (DEBUG) {
                Log.d(TAG, "there is not enough information in the CONFLICT response to recover.");
            }
            syncResult.stats.numConflictDetectedExceptions++;
        }
    }

    /**
     * Gets the server's time. If the time is missing, the local time will be returned.
     *
     * @param hr
     * @return the time that the response was generated, according to the server or the current
     *         system time
     */
    private long getServerTime(HttpResponse hr) {
        final Header hDate = hr.getFirstHeader("Date");
        try {
            if (hDate == null) {
                throw new DateParseException("No Date header in http response");
            }
            return DateUtils.parseDate(hDate.getValue()).getTime();

        } catch (final DateParseException e) {
            Log.w(TAG,
                    "could not retrieve date from server. Using local time, which may be incorrect.",
                    e);
            return System.currentTimeMillis();
        }
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
                mProvider.getSyncMap(provider, itemDir),
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

        final String remoteUri = cv.getAsString(JsonSyncableItem.COL_PUBLIC_URL);
        final long remoteModified = cv.getAsLong(JsonSyncableItem.COL_SERVER_MODIFIED_DATE);

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

        /**
         * Initial state. Both need to be resolved.
         */
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
         * Both local and remote were determined to be dirty. Resolve somehow.
         */
        BOTH_DIRTY,

        /**
         * The item is only stored locally.
         */
        LOCAL_ONLY,

        /**
         * Item is only stored on the remote side.
         */
        REMOTE_ONLY,

        /**
         * The item's {@link JsonSyncableItem#COL_DELETED} is set
         */
        DELETED_LOCALLY,

        /**
         * The item is no longer found on the server.
         */
        DELETED_REMOTELY
    }

    private static class SyncStatus {
        public SyncStatus(String remote, SyncState state) {
            this.remote = remote;
            this.state = state;
        }

        /**
         * local content URL
         */
        Uri local;
        /**
         * public URL
         */
        String remote;
        SyncState state;
        /**
         * The last modified time in the server's time scale
         */
        long remoteModifiedTime;
        JSONObject remoteJson;
        /**
         * The remoteJson as CV
         */
        ContentValues remoteCVs;

        boolean onPostSyncComplete = false;

        @Override
        public String toString() {

            return "[state: " + state + ", local uri: " + local + ", remote uri: " + remote
                    + ", ...]";
        }
    }
}
