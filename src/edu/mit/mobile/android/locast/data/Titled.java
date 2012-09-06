package edu.mit.mobile.android.locast.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;

/**
 * The content item has a title and description.
 *
 *
 */
public abstract class Titled {

    public interface Columns {
        @DBColumn(type = TextColumn.class, notnull = true)
        public static final String COL_TITLE = "title";

        @DBColumn(type = TextColumn.class)
        public static final String COL_DESCRIPTION = "description";
    }

    public static final SyncMap SYNC_MAP = new SyncMap();

    static {
        SYNC_MAP.put(Columns.COL_TITLE, new SyncFieldMap("title", SyncFieldMap.STRING));
        SYNC_MAP.put(Columns.COL_DESCRIPTION, new SyncFieldMap("description", SyncFieldMap.STRING,
                SyncItem.FLAG_OPTIONAL));
    }

    private static final String[] TITLE_PROJECTION = new String[] { Columns.COL_TITLE };

    public static String getTitle(Context context, Uri cast) {
        final Cursor c = context.getContentResolver().query(cast, TITLE_PROJECTION, null, null,
                null);
        String title = null;
        try {
            if (c.moveToFirst()) {
                title = c.getString(c.getColumnIndex(Columns.COL_TITLE));
            }
        } finally {
            c.close();
        }
        return title;
    }
}
