package edu.mit.mobile.android.locast.data.interfaces;

import android.accounts.Account;
import android.content.Context;
import android.database.Cursor;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticator;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.SyncMap;

public abstract class PrivatelyAuthorableUtils extends AuthorableUtils {

    /**
     * The current user can be gotten using {@link AbsLocastAuthenticator#getUserUri(Context)}. This
     * requires that the cursor has loaded {@link Authorable#COL_PRIVACY} and
     * {@link Authorable#COL_AUTHOR_URI}.
     *
     * @param userUri
     *            the user in question
     * @param c
     *            a cursor pointing at an item's row
     * @return true if the item is editable by the specified user.
     */
    public static boolean canEdit(String userUri, Cursor c) {
        if (userUri == null || userUri.length() == 0) {
            throw new IllegalArgumentException("userUri must not be null or empty");
        }

        final String privacy = c.getString(c.getColumnIndexOrThrow(PrivatelyAuthorable.COL_PRIVACY));
        final String itemAuthor = c.getString(c.getColumnIndexOrThrow(PrivatelyAuthorable.COL_AUTHOR_URI));

        return userUri.equals(itemAuthor)
                || (privacy != null && PrivatelyAuthorable.PRIVACY_PUBLIC.equals(privacy));
    }

    /**
     * @param c
     * @return true if the authenticated user can change the item's privacy level.
     */
    public static boolean canChangePrivacyLevel(Context context, Account account, Cursor c) {
        final String useruri = AbsLocastAuthenticator.getUserUri(context, account);
        return useruri == null
                || useruri.equals(c.getString(c.getColumnIndex(PrivatelyAuthorable.COL_AUTHOR_URI)));
    }

    public static final SyncMap SYNC_MAP = new PrivatelyAuthorableSyncMap();

    public static class PrivatelyAuthorableSyncMap extends SyncMap{
        /**
         *
         */
        private static final long serialVersionUID = 678035026097110298L;

        public PrivatelyAuthorableSyncMap() {
            putAll(AuthorableUtils.SYNC_MAP);
            put(PrivatelyAuthorable.COL_PRIVACY, new SyncFieldMap("privacy", SyncFieldMap.STRING));
        }
    }
}
