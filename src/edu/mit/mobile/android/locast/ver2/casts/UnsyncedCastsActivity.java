package edu.mit.mobile.android.locast.ver2.casts;

/*
 * Copyright (C) 2010-2012  MIT Mobile Experience Lab
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
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.accounts.AuthenticationService;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.SigninOrSkip;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.locast.sync.SyncEngine;
import edu.mit.mobile.android.locast.ver2.R;

public class UnsyncedCastsActivity extends FragmentActivity implements
		AccountManagerCallback<Boolean>, OnClickListener, LoaderCallbacks<Cursor> {

	private Account account;
	private AccountManager accountManager;

	private BroadcastReceiver syncBroadcastReceiver;
	private Button syncButton;
	private long mCurrentlySyncing;
	// private Cursor cursor;
	private UnsyncedCastsCursorAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.unsynced_cast_list);

		findViewById(R.id.logout).setOnClickListener(this);
		syncButton = (Button) findViewById(R.id.sync);
		syncButton.setOnClickListener(this);

		if (SigninOrSkip.startSignin(this, SigninOrSkip.REQUEST_SIGNIN)) {
			return;
		}

		initAccount();

		mAdapter = new UnsyncedCastsCursorAdapter(this, R.layout.unsynced_cast_list_item, null,
				new String[] { CastMedia._TITLE, CastMedia._THUMB_LOCAL }, new int[] { R.id.title,
						R.id.media_thumbnail }, new int[] { R.id.media_thumbnail }, 0);

		final ListView lv = ((ListView) findViewById(android.R.id.list));
		lv.setEmptyView(findViewById(android.R.id.empty));
		lv.setAdapter(new ImageLoaderAdapter(this, mAdapter, ImageCache.getInstance(this),
				new int[] { R.id.media_thumbnail }, 48, 48, ImageLoaderAdapter.UNIT_DIP));

		getSupportLoaderManager().initLoader(0, null, this);

		loadUsername();
	}

	@Override
	protected void onPause() {
		unbindFromSync();

		super.onPause();
	}

	private void bindToSync() {
		if (syncBroadcastReceiver != null) {
			return;
		}

		syncBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				final String status = intent.getExtras().getString(SyncEngine.EXTRA_SYNC_STATUS);
				if ("begin".equals(status)) {
					syncButton.setEnabled(false);
				} else if ("end".equals(status)) {
					syncButton.setEnabled(true);

					// cursor.requery();
				} else if ("castBegin".equals(status)) {
					mCurrentlySyncing = intent.getExtras().getLong(SyncEngine.EXTRA_SYNC_ID);
					// refreshList();
				} else if ("castEnd".equals(status)) {
					mCurrentlySyncing = -1;
					// refreshList();
				}
				getSupportLoaderManager().restartLoader(0, null, UnsyncedCastsActivity.this);
			}
		};
		registerReceiver(syncBroadcastReceiver, new IntentFilter(SyncEngine.SYNC_STATUS_CHANGED));
	}

	private void unbindFromSync() {
		if (syncBroadcastReceiver == null) {
			return;
		}

		unregisterReceiver(syncBroadcastReceiver);
		syncBroadcastReceiver = null;
	}

	private void loadUsername() {
		final TextView username = (TextView) findViewById(R.id.username);
		username.setText(accountManager.getUserData(account,
				AuthenticationService.USERDATA_DISPLAY_NAME));
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mAdapter.getCursor() != null) {
			bindToSync();
		}
	}

	private static final int DIALOG_CONFIRM = 100;

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case DIALOG_CONFIRM:
				return new AlertDialog.Builder(UnsyncedCastsActivity.this)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(R.string.logout)
						.setMessage(R.string.are_you_sure_you_want_to_logout)
						.setPositiveButton(android.R.string.yes,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										accountManager.removeAccount(account,
												UnsyncedCastsActivity.this, null);
									}
								}).setNegativeButton(android.R.string.no, null).create();
			default:
				return super.onCreateDialog(id);
		}
	}

	@Override
	public void run(AccountManagerFuture<Boolean> future) {
		SigninOrSkip.startSignin(this, SigninOrSkip.REQUEST_SIGNIN);
	}

	private void initAccount() {
		this.account = Authenticator.getFirstAccount(this);
		this.accountManager = AccountManager.get(this);
	}

	private class UnsyncedCastsCursorAdapter extends CastMediaAdapter {

		public UnsyncedCastsCursorAdapter(Context context, int layout, Cursor c, String[] from,
				int[] to, int[] imageIDs, int flags) {
			super(context, layout, c, from, to, imageIDs, flags);
		}

		@Override
		public View getView(int pos, View convertView, ViewGroup parent) {
			final View v = super.getView(pos, convertView, parent);

			final long id = getItemId(pos);

			final View syncing = v.findViewById(R.id.syncing);
			if (syncing != null) {
				syncing.setVisibility(id == mCurrentlySyncing ? View.VISIBLE : View.GONE);
			}

			return v;
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.sync: {
				final Bundle extras = new Bundle();
				extras.putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true);
				LocastSyncService.startSync(UnsyncedCastsActivity.this, Cast.CONTENT_URI, true,
						extras);
			}
				break;

			case R.id.logout:
				showDialog(DIALOG_CONFIRM);
				break;
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
		return new CursorLoader(this, CastMedia.CONTENT_URI, CastMedia.PROJECTION,
				CastMedia._PUBLIC_URI + " is null", null, CastMedia._MODIFIED_DATE + " DESC");
	}

	@Override
	public void onLoadFinished(Loader<Cursor> arg0, Cursor c) {
		mAdapter.swapCursor(c);
		if (c.moveToFirst()) {
			ProviderUtils.dumpCursorToLog(c, CastMedia.PROJECTION);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		mAdapter.swapCursor(null);

	}
}
