package edu.mit.mobile.android.locast.app;

import android.accounts.Account;
import android.content.Context;
import edu.mit.mobile.android.locast.net.LocastApplicationCallbacks;
import edu.mit.mobile.android.locast.net.NetworkClient;

/**
 * A default implementation of {@link LocastApplicationCallbacks}
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public class LocastApplication extends android.app.Application implements
        LocastApplicationCallbacks {

    @Override
    public NetworkClient getNetworkClientForAccount(Context context, Account account) {
        return NetworkClient.getInstance(context, account);
    }

    public static NetworkClient getNetworkClient(Context context, Account account) {
        return ((LocastApplicationCallbacks) context.getApplicationContext()).getNetworkClientForAccount(
                context, account);
    }
}
