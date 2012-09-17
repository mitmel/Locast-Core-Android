package edu.mit.mobile.android.locast.data;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.content.Context;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticationService;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncMapChain;

/**
 * The content item has an author.
 *
 */
public abstract class Authorable {

    public interface Columns {
        @DBColumn(type = TextColumn.class)
        public static final String COL_AUTHOR = "author";

        @DBColumn(type = TextColumn.class, notnull = true)
        public static final String COL_AUTHOR_URI = "author_uri";

    }

    /**
     * Adds the author information from the provided account to the given ContentValues
     * 
     * @param context
     * @param account
     * @param cv
     * @return
     */
    public static final ContentValues putAuthorInformation(Context context, Account account,
            ContentValues cv) {
        final AccountManager am = AccountManager.get(context);
        final String name = am.getUserData(account,
                AbsLocastAuthenticationService.USERDATA_DISPLAY_NAME);
        final String userUri = am.getUserData(account,
                AbsLocastAuthenticationService.USERDATA_USER_URI);

        // TODO this could probably be fixed so the user info is stored in a separate table.
        cv.put(Columns.COL_AUTHOR, name);
        cv.put(Columns.COL_AUTHOR_URI, userUri);

        return cv;
    }

    public static final SyncMap SYNC_MAP = new SyncMap();

    static {

        final SyncMap authorSync = new SyncMap();
        authorSync.put(Columns.COL_AUTHOR, new SyncFieldMap("display_name", SyncFieldMap.STRING,
                SyncItem.FLAG_OPTIONAL));
        authorSync.put(Columns.COL_AUTHOR_URI, new SyncFieldMap("uri", SyncFieldMap.STRING));
        SYNC_MAP.put("_author", new SyncMapChain("author", authorSync, SyncItem.SYNC_FROM));
    }
}
