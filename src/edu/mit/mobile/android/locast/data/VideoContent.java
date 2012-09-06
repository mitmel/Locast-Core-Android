package edu.mit.mobile.android.locast.data;

import android.database.Cursor;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.IntegerColumn;
import edu.mit.mobile.android.content.column.TextColumn;

public abstract class VideoContent extends CastMedia {

    @DBColumn(type = IntegerColumn.class)
    public final static String _DURATION = "duration";

    @DBColumn(type = TextColumn.class)
    public final static String _SCREENSHOT = "screenshot";

    @DBColumn(type = TextColumn.class)
    public final static String _ANIMATED_PREVIEW = "preview";

    @DBColumn(type = TextColumn.class)
    public final static String _WEB_STREAM = "web_stream";

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
            put(_DURATION, new SyncFieldMap("duration", SyncFieldMap.DURATION,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

            put(_SCREENSHOT, new SyncFieldMap("screenshot", SyncFieldMap.STRING,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

            put(_ANIMATED_PREVIEW, new SyncFieldMap("preview", SyncFieldMap.STRING,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

            put(_WEB_STREAM, new SyncFieldMap("web_stream", SyncFieldMap.STRING,
                    SyncFieldMap.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
        }
    }

}
