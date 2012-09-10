package edu.mit.mobile.android.locast.data;

import android.database.Cursor;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.IntegerColumn;
import edu.mit.mobile.android.content.column.TextColumn;

public abstract class VideoContent extends CastMedia {

    @DBColumn(type = IntegerColumn.class)
    public final static String COL_DURATION = "duration";

    @DBColumn(type = TextColumn.class)
    public final static String COL_SCREENSHOT = "screenshot";

    @DBColumn(type = TextColumn.class)
    public final static String COL_ANIMATED_PREVIEW = "preview";

    @DBColumn(type = TextColumn.class)
    public final static String COL_WEB_STREAM = "web_stream";

    public VideoContent(Cursor c) {
        super(c);
    }

    public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

    public static class ItemSyncMap extends CastMedia.ItemSyncMap {

        /**
         *
         */
        private static final long serialVersionUID = -5953501614068337339L;

        public ItemSyncMap() {
            super();

            // XXX this belongs in resources
            put(COL_DURATION, new SyncFieldMap("duration", SyncFieldMap.DURATION,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

            put(COL_SCREENSHOT, new SyncFieldMap("screenshot", SyncFieldMap.STRING,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

            put(COL_ANIMATED_PREVIEW, new SyncFieldMap("preview", SyncFieldMap.STRING,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

            put(COL_WEB_STREAM, new SyncFieldMap("web_stream", SyncFieldMap.STRING,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
        }
    }

}
