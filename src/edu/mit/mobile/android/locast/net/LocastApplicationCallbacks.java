package edu.mit.mobile.android.locast.net;

import android.accounts.Account;
import android.content.Context;

public interface LocastApplicationCallbacks {

    /**
     * Implement this to get the network client that will be used for the given account.
     * 
     * @param context
     * @param account
     * @return
     */
    public NetworkClient getNetworkClientForAccount(Context context, Account account);
}
