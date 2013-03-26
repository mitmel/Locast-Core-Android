package edu.mit.mobile.android.locast.data.tags;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import edu.mit.mobile.android.content.ContentItem;
import edu.mit.mobile.android.content.DBHelper;
import edu.mit.mobile.android.content.m2m.IdenticalChildFinder;

public class IdenticalTagFinder implements IdenticalChildFinder {

    private static final String[] PROJECTION = new String[] { ContentItem._ID };

    @Override
    public Uri getIdenticalChild(DBHelper m2m, Uri parentChildDir, SQLiteDatabase db,
            String childTable, ContentValues values) {
        final Cursor c = db.query(childTable, PROJECTION, Tag.COL_NAME + "=?",
                new String[] { values.getAsString(Tag.COL_NAME) }, null, null, null);
        try {
            if (c.moveToFirst()) {
                return ContentUris.withAppendedId(parentChildDir,
                        c.getLong(c.getColumnIndex(Tag._ID)));
            }
        } finally {
            c.close();
        }
        return null;
    }
}
