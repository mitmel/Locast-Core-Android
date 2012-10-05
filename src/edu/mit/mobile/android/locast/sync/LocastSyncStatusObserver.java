package edu.mit.mobile.android.locast.sync;

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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncStatusObserver;
import android.os.Handler;
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;

/**
 * A implementation of a SyncStatusObserver that will be used to monitor the current account
 * synchronization and notify to a specific handler when the process is ended
 *
 * @author Cristian Piacente
 *
 */
public class LocastSyncStatusObserver implements SyncStatusObserver {

    Context mContext;
    Handler mHandler;
    private final String mAuthority;
    private final Account mAccount;

    public static final int MSG_SET_REFRESHING = 100, MSG_SET_NOT_REFRESHING = 101;

    private static final String TAG = LocastSyncStatusObserver.class.getSimpleName();

    public LocastSyncStatusObserver(Context context, Account account, String authority,
            Handler handler) {
        this.mContext = context;
        this.mHandler = handler;
        this.mAuthority = authority;
        mAccount = account;
    }

    @Override
    public void onStatusChanged(int which) {
        notifySyncStatusToHandler(mContext, mHandler);
    }

    private void notifySyncStatusToHandler(Context context, Handler handler) {
        final Account a = mAccount;
        if (!ContentResolver.isSyncActive(a, mAuthority)
                && !ContentResolver.isSyncPending(a, mAuthority)) {
            if (Constants.DEBUG) {
                Log.d(TAG, "Sync finished, should refresh now!!");
            }
            handler.sendEmptyMessage(MSG_SET_NOT_REFRESHING);
        } else {
            if (Constants.DEBUG) {
                Log.d(TAG, "Sync Active or Pending!!");
            }
            handler.sendEmptyMessage(MSG_SET_REFRESHING);
        }
    }
}
