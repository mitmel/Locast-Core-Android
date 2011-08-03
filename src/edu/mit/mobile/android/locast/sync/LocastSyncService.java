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
