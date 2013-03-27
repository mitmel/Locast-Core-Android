package edu.mit.mobile.android.locast.data.interfaces;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;
import edu.mit.mobile.android.locast.data.SyncMap;

/**
 * The content item has a title and description.
 *
 *
 */
public class TitledUtils {

    public static final SyncMap SYNC_MAP = new TitledSyncMap();

    public static class TitledSyncMap extends SyncMap{
        /**
         *
         */
        private static final long serialVersionUID = 4756612166837000843L;

        public TitledSyncMap() {
            put(Titled.COL_TITLE, new SyncFieldMap("title", SyncFieldMap.STRING));
            put(Titled.COL_DESCRIPTION, new SyncFieldMap("description", SyncFieldMap.STRING,
                    SyncItem.FLAG_OPTIONAL));
        }
    }

    private static final String[] TITLE_PROJECTION = new String[] { Titled.COL_TITLE };

    public static String getTitle(Context context, Uri cast) {
        final Cursor c = context.getContentResolver().query(cast, TITLE_PROJECTION, null, null,
                null);
        String title = null;
        try {
            if (c.moveToFirst()) {
                title = c.getString(c.getColumnIndex(Titled.COL_TITLE));
            }
        } finally {
            c.close();
        }
        return title;
    }
}
