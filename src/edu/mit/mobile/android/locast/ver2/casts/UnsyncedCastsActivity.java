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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import edu.mit.mobile.android.locast.accounts.AuthenticationService;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.SigninOrSkip;
import edu.mit.mobile.android.locast.casts.CastListActivity;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.locast.ver2.R;

public class UnsyncedCastsActivity extends CastListActivity implements AccountManagerCallback<Boolean> {
	
	private Account account;
	private AccountManager accountManager;
	
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
		Button sync = (Button) findViewById(R.id.sync);
		sync.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				LocastSyncService.startSync(UnsyncedCastsActivity.this, null, true);
			}
		});
	}


	@Override
	protected void onResume() {
		super.onResume();
		
		initAccount();
		
		loadList(managedQuery(Cast.CONTENT_URI, Cast.PROJECTION,
				Cast._AUTHOR_URI + " = ? AND " + Cast._PUBLIC_URI + " is null",
				new String[]{accountManager.getUserData(account, AuthenticationService.USERDATA_USER_URI)},
				Cast._MODIFIED_DATE+" DESC"));
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
}
