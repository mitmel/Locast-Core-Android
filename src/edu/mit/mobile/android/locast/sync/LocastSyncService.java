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

import org.json.JSONException;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

public class LocastSyncService extends Service {
	private static final String TAG = LocastSyncService.class.getSimpleName();

	private static LocastSyncAdapter SYNC_ADAPTER = null;

	public static final String EXTRA_SYNC_URI = "edu.mit.mobile.android.locast.sync.EXTRA_SYNC_URI";

	@Override
	public IBinder onBind(Intent intent) {
		return getSyncAdapter().getSyncAdapterBinder();
	}


	private static class LocastSyncAdapter extends AbstractThreadedSyncAdapter {
		private final Context mContext;

		private final SyncEngine mSyncEngine;

		public LocastSyncAdapter(Context context) {
			super(context, true);
			mContext = context;
			mSyncEngine = new SyncEngine(mContext, NetworkClient.getInstance(context));
		}

		@Override
		public void onSyncCanceled() {
			Log.d(TAG, "onSyncCanceled()");
			super.onSyncCanceled();
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority,
				ContentProviderClient provider, SyncResult syncResult) {

			final String uriString = extras.getString(EXTRA_SYNC_URI);
			Uri uri = null;
			if (uriString != null){
				uri = Uri.parse(uriString);
			}
			if (uri != null){
				Log.d(TAG, "Synchronizing "+uri+"...");
			}else{
				Log.d(TAG, "Synchronizing...");
			}

			if (uri == null){
				uri = Cast.CONTENT_URI;
			}

			try {
				mSyncEngine.sync(uri, account, extras, provider, syncResult);
			} catch (final RemoteException e) {
				e.printStackTrace();

			} catch (final SyncException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final NetworkProtocolException e) {
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
}
