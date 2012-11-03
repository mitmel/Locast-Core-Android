package edu.mit.mobile.android.locast.data;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.IntegerColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

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

    @Override
    public SyncMap getSyncMap() {
        return SYNC_MAP;
    }

    public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

    public static class VideoResourcesSync extends ResourcesSync {
        public static final String KEY_WEB_STREAM = "web_stream";
        public static final String KEY_SCREENSHOT = "screenshot";
        public static final String KEY_ANIMATED_PREVIEW = "preview";

        @Override
        protected void fromResourcesJSON(Context context, Uri localItem, ContentValues cv,
                JSONObject resources) throws NetworkProtocolException, JSONException {
            // this adds in the primary resource
            // intentionally not calling super here, so the primary resource can be made optional.
            // This is until the API can be revised to make linkedmedia behave like the other types.
            // super.fromResourcesJSON(context, localItem, cv, resources);

            addToContentValues(cv, "primary", resources, CastMedia.COL_MEDIA_URL,
                    CastMedia.COL_MIME_TYPE, false);

            addToContentValues(cv, KEY_WEB_STREAM, resources, COL_WEB_STREAM, null, false);
            addToContentValues(cv, KEY_SCREENSHOT, resources, COL_SCREENSHOT, null, false);
            addToContentValues(cv, KEY_ANIMATED_PREVIEW, resources, COL_ANIMATED_PREVIEW, null,
                    false);
        }
    }

    public static class ItemSyncMap extends CastMedia.ItemSyncMap {

        /**
         *
         */
        private static final long serialVersionUID = -5953501614068337339L;

        protected static final String RESOURCES_KEY = "_resources";

        public ResourcesSync getResourcesSync() {
            return new VideoResourcesSync();
        }

        public ItemSyncMap() {
            super();

            put(RESOURCES_KEY, getResourcesSync());

            put(COL_DURATION, new SyncFieldMap("duration", SyncFieldMap.DURATION,
                    SyncFieldMap.SYNC_FROM | SyncFieldMap.FLAG_OPTIONAL));

            // this is used for linkedmedia resources.
            put(COL_MEDIA_URL, new SyncFieldMap("url", SyncFieldMap.STRING, SyncFieldMap.SYNC_FROM
                    | SyncFieldMap.FLAG_OPTIONAL));

        }
    }
}
