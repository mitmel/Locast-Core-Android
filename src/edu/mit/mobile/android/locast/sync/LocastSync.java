package edu.mit.mobile.android.locast.sync;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.accounts.Authenticator;

/**
 * <p>
 * This is a thin wrapper which routes sync requests to either {@link LocastSimpleSyncService} or
 * {@link LocastSyncService} depending on the constant {@link Constants#USE_ACCOUNT_FRAMEWORK}. This
 * is the primary interface to making requests for synchronizing content.
 * </p>
 */
public class LocastSync {

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
		if (Constants.USE_ACCOUNT_FRAMEWORK) {
			startSync(Authenticator.getFirstAccount(context), what, extras);
		} else {
			LocastSimpleSyncService.startSync(context, what, extras);
		}
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
	public static void startSync(Account account, Uri what) {
		final Bundle b = new Bundle();

		startSync(account, what, b);
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
	public static void startSync(Account account, Uri what, Bundle extras) {

		if (Constants.USE_ACCOUNT_FRAMEWORK) {
			LocastSyncService.startSync(account, what, extras);
		} else {
			throw new IllegalArgumentException(
					"Sync requested using an account, but account framework is not in use");
		}
	}
}
