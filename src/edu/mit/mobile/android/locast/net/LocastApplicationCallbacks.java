package edu.mit.mobile.android.locast.net;

import android.accounts.Account;
import android.content.Context;

public interface LocastApplicationCallbacks {

    public NetworkClient getNetworkClient(Context context, Account account);
}
