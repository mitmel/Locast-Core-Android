package edu.mit.mobile.android.locast.data;

/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import com.google.android.maps.GeoPoint;

import edu.mit.mobile.android.content.column.BooleanColumn;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.DatetimeColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticationService;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticator;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.sync.MediaSync;

public abstract class CastMedia extends JsonSyncableItem {
    private static final String TAG = CastMedia.class.getSimpleName();

    @DBColumn(type = DatetimeColumn.class)
    public final static String COL_CAPTURE_TIME = "capture_time";

    @DBColumn(type = TextColumn.class)
    public final static String COL_MEDIA_URL = "media_url"; // the body of the object

    @DBColumn(type = TextColumn.class)
    public final static String COL_LOCAL_URL = "local_url"; // any local copy of the main media

    @DBColumn(type = TextColumn.class)
    public final static String COL_MIME_TYPE = "mimetype"; // type of the media

    @DBColumn(type = BooleanColumn.class)
    public final static String COL_KEEP_OFFLINE = "offline";

    // TODO move this to ImageContent, etc.
    @DBColumn(type = TextColumn.class)
    public final static String COL_THUMB_LOCAL = "local_thumb"; // filename of the local thumbnail

    //@formatter:off
    public static final String
        MIMETYPE_HTML = "text/html",
        MIMETYPE_3GPP = "video/3gpp",
        MIMETYPE_MPEG4 = "video/mpeg4";
    //@formatter:on

    public CastMedia(Cursor c) {
        super(c);
    }

    @Override
    public SyncMap getSyncMap() {

        return SYNC_MAP;
    }

    public Uri getMedia(int mediaCol, int mediaLocalCol) {
        Uri media;
        if (!isNull(mediaLocalCol)) {
            media = Uri.parse(getString(mediaLocalCol));
        } else if (!isNull(mediaCol)) {
            media = Uri.parse(getString(mediaCol));
        } else {
            media = null;
        }
        return media;
    }

    public static Intent showMedia(Context context, Cursor c, Uri castMediaUri) {
        final String mediaString = c.getString(c.getColumnIndex(CastMedia.COL_MEDIA_URL));
        final String locMediaString = c.getString(c.getColumnIndex(CastMedia.COL_LOCAL_URL));
        String mimeType = null;

        Uri media;

        if (locMediaString != null) {
            media = Uri.parse(locMediaString);
            if ("file".equals(media.getScheme())) {
                mimeType = c.getString(c.getColumnIndex(CastMedia.COL_MIME_TYPE));
            }

        } else if (mediaString != null) {
            media = Uri.parse(mediaString);
            mimeType = c.getString(c.getColumnIndex(CastMedia.COL_MIME_TYPE));

            // we strip this because we don't really want to force them to go to the browser.
            if ("text/html".equals(mimeType)) {
                mimeType = null;
            }
        } else {
            Log.e(TAG, "asked to show media for " + castMediaUri + " but there was nothing to show");
            return null;
        }

        final Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(media, mimeType);

        if (mimeType != null && mimeType.startsWith("video/")) {
            return new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(castMediaUri,
                    c.getLong(c.getColumnIndex(CastMedia._ID))));
        } else {
            // setting the MIME type for URLs doesn't work.
            if (i.resolveActivity(context.getPackageManager()) != null) {
                return i;
            } else {
                // try it again, but without a mime type.
                if (mimeType != null) {
                    i.setDataAndType(media, null);
                }
                if (i.resolveActivity(context.getPackageManager()) != null) {
                    return i;
                } else {
                    return null;
                }
            }
        }
    }

    /**
     * Adds the given media to a castMedia uri.
     *
     * If the media is a content:// uri, queries the content resolver to get the needed information.
     *
     * @param context
     * @param castMedia
     *            uri of the cast media dir to insert into
     * @param content
     *            uri of the media to add
     * @return a summary of the information discovered from the content uri
     * @throws MediaProcessingException
     */
    public static CastMediaInfo addMedia(Context context, Uri castMedia, Uri content,
            ContentValues cv) throws MediaProcessingException {

        final ContentResolver cr = context.getContentResolver();

        final long now = System.currentTimeMillis();

        cv.put(CastMedia.COL_MODIFIED_DATE, now);
        cv.put(CastMedia.COL_CREATED_DATE, now);

        final String mimeType = getContentType(cr, content);
        cv.put(CastMedia.COL_MIME_TYPE, mimeType);

        String mediaPath;

        // only add in credentials on inserts
        final Account me = AbsLocastAuthenticator.getFirstAccount(context);
        final AccountManager am = AccountManager.get(context);

        final String displayName = am.getUserData(me, AbsLocastAuthenticationService.USERDATA_DISPLAY_NAME);
        final String authorUri = am.getUserData(me, AbsLocastAuthenticationService.USERDATA_USER_URI);

        // for media that use content URIs, we need to query the content provider to look up all the
        // details

        final CastMediaInfo castMediaInfo = new CastMediaInfo();
        GeoPoint location;

        if ("content".equals(content.getScheme())) {
            // XXX figure out how to make this work with a static method. Maybe make this method
            // non-static?
            // extractContent(cr, content, cv, castMediaInfo);

            mediaPath = null; // XXX
        } else if ("file".equals(content.getScheme())) {
            if (!new File(content.getPath()).exists()) {
                throw new MediaProcessingException("specified media file does not exist: "
                        + content);
            }
            mediaPath = content.toString();
            location = null;
        } else {
            throw new MediaProcessingException("Don't know how to process URI scheme "
                    + content.getScheme() + " for " + content);
        }

        if ("image/jpeg".equals(mimeType)) {
            try {
                String exifDateTime = "";
                final ExifInterface exif = new ExifInterface(mediaPath);
                exifDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
                cv.put(CastMedia.COL_CAPTURE_TIME, exifDateTime);

            } catch (final IOException ioex) {
                Log.e(TAG, "EXIF: Couldn't find media: " + mediaPath);
            }
        }

        // ensure that there's always a capture time, even if it's faked.
        if (!cv.containsKey(CastMedia.COL_CAPTURE_TIME)) {
            Log.w(TAG, "faking capture time with current time");
            cv.put(CastMedia.COL_CAPTURE_TIME, now);
        }

        cv.put(CastMedia.COL_THUMB_LOCAL, mediaPath);
        cv.put(CastMedia.COL_LOCAL_URL, mediaPath);

        Log.d(TAG, "addMedia(" + castMedia + ", " + cv + ")");
        castMediaInfo.castMediaItem = cr.insert(castMedia, cv);

        return castMediaInfo;

    }

    private static String getContentType(ContentResolver cr, Uri content) {
        String mimeType = cr.getType(content);
        if (mimeType == null) {
            mimeType = CastMedia.guessContentTypeFromUrl(content.toString());
        }
        return mimeType;
    }

    public static class CastMediaInfo {

        /**
         * The content's title
         */
        protected String title;

        /**
         * the content's MIME type
         */
        protected String mimeType;
        /**
         * the content:// URI of the newly created cast media item
         */
        protected Uri castMediaItem;
        /**
         * if location information was discovered from the media's metadata, this location is
         * extracted. Otherwise null.
         */
        protected GeoPoint location;
        /**
         * the path on disk to the media
         */
        protected String path;

        public CastMediaInfo() {

        }

        /**
         * @param path
         * @param mimeType
         * @param castMediaItem
         * @param location
         */
        public CastMediaInfo(String path, String mimeType, Uri castMediaItem, GeoPoint location) {
            this.path = path;
            this.mimeType = mimeType;
            this.castMediaItem = castMediaItem;
            this.location = location;
        }

    }

    public static Uri getThumbnail(Cursor c, int thumbCol, int thumbLocalCol) {
        Uri thumbnail;
        if (!c.isNull(thumbLocalCol)) {
            thumbnail = Uri.parse(c.getString(thumbLocalCol));
        } else if (!c.isNull(thumbCol)) {
            thumbnail = Uri.parse(c.getString(thumbCol));
        } else {
            thumbnail = null;
        }
        return thumbnail;
    }

    /**
     * Guesses the mime type from the URL
     *
     * @param url
     * @return the inferred mime type based on the file extension or null if it can't determine one
     */
    public static String guessContentTypeFromUrl(String url) {

        // this was improved in Gingerbread
        // http://code.google.com/p/android/issues/detail?id=10100
        // so we have some pre-defined types here so we can make sure to return SOMETHING.
        String mimeType = URLConnection.guessContentTypeFromName(url);
        if (mimeType != null) {
            return mimeType;
        }

        if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
            mimeType = "image/jpeg";

        } else if (url.endsWith(".3gp")) {
            mimeType = "video/3gpp";

        } else if (url.endsWith(".mp4") || url.endsWith(".mpeg4")) {
            mimeType = "video/mp4";

        } else if (url.endsWith(".png")) {
            mimeType = "image/png";
        }

        return mimeType;
    }

    public final static ItemSyncMap SYNC_MAP = new ItemSyncMap();

    public static class ItemSyncMap extends JsonSyncableItem.ItemSyncMap {
        /**
         *
         */
        private static final long serialVersionUID = 8477549708016150941L;

        public ItemSyncMap() {
            super();

            this.addFlag(FLAG_PARENT_MUST_SYNC_FIRST);

            // put("_resources", );

            // no MIME type is passed with a link media type, so we need to add one in.
            put("_content_type", new SyncCustom("content_type", SyncItem.SYNC_BOTH) {

                @Override
                public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
                        throws JSONException, NetworkProtocolException, IOException {
                    String mimeType = c.getString(c.getColumnIndex(COL_MIME_TYPE));
                    final String localUri = c.getString(c.getColumnIndex(COL_LOCAL_URL));

                    if (mimeType == null && localUri != null) {

                        // XXX context.getContentResolver().getType(url);

                        mimeType = guessContentTypeFromUrl(localUri);
                        if (mimeType != null) {
                            Log.d(TAG, "guessed MIME type from uri: " + localUri + ": " + mimeType);
                        }
                    }

                    if (mimeType == null) {
                        return null;
                    }

                    if (mimeType.startsWith("video/")) {
                        return "videomedia";
                    } else if (mimeType.startsWith("image/")) {
                        return "imagemedia";
                    } else {
                        return null;
                    }
                }

                @Override
                public ContentValues fromJSON(Context context, Uri localItem, JSONObject item,
                        String lProp) throws JSONException, NetworkProtocolException, IOException {
                    final ContentValues cv = new ContentValues();
                    final String content_type = item.getString(remoteKey);

                    if ("linkedmedia".equals(content_type)) {
                        cv.put(COL_MIME_TYPE, MIMETYPE_HTML);
                    }

                    return cv;
                }
            });

            put(COL_CAPTURE_TIME, new SyncFieldMap("capture_time", SyncFieldMap.DATE,
                    SyncItem.FLAG_OPTIONAL));
        }

        @Override
        public void onPostSyncItem(Context context, Account account, Uri uri, JSONObject item,
                boolean updated) throws SyncException, IOException {
            super.onPostSyncItem(context, account, uri, item, updated);
            if (uri != null) {
                Log.d(TAG, "Starting media sync for " + uri);
                context.startService(new Intent(MediaSync.ACTION_SYNC_RESOURCES, uri));
            }
        }
    }
}
