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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;
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

	private static LocastSyncAdapter SYNC_ADAPTER = null;

	/**
	 * A string extra specifying the URI of the object to sync. Can be a
	 * content:// or http:// uri.
	 */
	public static final String EXTRA_SYNC_URI = "edu.mit.mobile.android.locast.sync.EXTRA_SYNC_URI";

	/**
	 * Convenience method to start a background sync of the first account found.
	 *
	 * @param context
	 * @param what
	 * @see #startSync(Account, Uri, boolean, Bundle)
	 */
	public static void startSync(Context context, Uri what) {
		startSync(Authenticator.getFirstAccount(context), what, false, new Bundle());
	}

	/**
	 * Convenience method to start a sync of the first account found.
	 *
	 * @param context
	 * @param what
	 * @param explicitSync
	 * @see #startSync(Account, Uri, boolean, Bundle)
	 */
	public static void startSync(Context context, Uri what, boolean explicitSync) {
		startSync(Authenticator.getFirstAccount(context), what, explicitSync, new Bundle());
	}

	/**
	 * @param context
	 * @param what
	 * @param explicitSync
	 * @param extras
	 * @see #startSync(Account, Uri, boolean, Bundle)
	 */
	public static void startSync(Context context, Uri what, boolean explicitSync, Bundle extras) {
		startSync(Authenticator.getFirstAccount(context), what, explicitSync, extras);
	}

	public static void startExpeditedAutomaticSync(Context context, Uri what){
		final Bundle extras = new Bundle();
		extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

		startSync(Authenticator.getFirstAccount(context), what, false, extras);
	}

	/**
	 * Requests a sync of the item specified by a public URL.
	 *
	 * @param context
	 * @param httpPubUrl
	 *            an http or https URL pointing to a json array of json objects
	 *            (if {@code destination} is a dir uri) or a single json object.
	 * @param destination
	 *            the destination local uri for the result to be stored in if
	 *            it's not present
	 * @param explicitSync
	 * @see #startSync(Account, Uri, boolean, Bundle)
	 */
	public static void startSync(Context context, Uri httpPubUrl, Uri destination,
			boolean explicitSync) {
		final Bundle b = new Bundle();
		b.putString(SyncEngine.EXTRA_DESTINATION_URI, destination.toString());

		startSync(Authenticator.getFirstAccount(context), httpPubUrl, explicitSync, b);
	}

	/**
	 * Convenience method to request a sync.
	 *
	 * @param account
	 * @param what
	 * @param explicitSync
	 * @see #startSync(Account, Uri, boolean, Bundle)
	 */
	public static void startSync(Account account, Uri what, boolean explicitSync) {
		final Bundle b = new Bundle();

		startSync(account, what, explicitSync, b);
	}

	/**
	 * Convenience method to request a sync.
	 *
	 * This wraps {@link ContentResolver#requestSync(Account, String, Bundle)}
	 * and fills in the necessary extras.
	 *
	 * @param account
	 * @param what
	 * @param explicitSync
	 *            if true, adds {@link ContentResolver#SYNC_EXTRAS_MANUAL} to
	 *            the extras
	 * @param extras
	 */
	public static void startSync(Account account, Uri what, boolean explicitSync, Bundle extras) {
		if (explicitSync) {
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		}
		extras.putString(LocastSyncService.EXTRA_SYNC_URI, what.toString());

		ContentResolver.requestSync(account, MediaProvider.AUTHORITY, extras);
	}

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
			if (uriString != null) {
				uri = Uri.parse(uriString);
			}
			if (uri != null) {
				Log.d(TAG, "Synchronizing " + uri + "...");
			} else {
				Log.d(TAG, "Synchronizing...");
			}

			if (uri == null) {
				uri = Cast.CONTENT_URI;
			}

			try {
				if (uri != null) {
					mSyncEngine.sync(uri, account, extras, provider, syncResult);
				} else {
					mSyncEngine.sync(Cast.CONTENT_URI, account, extras, provider, syncResult);
					mSyncEngine.sync(Itinerary.CONTENT_URI, account, extras, provider, syncResult);
				}
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
			} catch (final NoPublicPath e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final OperationApplicationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	private LocastSyncAdapter getSyncAdapter() {
		if (SYNC_ADAPTER == null) {
			SYNC_ADAPTER = new LocastSyncAdapter(this);
		}
		return SYNC_ADAPTER;
	}
}
