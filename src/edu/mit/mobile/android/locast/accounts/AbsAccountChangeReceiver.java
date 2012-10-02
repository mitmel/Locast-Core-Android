package edu.mit.mobile.android.locast.accounts;

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
import java.util.Arrays;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.R;

public abstract class AbsAccountChangeReceiver extends BroadcastReceiver {
    private static final String TAG = AbsAccountChangeReceiver.class.getSimpleName();

    private static final String PREF_ACCOUNT_NAMES = "accountNames";

    private static final String ACCOUNT_STRING_DELIMITER = ",";


    @Override
    public void onReceive(Context context, Intent intent) {
        final AccountManager am = AccountManager.get(context);

        final SharedPreferences prefs = context.getSharedPreferences("account",
                Context.MODE_PRIVATE);
        final String existingAccountString = prefs.getString(PREF_ACCOUNT_NAMES, "");
        final Account[] currentAccounts = am.getAccountsByType(getAccountType());
        Arrays.sort(currentAccounts);
        final String[] lastKnownAccounts = existingAccountString.length() > 0 ? existingAccountString
                .split(ACCOUNT_STRING_DELIMITER) : new String[0];

        // not very efficient for large numbers, but we only ever expect to have 0 or 1 accounts
        // maybe 2 if we're unlucky.
        for (final String lastKnownAccount : lastKnownAccounts) {

            boolean found = false;
            for (final Account currentAccount : currentAccounts) {
                if (currentAccount.name.equals(lastKnownAccount)) {
                    found = true;
                    break;
                }
            }

            // account has been deleted
            if (!found) {
                onAccountDeleted(context, new Account(lastKnownAccount,
 getAccountType()));
            }
        }

        final StringBuilder accountString = new StringBuilder();
        final boolean first = true;
        for (final Account currentAccount : currentAccounts) {
            boolean found = false;
            for (final String lastKnownAccount : lastKnownAccounts) {
                if (currentAccount.name.equals(lastKnownAccount)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                onAccountAdded(context, currentAccount);
            }
            if (!first) {
                accountString.append(ACCOUNT_STRING_DELIMITER);
            }
            accountString.append(currentAccount.name);
        }

        prefs.edit().putString(PREF_ACCOUNT_NAMES, accountString.toString()).commit();
    }

    private void onAccountDeleted(Context context, Account account) {
        if (Constants.DEBUG) {
            Log.d(TAG, "Locast account removed: " + account);
        }

        // only delete the database when it was a real account
        if (AbsLocastAuthenticator.hasRealAccount(context, getAccountType())) {
            resetEverything(context, getAccountType(), false, false);
        }
    }

    private void onAccountAdded(Context context, Account account) {
        // that's swell! we don't really need to do anything.
        if (Constants.DEBUG) {
            Log.d(TAG, "Locast account added: " + account);
        }
    }

    protected void resetEverything(Context context, String accountType, boolean showNotice,
            boolean removeAccounts) {
        if (Constants.DEBUG) {
            Log.d(TAG, "erasing all data...");
        }

        if (removeAccounts) {
            final AccountManager am = (AccountManager) context
                    .getSystemService(Context.ACCOUNT_SERVICE);

            for (final Account account : AbsLocastAuthenticator.getAccounts(context, accountType)) {
                am.removeAccount(account, null, null);
            }
        }

        // clear cache
        for (final File file : context.getCacheDir().listFiles()) {
            file.delete();
        }

        final ContentResolver cr = context.getContentResolver();

        deleteAllData(cr);

        if (showNotice) {
            Toast.makeText(context.getApplicationContext(), R.string.notice_databases_reset,
                    Toast.LENGTH_LONG).show();
        }
        if (Constants.DEBUG) {
            Log.d(TAG, "All Locast data has been erased.");
        }
    }

    /**
     * implement this to erase all the data in your content provider.
     */
    protected abstract void deleteAllData(ContentResolver cr);

    protected abstract String getAccountType();

}
