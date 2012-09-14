package edu.mit.mobile.android.locast.home;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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

import java.io.File;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticator;

public abstract class ResetActivity extends Activity implements OnClickListener {
    private static final String TAG = ResetActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reset_activity);

        // ContentResolver.cancelSync(AbsLocastAuthenticator.getFirstAccount(this),
        // MediaProvider.AUTHORITY);

        findViewById(R.id.reset).setOnClickListener(this);
        findViewById(R.id.cancel).setOnClickListener(this);
    }

    protected abstract String getAccountType();

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.reset) {
            resetEverything(this, getAccountType(), true, true);
            setResult(RESULT_OK);
            finish();
        } else if (v.getId() == R.id.cancel) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    public static void resetEverything(Context context, String accountType, boolean showNotice,
            boolean removeAccounts) {
        if (Constants.DEBUG) {
            Log.d(TAG, "erasing all data...");
        }

        if (removeAccounts) {
            final AccountManager am = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);

            for (final Account account : AbsLocastAuthenticator.getAccounts(context, accountType)) {
                am.removeAccount(account, null, null);
            }
        }

        // clear cache
        for (final File file : context.getCacheDir().listFiles()) {
            file.delete();
        }

        final ContentResolver cr = context.getContentResolver();

        // TODO delete everything here.

        if (showNotice) {
            Toast.makeText(context.getApplicationContext(), R.string.notice_databases_reset,
                    Toast.LENGTH_LONG).show();
        }
        if (Constants.DEBUG) {
            Log.d(TAG, "All Locast data has been erased.");
        }
    }
}
