package edu.mit.mobile.android.locast.ver2.casts;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import edu.mit.mobile.android.locast.accounts.AuthenticationService;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.SigninOrSkip;
import edu.mit.mobile.android.locast.casts.CastListActivity;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.locast.sync.SyncEngine;
import edu.mit.mobile.android.locast.ver2.R;

public class UnsyncedCastsActivity extends CastListActivity implements AccountManagerCallback<Boolean> {
	
	private Account account;
	private AccountManager accountManager;
	
	private BroadcastReceiver syncBroadcastReceiver;
	private Button syncButton;
	private long currentlySyncing;
	private Cursor cursor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (!Authenticator.hasRealAccount(this)) {
			SigninOrSkip.startSignin(this, SigninOrSkip.REQUEST_SIGNIN);
			return;
		}
		
		initAccount();
		
		loadUsername();
		hookLogoutButton();
		hookSyncButton();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		unbindFromSync();
	}

	private void bindToSync() {
		if (syncBroadcastReceiver != null) return;
		
		syncBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String status = intent.getExtras().getString(SyncEngine.EXTRA_SYNC_STATUS);
				if ("begin".equals(status)) {
					syncButton.setText(R.string.syncing);
					syncButton.setEnabled(false);
				} else if ("end".equals(status)) {
					syncButton.setText(R.string.sync);
					syncButton.setEnabled(true);
					
					cursor.requery();
				} else if ("castBegin".equals(status)) {
					currentlySyncing = intent.getExtras().getLong(SyncEngine.EXTRA_SYNC_ID);
					refreshList();
				} else if ("castEnd".equals(status)) {
					currentlySyncing = -1;
					refreshList();
				}
			}
			
			private void refreshList() {
				if (adapter instanceof CursorAdapter) {
					((CursorAdapter) adapter).notifyDataSetChanged();
				}
			}
			
		};
		registerReceiver(syncBroadcastReceiver, new IntentFilter(SyncEngine.SYNC_STATUS_CHANGED));
	}
	
	private void unbindFromSync() {
		if (syncBroadcastReceiver == null) return;
		
		unregisterReceiver(syncBroadcastReceiver);
		syncBroadcastReceiver = null;
	}

	private void loadUsername() {
		TextView username = (TextView) findViewById(R.id.username);
		username.setText(accountManager.getUserData(account, AuthenticationService.USERDATA_DISPLAY_NAME));
	}
	
	private void hookLogoutButton() {
		Button logout = (Button) findViewById(R.id.logout);
		logout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				new AlertDialog.Builder(UnsyncedCastsActivity.this)
		        .setIcon(android.R.drawable.ic_dialog_alert)
		        .setTitle(R.string.logout)
		        .setMessage(R.string.are_you_sure_you_want_to_logout)
		        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
		            @Override
		            public void onClick(DialogInterface dialog, int which) {
		            	accountManager.removeAccount(account, UnsyncedCastsActivity.this, null);
		            }
		        })
		        .setNegativeButton(android.R.string.no, null)
		        .show();
			}
		});
	}
	
	private void hookSyncButton() {
		syncButton = (Button) findViewById(R.id.sync);
		syncButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				Bundle extras = new Bundle();
				extras.putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true);
				LocastSyncService.startSync(UnsyncedCastsActivity.this, Cast.CONTENT_URI, true, extras);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		initAccount();
		
		cursor = managedQuery(Cast.CONTENT_URI, Cast.PROJECTION,
				Cast._AUTHOR_URI + " = ? AND " + Cast._PUBLIC_URI + " is null",
				new String[]{accountManager.getUserData(account, AuthenticationService.USERDATA_USER_URI)},
				Cast._MODIFIED_DATE+" DESC");
		loadList(cursor);
		
		bindToSync();
	}
	
	@Override
	protected int getContentView() {
		return R.layout.unsynced_cast_list;
	}

	@Override
	public void run(AccountManagerFuture<Boolean> future) {
		SigninOrSkip.startSignin(this, SigninOrSkip.REQUEST_SIGNIN);
	}
	
	private void initAccount() {
		this.account = Authenticator.getFirstAccount(this);
		this.accountManager = AccountManager.get(this);
	}
	
	@Override
	protected ListAdapter getAdapter(Cursor c) {
		return new UnsyncedCastsCursorAdapter(this, R.layout.unsynced_cast_list_item, c, new String[]{}, new int[]{});
	}
	
	private class UnsyncedCastsCursorAdapter extends SimpleCursorAdapter {

		public UnsyncedCastsCursorAdapter(Context context, int layout,
				Cursor c, String[] from, int[] to) {
			super(context, layout, c, from, to);
		}
		
		@Override
		public View getView(int pos, View inView, ViewGroup parent) {
			View v = inView;
			if (v == null) {
				LayoutInflater inflater = (LayoutInflater) getBaseContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.unsynced_cast_list_item, null);
			}
			this.getCursor().moveToPosition(pos);
			
			TextView title = (TextView) v.findViewById(android.R.id.text1);
			title.setText(this.getCursor().getString(this.getCursor().getColumnIndex(Cast._TITLE)));
			
			long id = this.getCursor().getLong(this.getCursor().getColumnIndex(JsonSyncableItem._ID));
			
			TextView draft = (TextView) v.findViewById(R.id.draft);
			draft.setVisibility(id == currentlySyncing ? View.VISIBLE : View.INVISIBLE);
			
			return v;

		}
		
	}
}
