package edu.mit.mobile.android.locast.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;

public abstract class ImageContent extends CastMedia {

    public static final String TAG = ImageContent.class.getSimpleName();

    @DBColumn(type = TextColumn.class)
    public final static String COL_THUMBNAIL = "thumbnail";

    public ImageContent(Cursor c) {
        super(c);
    }

    public static Location extractLocationFromContentUri(Cursor c) {
        // if the current location is null, infer it from the first media that's
        // added.
        final int latCol = c.getColumnIndex(Media.LATITUDE);
        final int lonCol = c.getColumnIndex(Media.LONGITUDE);
        final double lat = c.getDouble(latCol);
        final double lon = c.getDouble(lonCol);

        final boolean isInArmpit = lat == 0 && lon == 0; // Sorry, people in boats
                                                         // off the coast
                                                         // of Ghana, but you're
                                                         // an unfortunate edge
                                                         // case...
        if (!c.isNull(latCol) && !c.isNull(lonCol) && !isInArmpit) {
            final Location l = new Location("internal");
            l.setLatitude(c.getDouble(latCol));
            l.setLongitude(c.getDouble(lonCol));
            return l;
        } else {
            return null;
        }
    }

    static void extractContent(ContentResolver cr, Uri content, ContentValues cv,
            CastMediaInfo castMedia) throws MediaProcessingException {
        final Cursor c = cr.query(content, new String[] { MediaColumns._ID, MediaColumns.DATA,
                MediaColumns.TITLE, Media.LATITUDE, Media.LONGITUDE, Media.DATE_TAKEN }, null,
                null, null);
        try {
            if (c.moveToFirst()) {

                // TODO figure out how titles could work
                // cv.put(CastMedia._TITLE,
                // c.getString(c.getColumnIndexOrThrow(MediaColumns.TITLE)));

                castMedia.path = "file://"
                        + c.getString(c.getColumnIndexOrThrow(MediaColumns.DATA));

                cv.put(CastMedia.COL_CAPTURE_TIME,
                        c.getLong(c.getColumnIndexOrThrow(Media.DATE_TAKEN)));

            } else {
                Log.e(TAG, "couldn't add media from uri " + content);
                throw new MediaProcessingException("could not find content from uri: " + content);
            }
        } finally {
            c.close();
        }
    }

    public final static ItemSyncMap SYNC_MAP = new ItemSyncMap();

    public static class ItemSyncMap extends CastMedia.ItemSyncMap {
        protected static final String RESOURCES_KEY = "_resources";
        /**
         *
         */
        private static final long serialVersionUID = 7045578635861708298L;

        public ItemSyncMap() {
            put(RESOURCES_KEY, new ResourcesSync());
        }

        protected void setResourcesSync(AbsResourcesSync res) {
            put(RESOURCES_KEY, res);
        }
    }
}
