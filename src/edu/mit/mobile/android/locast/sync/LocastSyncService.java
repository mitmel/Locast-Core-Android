package edu.mit.mobile.android.locast.sync;

import android.accounts.Account;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;

/**
 * <p>
 * This is a thin wrapper which routes sync requests to either {@link LocastSimpleSyncService} or
 * {@link AbsLocastAccountSyncService} depending on the constant
 * {@link Constants#USE_ACCOUNT_FRAMEWORK}. This is the primary interface to making requests for
 * synchronizing content.
 * </p>
 */
public abstract class LocastSyncService extends Service {

    private static final String TAG = LocastSyncService.class.getSimpleName();
    private static final boolean DEBUG = false;
    public static final String EXTRA_ACCOUNT = "edu.mit.mobile.android.locast.EXTRA_ACCOUNT";

    final String mAuthority;

    /**
     * Convenience method to start a background sync.
     *
     * @param context
     * @param what
     *            Locast content dir or item to sync
     * @see #startSync(Account, Uri, boolean, Bundle)
     */
    public static void startSync(Context context, Uri what) {
        startSync(context, what, false);
    }

    public LocastSyncService() {
        mAuthority = getAuthority();
    }

    /**
     * Implement this to inform the superclass what authority this handles. This will only be called
     * once in the constructor, so make it count.
     *
     * @return the authority that this sync service handles.
     */
    public abstract String getAuthority();

    /**
     * Convenience method to start a sync.
     *
     * @param context
     * @param what
     *            Locast content dir or item to sync
     * @param explicitSync
     *            if the sync request was explicitly requested by the user, set this to true.
     * @see #startSync(Account, Uri, boolean, Bundle)
     */
    public static void startSync(Context context, Uri what, boolean explicitSync) {
        startSync(context, what, explicitSync, new Bundle());
    }

    public static void startExpeditedAutomaticSync(Context context, Uri what) {
        final Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

        startSync(context, what, false, extras);
    }

    /**
     * Requests a sync of the item specified by a public URL.
     *
     * @param context
     * @param httpPubUrl
     *            an http or https URL pointing to a json array of json objects (if
     *            {@code destination} is a dir uri) or a single json object.
     * @param destination
     *            the destination local uri for the result to be stored in if it's not present
     * @param explicitSync
     *            If the sync request was explicitly requested by the user, set this to true.
     * @see #startSync(Account, Uri, boolean, Bundle)
     */
    public static void startSync(Context context, Uri httpPubUrl, Uri destination,
            boolean explicitSync) {
        final Bundle b = new Bundle();
        b.putString(SyncEngine.EXTRA_DESTINATION_URI, destination.toString());

        startSync(context, httpPubUrl, explicitSync, b);
    }

    /**
     * @param context
     * @param what
     *            Locast content dir or item to sync
     * @param explicitSync
     *            If the sync request was explicitly requested by the user, set this to true. adds
     *            {@link ContentResolver#SYNC_EXTRAS_MANUAL} and
     *            {@link ContentResolver#SYNC_EXTRAS_EXPEDITED} (unless it's already present)
     *
     * @param extras
     * @see #startSync(Account, Uri, boolean, Bundle)
     */
    public static void startSync(Context context, Uri what, boolean explicitSync, Bundle extras) {
        if (explicitSync) {
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_EXPEDITED)) {
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            }
        }
        startSync(context, what, extras);
    }

    /**
     * Convenience method to request a sync.
     *
     * @param account
     * @param what
     *            Locast content dir or item to sync
     * @param explicitSync
     *            If the sync request was explicitly requested by the user, set this to true.
     * @see #startSync(Account, Uri, boolean, Bundle)
     */
    public static void startSync(Context context, Account account, Uri what) {
        final Bundle b = new Bundle();

        startSync(context, account, what, b);
    }

    /**
     * Convenience method to request a sync.
     *
     * @param account
     * @param what
     *            the uri of the item that needs to be sync'd. can be null
     * @param explicitSync
     *            if true, adds {@link ContentResolver#SYNC_EXTRAS_MANUAL} to the extras
     * @param extras
     */
    public static void startSync(Context context, Account account, Uri what, Bundle extras) {

        if (what != null) {
            extras.putString(AbsLocastAccountSyncService.EXTRA_SYNC_URI, what.toString());
        }

        if (account != null) {
            extras.putParcelable(EXTRA_ACCOUNT, account);
        }

        if (DEBUG) {
            Log.d(TAG, "requesting sync for " + account + " with extras: " + extras);
        }

        startSync(context, what, extras);
    }

    /**
     * Convenience method to request a sync.
     *
     * @param context
     * @param what
     *            the uri of the item that needs to be sync'd. can be null
     * @param extras
     */
    public static void startSync(Context context, Uri what, Bundle extras) {
        context.startService(new Intent(Intent.ACTION_SYNC, what).putExtras(extras));
    }
}
