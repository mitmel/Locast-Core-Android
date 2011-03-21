package edu.mit.mobile.android.locast.sync;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class LocastSyncService extends Service {
	private static final String TAG = LocastSyncService.class.getSimpleName();

	private static LocastSyncAdapter SYNC_ADAPTER = null;

	@Override
	public IBinder onBind(Intent intent) {
		return getSyncAdapter().getSyncAdapterBinder();
	}


	private static class LocastSyncAdapter extends AbstractThreadedSyncAdapter {
		private final Context mContext;

		public LocastSyncAdapter(Context context) {
			super(context, true);
			mContext = context;
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority,
				ContentProviderClient provider, SyncResult syncResult) {
			try {
				LocastSyncService.performSync(mContext, account, extras, authority, provider, syncResult);
			} catch (final OperationCanceledException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	private LocastSyncAdapter getSyncAdapter() {
		if (SYNC_ADAPTER == null){
			SYNC_ADAPTER = new LocastSyncAdapter(this);
		}
		return SYNC_ADAPTER;
	}

	private static void performSync(Context context, Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) throws OperationCanceledException {
		Log.d(TAG, "Synchronizing...");
		// sync here

	}
}
