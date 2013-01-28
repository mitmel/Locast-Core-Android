package edu.mit.mobile.android.locast.sync;

/*
 * Copyright (C) 2011-2013  MIT Mobile Experience Lab
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
import java.util.concurrent.PriorityBlockingQueue;

import org.json.JSONException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.net.LocastApplicationCallbacks;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * <p>
 * A sync manager for {@link SyncEngine} that uses a simplified interface to let activities sync
 * content. Requests are put in a {@link PriorityBlockingQueue} and are handled in reverse
 * chronological order (most recent requests are first). This will be used when
 * {@link AbsLocastAccountSyncService} and the accounts / sync framework isn't being used.
 * </p>
 *
 * <p>
 * Requests should be made using {@link LocastSyncService#startSync(Context, Uri, boolean, Bundle)}
 * and its various permutations.
 * </p>
 *
 */
public abstract class LocastSimpleSyncService extends LocastSyncService {
    private static final String TAG = LocastSimpleSyncService.class.getSimpleName();

    public static final boolean DEBUG = Constants.DEBUG;

    private SyncEngine mSyncEngine;

    private NetworkClient mNetworkClient;

    private SyncQueueProcessor mSyncProcessor;
    private Thread mSyncThread;

    private final String mAuthority;

    private SyncableProvider mProvider;

    private final PriorityBlockingQueue<SyncItem> mPriorityQueue = new PriorityBlockingQueue<LocastSimpleSyncService.SyncItem>();

    public ContentProviderClient mContentProviderClient;

    public LocastSimpleSyncService() {

        mAuthority = getAuthority();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNetworkClient = ((LocastApplicationCallbacks) getApplication())
                .getNetworkClientForAccount(this, null);

        mContentProviderClient = getContentResolver().acquireContentProviderClient(mAuthority);

        mProvider = (SyncableProvider) mContentProviderClient.getLocalContentProvider();

        mSyncEngine = new SyncEngine(this, mNetworkClient, mProvider);
        mSyncProcessor = new SyncQueueProcessor();
        mSyncThread = new Thread(mSyncProcessor);
        mSyncThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final Uri uri = intent.getData();

        final Bundle extras = intent.getExtras();

        enqueueItem(uri, extras);

        return START_REDELIVER_INTENT;
    }

    public void enqueueItem(Uri uri, Bundle extras) {

        final boolean expedited = extras.getBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
        final boolean manual = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);

        final SyncItem i = new SyncItem(uri, extras, (expedited ? 10 : 0) + (manual ? 5 : 0));

        if (!expedited && mPriorityQueue.contains(i)) {
            Log.d(TAG, "not adding " + i + " as it's already in the sync queue");
            return;
        }

        mPriorityQueue.add(i);
        Log.d(TAG, "enqueued " + i);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSyncProcessor.stop();
        mContentProviderClient.release();
        mProvider = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public class LocalBinder extends Binder {
        public LocastSimpleSyncService getBinder() {
            return LocastSimpleSyncService.this;
        }
    }

    private void showNotification() {
        final Notification n = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.sync_notification))
                .setTicker(getString(R.string.sync_notification))
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(
                        PendingIntent.getActivity(this, 0,
                                getPackageManager().getLaunchIntentForPackage(getPackageName())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0)).build();
        final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(R.id.locast_core__sync, n);

    }

    private void clearNotification() {
        final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.id.locast_core__sync);
    }

    private class SyncQueueProcessor implements Runnable {

        private boolean mKeepOn = true;

        public void stop() {
            mKeepOn = false;
            Thread.currentThread().interrupt();
        }

        @Override
        public void run() {
            while (mKeepOn) {

                try {
                    showNotification();
                    final SyncResult sr = new SyncResult();
                    final SyncItem item = mPriorityQueue.take();
                    Log.d(TAG, "took " + item + " from sync queue. Syncing...");
                    mSyncEngine.sync(item.uri, null, item.extras, mContentProviderClient, sr);
                    Log.d(TAG, "finished syncing " + item);
                    Log.d(TAG, mPriorityQueue.size() + " item(s) in queue");

                } catch (final RemoteException e) {
                    Log.e(TAG, "sync error", e);
                } catch (final SyncException e) {
                    Log.e(TAG, "sync error", e);
                } catch (final JSONException e) {
                    Log.e(TAG, "sync error", e);
                } catch (final IOException e) {
                    Log.e(TAG, "sync error", e);
                } catch (final NetworkProtocolException e) {
                    Log.e(TAG, "sync error", e);
                } catch (final NoPublicPath e) {
                    Log.e(TAG, "sync error", e);
                } catch (final OperationApplicationException e) {
                    Log.e(TAG, "sync error", e);
                } catch (final InterruptedException e) {
                    Log.w(TAG, "interrupted", e);
                } finally {
                    clearNotification();
                }
            }
        }
    }

    private static class SyncItem implements Comparable<SyncItem> {
        final Uri uri;
        final Bundle extras;
        final int priority; // higher priority is more likely to occur
        final long creationTime = System.currentTimeMillis();

        public SyncItem(Uri uri, Bundle extras, int priority) {
            this.uri = uri;
            this.extras = extras;
            this.priority = priority;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SyncItem) {
                final SyncItem osi = (SyncItem) o;
                return this.priority == osi.priority
                        && (uri != null ? uri.equals(osi.uri) : uri != osi.uri)
                        && (extras != null ? extras.equals(osi.extras) : extras != osi.extras);
            } else {
                return false;
            }
        }

        @Override
        public int compareTo(SyncItem another) {
            if (priority == another.priority) {
                return -/* inverted */Long.valueOf(creationTime).compareTo(another.creationTime);
            } else {
                return -/* inverted */Integer.valueOf(priority).compareTo(another.priority);
            }
        }

        @Override
        public String toString() {

            return "SyncItem: uri=" + uri + ", extras=" + extras + ", priority=" + priority
                    + ", creationTime=" + creationTime;
        }
    }
}
