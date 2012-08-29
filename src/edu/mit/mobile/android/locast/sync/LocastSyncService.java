package edu.mit.mobile.android.locast.sync;

/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.json.JSONException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.annotation.TargetApi;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Collection;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * A wrapper to {@link SyncEngine} which provides the interface to the
 * {@link ContentResolver} sync framework.
 *
 * There are some helper static methods to simplify the creation of the
 * {@link ContentResolver#requestSync(Account, String, Bundle)} calls. See
 * {@link #startSync(Account, Uri, boolean, Bundle)} and friends.
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public class LocastSyncService extends Service {
    private static final String TAG = LocastSyncService.class.getSimpleName();

    private static final boolean DEBUG = Constants.DEBUG;

    private static LocastSyncAdapter SYNC_ADAPTER = null;


    /**
     * A string extra specifying the URI of the object to sync. Can be a
     * content:// or http:// uri.
     */
    public static final String EXTRA_SYNC_URI = "edu.mit.mobile.android.locast.sync.EXTRA_SYNC_URI";

    /**
     * Convenience method to request a sync.
     *
     * This wraps {@link ContentResolver#requestSync(Account, String, Bundle)}
     * and fills in the necessary extras.
     *
     * @param account
     * @param what the uri of the item that needs to be sync'd. can be null
     * @param explicitSync
     *            if true, adds {@link ContentResolver#SYNC_EXTRAS_MANUAL} to
     *            the extras
     * @param extras
     */
    public static void startSync(Account account, Uri what, Bundle extras) {

        if (what != null){
            extras.putString(LocastSyncService.EXTRA_SYNC_URI, what.toString());
        }

        if (extras.getBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false)) {
            if (SYNC_ADAPTER != null) {
                if (DEBUG) {
                    Log.d(TAG, "canceling current sync to make room for expedited sync of " + what);
                }
                // only cancel the ongoing sync, to leave the queue untouched.
                SYNC_ADAPTER.cancelCurrentSync();
            }
        }

        if (DEBUG) {
            Log.d(TAG, "requesting sync for " + account + " with extras: " + extras);
        }
        ContentResolver.requestSync(account, MediaProvider.AUTHORITY, extras);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return getSyncAdapter().getSyncAdapterBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AccountManager.get(this).addOnAccountsUpdatedListener(getSyncAdapter(), null, true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AccountManager.get(this).removeOnAccountsUpdatedListener(getSyncAdapter());
    }

    private static class LocastSyncAdapter extends AbstractThreadedSyncAdapter implements
            OnAccountsUpdateListener {
        private final Context mContext;

        private final HashMap<Account, SyncEngine> mSyncEngines = new HashMap<Account, SyncEngine>();

        private WeakReference<Thread> mSyncThread;

        private Account mCurrentlySyncing;

        public LocastSyncAdapter(Context context) {
            super(context, true);
            mContext = context;

        }

        /**
         * Cancels the current sync, but does not clear the queue.
         */
        public void cancelCurrentSync() {
            if (DEBUG) {
                Log.d(TAG, "cancelCurrentSync()");
            }

            if (mSyncThread != null){
                final Thread syncThread = mSyncThread.get();
                if (syncThread != null){
                    if (DEBUG){
                        Log.d(TAG, "interrupting current sync thread "+syncThread.getId()+"...");
                    }
                    syncThread.interrupt();
                    if (DEBUG){
                        Log.d(TAG, "waiting for previous sync to finish...");
                    }
                    final long start = System.nanoTime();
                    try {
                        syncThread.join();
                        if (DEBUG){
                            Log.d(TAG, "Sync took " + (System.nanoTime() - start ) / 1000000 + "ms to finish.");
                        }
                    } catch (final InterruptedException e) {
                        Log.w(TAG, e.getLocalizedMessage(), e);
                    }
                }
            }
        }

        @TargetApi(8)
        @Override
        public void onSyncCanceled() {
            if (DEBUG) {
                Log.d(TAG, "onSyncCanceled()");
            }
            super.onSyncCanceled();

            cancelCurrentSync();
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority,
                ContentProviderClient provider, SyncResult syncResult) {

            Intent intent = new Intent(SyncEngine.SYNC_STATUS_CHANGED);
            intent.putExtra(SyncEngine.EXTRA_SYNC_STATUS, "begin");
            mContext.sendStickyBroadcast(intent);

            mCurrentlySyncing = account;
            SyncEngine syncEngine = mSyncEngines.get(account);
            if (syncEngine == null) {
                syncEngine = new SyncEngine(mContext, NetworkClient.getInstance(mContext, account));
                mSyncEngines.put(account, syncEngine);
            }

            mSyncThread = new WeakReference<Thread>(Thread.currentThread());

            final String uriString = extras.getString(EXTRA_SYNC_URI);

            final Uri uri = uriString != null ? Uri.parse(uriString) : null;
            if (uri != null) {
                extras.remove(EXTRA_SYNC_URI);
            }

            final boolean uploadOnly = extras.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, false);

            if (DEBUG){
                if (uri != null) {
                    Log.d(TAG, "onPerformSync() triggered with uri: " + uri);
                } else {
                    Log.d(TAG, "onPerformSync() triggered without an uri.");
                }
            }

            try {
                mContext.startService(new Intent(MediaSync.ACTION_SYNC_RESOURCES));

                if (uploadOnly) {
                    // default to only uploading casts
                    syncEngine.uploadUnpublished(uri != null ? uri : Cast.CONTENT_URI, account,
                            extras, provider, syncResult);
                } else {
                    if (uri != null) {
                        syncEngine.sync(uri, account, extras, provider, syncResult);
                    }else{
                        // the default items to sync, if an unspecified sync occurs.
                        syncEngine.sync(Cast.FEATURED, account, extras, provider, syncResult);
                        syncEngine.sync(Collection.CONTENT_URI, account, extras, provider,
                                syncResult);
                        if (!Authenticator.isDemoMode(mContext)){
                            syncEngine.sync(Cast.FAVORITE, account, extras, provider, syncResult);
                        }
                    }
                }
            } catch (final InterruptedIOException e) {
                if (DEBUG) {
                    Log.i(TAG, "Sync was interrupted");
                }

            } catch (final InterruptedException e) {
                if (DEBUG) {
                    Log.i(TAG, "Sync was interrupted");
                }

            } catch (final RemoteException e) {
                Log.e(TAG, e.toString(), e);
                // TODO handle

            } catch (final HttpResponseException e) {
                Log.e(TAG, e.toString(), e);

                switch (e.getStatusCode()) {
                    case HttpStatus.SC_NOT_FOUND:
                        syncResult.stats.numSkippedEntries++;
                        break;

                    case HttpStatus.SC_UNAUTHORIZED:
                        syncResult.stats.numAuthExceptions++;
                        break;

                    default:
                        syncResult.stats.numParseExceptions++;
                }

            } catch (final SyncException e) {
                Log.e(TAG, e.toString(), e);
                // TODO handle

            } catch (final JSONException e) {
                syncResult.stats.numParseExceptions++;
                Log.e(TAG, e.toString(), e);

            } catch (final IOException e) {
                syncResult.stats.numIoExceptions++;
                Log.e(TAG, e.toString(), e);

            } catch (final NetworkProtocolException e) {
                syncResult.stats.numParseExceptions++;
                Log.e(TAG, e.toString(), e);

            } catch (final NoPublicPath e) {

                Log.e(TAG, e.toString(), e);

            } catch (final OperationApplicationException e) {
                Log.e(TAG, e.toString(), e);
                // TODO handle

            } catch ( final SQLiteException e){
                syncResult.databaseError = true;
                Log.e(TAG, e.toString(), e);

            } catch (final IllegalArgumentException e){
                syncResult.databaseError = true;
                Log.e(TAG, e.toString(), e);

            } finally {
                intent = new Intent(SyncEngine.SYNC_STATUS_CHANGED);
                intent.putExtra(SyncEngine.EXTRA_SYNC_STATUS, "end");
                mContext.sendStickyBroadcast(intent);

                mCurrentlySyncing = null;
            }
        } // onPerformSync

        @Override
        public void onAccountsUpdated(Account[] accounts) {
            for (final Account cachedEngines : mSyncEngines.keySet()) {
                boolean accountStillExists = false;
                for (final Account account : accounts) {
                    if (cachedEngines.equals(account)) {
                        accountStillExists = true;
                        break;
                    }
                }
                if (!accountStillExists) {
                    if (DEBUG) {
                        Log.d(TAG, "removing stale sync engine for removed account "
                                + cachedEngines);
                        mSyncEngines.remove(cachedEngines);
                    }
                }
            }
        }
    } // LocastSyncAdapter

    private LocastSyncAdapter getSyncAdapter() {
        if (SYNC_ADAPTER == null) {
            SYNC_ADAPTER = new LocastSyncAdapter(this);
        }
        return SYNC_ADAPTER;
    }
}
