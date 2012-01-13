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
import android.os.Bundle;
import edu.mit.mobile.android.locast.accounts.AuthenticationService;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.casts.CastListActivity;
import edu.mit.mobile.android.locast.data.Cast;

public class UnsyncedCastsActivity extends CastListActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final Account me = Authenticator.getFirstAccount(this);
		final AccountManager am = AccountManager.get(this);

		loadList(managedQuery(Cast.CONTENT_URI, Cast.PROJECTION,
				Cast._AUTHOR_URI + " = ? AND " + Cast._PUBLIC_URI + " is null",
				new String[]{am.getUserData(me, AuthenticationService.USERDATA_USER_URI)},
				Cast._MODIFIED_DATE+" DESC"));
	}
}
