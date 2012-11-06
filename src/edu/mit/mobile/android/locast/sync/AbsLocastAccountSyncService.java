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
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
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
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.net.LocastApplicationCallbacks;
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
public abstract class AbsLocastAccountSyncService extends LocastSyncService {
    private static final String TAG = AbsLocastAccountSyncService.class.getSimpleName();

    private static final boolean DEBUG = Constants.DEBUG;

    private LocastSyncAdapter mSyncAdapter = null;

    /**
     * A string extra specifying the URI of the object to sync. Can be a
     * content:// or http:// uri.
     */
    public static final String EXTRA_SYNC_URI = "edu.mit.mobile.android.locast.sync.EXTRA_SYNC_URI";

    public SyncableProvider mProvider;

    @Override
    public IBinder onBind(Intent intent) {
        return getSyncAdapter().getSyncAdapterBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mProvider = (SyncableProvider) getContentResolver().acquireContentProviderClient(
                getAuthority()).getLocalContentProvider();

        mSyncAdapter = new LocastSyncAdapter(this, mProvider);

        AccountManager.get(this).addOnAccountsUpdatedListener(mSyncAdapter, null, true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Bundle extras = intent.getExtras();
        if (extras == null) {
            extras = new Bundle();
        }
        extras.putString(EXTRA_SYNC_URI, intent.getData().toString());

        final Account account = extras.getParcelable(EXTRA_ACCOUNT);

        // TODO make this shortcut the Android sync system.
        ContentResolver.requestSync(account, getAuthority(), extras);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProvider = null;
        if (mSyncAdapter != null) {
            AccountManager.get(this).removeOnAccountsUpdatedListener(mSyncAdapter);
        }
    }

    protected NetworkClient getNetworkClient(Account account) {
        return ((LocastApplicationCallbacks) this.getApplication()).getNetworkClient(this,
                account);
    }

    private static class LocastSyncAdapter extends AbstractThreadedSyncAdapter implements
            OnAccountsUpdateListener {
        private final AbsLocastAccountSyncService mContext;

        private final HashMap<Account, SyncEngine> mSyncEngines = new HashMap<Account, SyncEngine>();

        private WeakReference<Thread> mSyncThread;

        private Account mCurrentlySyncing;

        private final SyncableProvider mProvider;

        public LocastSyncAdapter(AbsLocastAccountSyncService context, SyncableProvider provider) {
            super(context, true);
            mContext = context;
            mProvider = provider;

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
                syncEngine = new SyncEngine(mContext, mContext.getNetworkClient(account),
                        mProvider);
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
                // XXX is this needed ? mContext.startService(new
                // Intent(AbsMediaSync.ACTION_SYNC_RESOURCES).setType(type));

                if (uploadOnly) {
                    if (uri != null) {
                    // default to only uploading content
                    syncEngine.uploadUnpublished(uri, account,
                            extras, provider, syncResult);
                    } else {
                        Log.w(TAG, "uploadOnly was triggered without any URI to upload");
                    }
                } else {
                    if (uri != null) {
                        syncEngine.sync(uri, account, extras, provider, syncResult);
                    }else{
                        mContext.syncDefaultItems(syncEngine, account, extras, provider, syncResult);
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

                    case HttpStatus.SC_METHOD_NOT_ALLOWED:
                        syncResult.stats.numSkippedEntries++;
                        break;

                    case HttpStatus.SC_BAD_REQUEST:
                        syncResult.stats.numSkippedEntries++;
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
        return mSyncAdapter;
    }

    /**
     * This will be called when no URI is provided.
     *
     * @param syncEngine
     * @param account
     * @param extras
     * @param provider
     * @param syncResult
     */
    public abstract void syncDefaultItems(SyncEngine syncEngine, Account account, Bundle extras,
            ContentProviderClient provider, SyncResult syncResult) throws HttpResponseException,
            RemoteException, SyncException, JSONException, IOException, NetworkProtocolException,
            NoPublicPath, OperationApplicationException, InterruptedException;
}
