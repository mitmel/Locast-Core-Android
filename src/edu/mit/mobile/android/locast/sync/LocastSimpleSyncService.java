package edu.mit.mobile.android.locast.sync;

import java.io.IOException;
import java.util.concurrent.PriorityBlockingQueue;

import org.json.JSONException;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.net.NotificationProgressListener;
import edu.mit.mobile.android.locast.notifications.ProgressNotification;
import edu.mit.mobile.android.locast.ver2.R;

/**
 * <p>
 * A sync manager for {@link SyncEngine} that uses a simplified interface to let activities sync
 * content. Requests are put in a {@link PriorityBlockingQueue} and are handled in reverse
 * chronological order (most recent requests are first). This will be used when
 * {@link LocastSyncService} and the accounts / sync framework isn't being used.
 * </p>
 *
 * <p>
 * Requests should be made using {@link LocastSync#startSync(Context, Uri, boolean, Bundle)} and its
 * various permutations.
 * </p>
 *
 */
public class LocastSimpleSyncService extends Service {
	private static final String TAG = LocastSimpleSyncService.class.getSimpleName();

	public static final boolean DEBUG = Constants.DEBUG;

	private SyncEngine mSyncEngine;

	private NetworkClient mNetworkClient;

	private SyncQueueProcessor mSyncProcessor;
	private Thread mSyncThread;

	private final PriorityBlockingQueue<SyncItem> mPriorityQueue = new PriorityBlockingQueue<LocastSimpleSyncService.SyncItem>();

	public static void startSync(Context context, Uri uri, Bundle extras) {
		final Intent intent = new Intent(Intent.ACTION_SYNC, uri);
		intent.putExtras(extras);
		context.startService(intent);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mNetworkClient = new NetworkClient(this);

		mSyncEngine = new SyncEngine(this, mNetworkClient);
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

	private NotificationProgressListener showNotification() {
		final ProgressNotification notification = new ProgressNotification(this,
				getString(R.string.sync_notification), ProgressNotification.TYPE_GENERIC,
				PendingIntent.getActivity(
						this,
						0,
						getPackageManager().getLaunchIntentForPackage(getPackageName()).addFlags(
								Intent.FLAG_ACTIVITY_NEW_TASK), 0), false);

		final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		final NotificationProgressListener npl = new NotificationProgressListener(nm, notification,
				100, 0);

		nm.notify(R.id.sync, notification);

		return npl;
	}

	private void clearNotification() {
		final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(R.id.sync);
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
					mSyncEngine.sync(item.uri, null, item.extras, getContentResolver()
							.acquireContentProviderClient(MediaProvider.AUTHORITY), sr);
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

		public SyncItem(Uri uri, Bundle extras) {
			this(uri, extras, 0);
		}

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
