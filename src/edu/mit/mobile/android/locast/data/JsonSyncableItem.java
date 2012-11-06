package edu.mit.mobile.android.locast.data;

/*
 * Copyright (C) 2010-2012  MIT Mobile Experience Lab
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
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.text.TextUtils;
import edu.mit.mobile.android.content.ContentItem;
import edu.mit.mobile.android.content.column.BooleanColumn;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.DatetimeColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.net.LocastApplicationCallbacks;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.locast.sync.SyncEngine;
import edu.mit.mobile.android.locast.sync.SyncableSimpleContentProvider;

/**
 * This type of object row can be serialized to/from JSON and synchronized to a server.
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public abstract class JsonSyncableItem extends CursorWrapper implements ContentItem {

    /**
     * The item's permanent, public identifier. This is also a link to the item's data within the
     * API, so that it can be resolved to the get the server's data.
     */
    @DBColumn(type = TextColumn.class, unique = true)
    public static final String COL_PUBLIC_URL = "url";

    /**
     * The time that the item was last modified, in local time. This should be updated each time the
     * item is modified locally.
     */
    @DBColumn(type = DatetimeColumn.class, notnull = true, defaultValue = DatetimeColumn.NOW_IN_MILLISECONDS)
    public static final String COL_MODIFIED_DATE = "modified";

    /**
     * The date that the server last returned as the item's modified date. This is used to determine
     * if the item has been modified at all on the server side. This value is relative to the
     * server's clock and may need to be adjusted for the local clock skew ({@link SyncEngine} does
     * this).
     */
    @DBColumn(type = DatetimeColumn.class)
    public static final String COL_SERVER_MODIFIED_DATE = "server_modified";

    /**
     * The date that the item has been created. This is auto-set to the time of insertion.
     */
    @DBColumn(type = DatetimeColumn.class, notnull = true, defaultValue = DatetimeColumn.NOW_IN_MILLISECONDS)
    public static final String COL_CREATED_DATE = "created";

    /**
     * <p>
     * Set this to true to prevent the {@link SyncEngine} from propagating changes to the server.
     * Items marked draft will be ignored until this column is false.
     * </p>
     *
     * <p>
     * 1 is true; 0 or {@code null} is false
     * </p>
     *
     * @see #isDraft()
     * @see #isDraft(Cursor)
     */
    @DBColumn(type = BooleanColumn.class)
    public static final String COL_DRAFT = "draft";

    /**
     * <p>
     * Set this to true to mark that the item has been deleted locally by the user. Once the
     * {@link SyncEngine} processes the item, it will take care of actually deleting the content
     * both remotely and locally.
     * </p>
     * <p>
     * 1 is true; 0 or {@code null} is false
     * </p>
     *
     * @see #isDeleted()
     * @see #isDeleted(Cursor)
     */
    @DBColumn(type = BooleanColumn.class)
    public static final String COL_DELETED = "deleted";

    /**
     * This should be set to true when an item is locally modified.
     * {@link SyncableSimpleContentProvider} will do this automatically.
     */
    @DBColumn(type = BooleanColumn.class)
    public static final String COL_DIRTY = "dirty";

    /**
     * @return The URI for a given content directory.
     */
    public abstract Uri getContentUri();

    private static String[] PUB_URI_PROJECTION = { _ID, COL_PUBLIC_URL };

    /**
     * A selection that matches items that items that are not draft.
     *
     * @see #COL_DELETED
     * @see #isDraft()
     * @see #isDraft(Cursor)
     */
    public static final String SELECTION_NOT_DRAFT = "(" + COL_DRAFT + " ISNULL OR " + COL_DRAFT
            + "=0)";

    /**
     * A selection that matches items that are marked draft.
     *
     * @see #COL_DRAFT
     * @see #isDraft()
     * @see #isDraft(Cursor)
     */
    public static final String SELECTION_DRAFT = "(" + COL_DRAFT + "=1)";

    /**
     * A selection that matches items that aren't deleted locally.
     *
     * @see #COL_DELETED
     * @see #isDeleted()
     * @see #isDeleted(Cursor)
     */
    public static final String SELECTION_NOT_DELETED = "(" + COL_DELETED + " ISNULL OR "
            + COL_DELETED + "=0)";

    /**
     * A selection that matches items that have been marked deleted locally.
     *
     * @see #COL_DELETED
     * @see #isDeleted()
     * @see #isDeleted(Cursor)
     */
    public static final String SELECTION_DELETED = "(" + COL_DELETED + "=1)";

    /**
     * Instantiate this and wrap a cursor pointing to this type of object in order to access
     * convenient getters.
     *
     * @param c
     *            the cursor to wrap
     */
    public JsonSyncableItem(Cursor c) {
        super(c);
    }

    /**
     * Using {@link #getContentUri()}, constructs a canonical URI representing the item under the
     * cursor.
     *
     * @return the canonical URI for the item under the cursor
     */
    public Uri getCanonicalUri() {
        return ContentUris.withAppendedId(getContentUri(), getLong(getColumnIndexOrThrow(_ID)));
    }

    // some handy getters

    public long getModified() {
        return getLong(getColumnIndexOrThrow(COL_MODIFIED_DATE));
    }

    public long getServerModified() {
        return getLong(getColumnIndexOrThrow(COL_SERVER_MODIFIED_DATE));
    }

    /**
     * @param c
     *            a cursor pointing to the desired item
     * @return true if the item is a draft
     * @see #COL_DRAFT
     */
    public static boolean isDraft(Cursor c) {
        final int col = c.getColumnIndexOrThrow(COL_DRAFT);
        return !c.isNull(col) && c.getInt(col) != 0;
    }

    /**
     * @return true if currently selected item is a draft
     * @see #isDraft(Cursor)
     * @see #COL_DRAFT
     */
    public boolean isDraft() {
        return isDraft(this);
    }

    /**
     * @param c
     *            a cursor pointing to the desired item, containing the column {@link #COL_DELETED}
     * @return true if the item has been deleted locally
     * @see #COL_DELETED
     */
    public static boolean isDeleted(Cursor c) {
        final int col = c.getColumnIndexOrThrow(COL_DELETED);
        return !c.isNull(col) && c.getInt(col) != 0;
    }

    /**
     * @return true if the currently selected item has been deleted locally
     * @see #isDeleted(Cursor)
     * @see #COL_DELETED
     */
    public boolean isDeleted() {
        return isDeleted(this);
    }

    public String getPublicUrl() {
        return getString(getColumnIndexOrThrow(COL_PUBLIC_URL));
    }

    /**
     * Given a public Uri fragment, finds the local item representing it. If there isn't any such
     * item, null is returned.
     *
     * @param context
     * @param dirUri
     *            the base local URI to search.
     * @param pubUri
     *            A public URI fragment that represents the given item. This must match the result
     *            from the API.
     * @return a local URI matching the item or null if none were found.
     */
    public static Uri getItemByPubIUri(Context context, Uri dirUri, String pubUri) {
        Uri uri = null;
        final ContentResolver cr = context.getContentResolver();

        final String[] selectionArgs = { pubUri };
        final Cursor c = cr.query(dirUri, PUB_URI_PROJECTION, COL_PUBLIC_URL + "=?", selectionArgs,
                null);
        if (c.moveToFirst()) {
            uri = ContentUris.withAppendedId(dirUri, c.getLong(c.getColumnIndex(_ID)));
        }

        c.close();
        return uri;
    }

    /**
     * @return A mapping of server↔local DB items.
     */
    public abstract SyncMap getSyncMap();

    /**
     * The base {@link SyncMap} which is required by the {@link SyncEngine}. This synchronizes
     * {@link #COL_PUBLIC_URL}, {@link #COL_SERVER_MODIFIED_DATE}, and {@link #COL_CREATED_DATE}.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static class ItemSyncMap extends SyncMap {
        /**
         *
         */
        private static final long serialVersionUID = 1L;

        public ItemSyncMap() {
            super();

            put(COL_PUBLIC_URL, new SyncFieldMap("uri", SyncFieldMap.STRING, SyncItem.SYNC_FROM));
            put(COL_SERVER_MODIFIED_DATE, new SyncFieldMap("modified", SyncFieldMap.DATE,
                    SyncItem.SYNC_FROM));
            put(COL_CREATED_DATE, new SyncFieldMap("created", SyncFieldMap.DATE, SyncItem.SYNC_FROM
                    | SyncItem.FLAG_OPTIONAL));
        }
    }

    /**
     * The base {@link SyncMap} for an item that can be sync'd with the {@link SyncEngine}. All sync
     * maps should include all the items in this.
     *
     * @see {@link #ItemSyncMap}
     */
    public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

    public static final String LIST_DELIM = "|";
    // the below splits "tag1|tag2" but not "tag1\|tag2"
    public static final String LIST_SPLIT = "(?<!\\\\)\\|";

    /**
     * In a {@link SyncItem}, prefix your key with this string in order to cause the engine to skip
     * attempting to find a JSON key in the JSON object. The item will still be processed, so this
     * can be used for containers that descend into complex JSON objects.
     */
    public static final String PREFIX_IGNORE_KEY = "_";

    /**
     * Gets a list for the current item in the cursor.
     *
     * @param column
     *            column number
     * @param c
     *            cursor pointing to a row
     * @return
     */
    public static List<String> getList(int column, Cursor c) {
        final String t = c.getString(column);
        return getList(t);
    }

    /**
     * Given a string made by {@link JsonSyncableItem#putList(String, ContentValues, Collection)
     * putList()}, return a List containing all the items.
     *
     * @param listString
     * @return a new list representing all the items in the list
     * @see #putList(String, ContentValues, Collection)
     */
    public static List<String> getList(String listString) {
        if (listString != null && listString.length() > 0) {
            final String[] split = listString.split(LIST_SPLIT);
            for (int i = 0; i < split.length; i++) {
                split[i] = split[i].replace("\\" + LIST_DELIM, LIST_DELIM);
            }
            return Arrays.asList(split);
        } else {
            return new Vector<String>();
        }
    }

    /**
     * @param columnName
     *            the name of the key in cv to store the resulting list
     * @param cv
     *            a {@link ContentValues} to store the resulting list in
     * @param list
     * @return the same ContentValues that were passed in
     * @see #toListString(Collection)
     */
    public static ContentValues putList(String columnName, ContentValues cv, Collection<String> list) {
        cv.put(columnName, toListString(list));
        return cv;
    }

    /**
     * Turns a collection of strings into a delimited string
     *
     * @param list
     *            a list of strings
     * @return a string representing the list, delimited by LIST_DELIM with any existing instances
     *         escaped.
     * @see #getList(String)
     */
    public static String toListString(Collection<String> list) {
        final List<String> tempList = new Vector<String>(list.size());

        for (String s : list) {
            // escape all of the delimiters in the individual strings
            s = s.replace(LIST_DELIM, "\\" + LIST_DELIM);
            tempList.add(s);
        }

        return TextUtils.join(LIST_DELIM, tempList);
    }

    /**
     * Marks the given content item(s) as deleted. They will actually be deleted once the deletion
     * is propagated to the server by way of the {@link SyncEngine}.
     *
     * @param cr
     * @param item
     *            item or dir to mark deleted
     * @param deleted
     *            if true, the item will be deleted on next sync
     * @param selection
     *            extra selection or null
     * @param selectionArgs
     *            extra selection arguments or null
     * @return
     */
    public static final int markDeleted(ContentResolver cr, Uri item, boolean deleted,
            String selection, String[] selectionArgs) {
        final ContentValues cv = new ContentValues();
        cv.put(COL_DELETED, deleted);
        return cr.update(item, cv, selection, selectionArgs);
    }

    private static Pattern durationPattern = Pattern.compile("(\\d{1,2}):(\\d{1,2}):(\\d{1,2})");

    /**
     * Given a JSON item and a sync map, create a ContentValues map to be inserted into the DB.
     *
     * @param context
     * @param localItem
     *            will be null if item is new to mobile. If it's been sync'd before, will point to
     *            local entry.
     * @param item
     *            incoming JSON item.
     * @param mySyncMap
     *            A mapping between the JSON object and the content values.
     * @return new ContentValues, ready to be inserted into the database.
     * @throws JSONException
     * @throws IOException
     * @throws NetworkProtocolException
     */
    public final static ContentValues fromJSON(Context context, Uri localItem, JSONObject item,
            SyncMap mySyncMap) throws JSONException, IOException, NetworkProtocolException {
        final ContentValues cv = new ContentValues();

        for (final String propName : mySyncMap.keySet()) {
            final SyncItem map = mySyncMap.get(propName);
            if (!map.isDirection(SyncItem.SYNC_FROM)) {
                continue;
            }
            if (map.isOptional() && (!item.has(map.remoteKey) || item.isNull(map.remoteKey))) {
                continue;
            }
            final ContentValues cv2 = map.fromJSON(context, localItem, item, propName);
            if (cv2 != null) {
                cv.putAll(cv2);
            }

        }
        return cv;
    }

    /**
     * @param context
     * @param localItem
     *            Will contain the URI of the local item being referenced in the cursor
     * @param c
     *            active cursor with the item to sync selected.
     * @param mySyncMap
     * @return a new JSONObject representing the item
     * @throws JSONException
     * @throws NetworkProtocolException
     * @throws IOException
     */
    public final static JSONObject toJSON(Context context, Uri localItem, Cursor c,
            SyncMap mySyncMap) throws JSONException, NetworkProtocolException, IOException {
        final JSONObject jo = new JSONObject();

        for (final String lProp : mySyncMap.keySet()) {
            final SyncItem map = mySyncMap.get(lProp);

            if (!map.isDirection(SyncItem.SYNC_TO)) {
                continue;
            }
            if (c.isAfterLast() || c.isBeforeFirst()) {
                throw new RuntimeException("Cursor passed to toJSON() isn't pointing to any rows");
            }

            final int colIndex = c.getColumnIndex(lProp);
            // if it's a real property that's optional and is null on the local side
            if (!lProp.startsWith(PREFIX_IGNORE_KEY) && map.isOptional()) {
                if (colIndex == -1) {
                    throw new RuntimeException("Programming error: Cursor does not have column '"
                            + lProp + "', though sync map says it should. Sync Map: " + mySyncMap);
                }
                if (c.isNull(colIndex)) {
                    continue;
                }
            }

            final Object jsonObject = map.toJSON(context, localItem, c, lProp);
            if (jsonObject instanceof MultipleJsonObjectKeys) {
                for (final Entry<String, Object> entry : ((MultipleJsonObjectKeys) jsonObject)
                        .entrySet()) {
                    jo.put(entry.getKey(), entry.getValue());
                }

            } else {
                jo.put(map.remoteKey, jsonObject);
            }
        }
        return jo;
    }

    /**
     * Return this from the toJson() method in order to have the mapper insert multiple keys into
     * the parent JSON object. Use the standard put() method to add keys.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static class MultipleJsonObjectKeys extends HashMap<String, Object> {

        /**
         *
         */
        private static final long serialVersionUID = 6639058165035918704L;

    }

    /**
     * An item in a {@link SyncMap} which translates from JSON to a local {@link ContentProvider}
     * and vice versa. By building a {@link SyncMap} and using the subclasses of this class, JSON
     * objects of arbitrary complexity can be translated to local database columns.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static abstract class SyncItem {

        protected final String remoteKey;

        /**
         * Flag indicating that the item will sync in both directions.
         */
        public static final int SYNC_BOTH = 0x3;

        /**
         * Flag indicating that the item will sync to the server.
         */
        public static final int SYNC_TO = 0x1;

        /**
         * Flag indicating that the item will sync from the server.
         */
        public static final int SYNC_FROM = 0x2;

        /**
         * Flag indicating that the item won't sync in either direction.
         */
        public static final int SYNC_NONE = 0x4;

        /**
         * Flag for to indicate that the given item is optional. By default, an item is required and
         * the sync will fail if it's missing.
         */
        public static final int FLAG_OPTIONAL = 0x10;

        /**
         * The default sync direction: {@link #SYNC_BOTH}.
         */
        public static final int DEFAULT_SYNC_DIRECTION = SYNC_BOTH;

        private final int flags;

        /**
         * Creates a SyncItem with the default flag of {@link #DEFAULT_SYNC_DIRECTION}
         *
         * @param remoteKey
         *            the key in the remote JSON
         */
        public SyncItem(String remoteKey) {
            this(remoteKey, DEFAULT_SYNC_DIRECTION);
        }

        /**
         * @param remoteKey
         *            the key in the remote JSON object
         * @param flags
         *            one of {@link #SYNC_BOTH}, {@link #SYNC_FROM}, {@link #SYNC_TO},
         *            {@link #SYNC_NONE}, or {@link #FLAG_OPTIONAL}
         */
        public SyncItem(String remoteKey, int flags) {
            this.remoteKey = remoteKey;
            this.flags = flags;
        }

        public String getRemoteKey() {
            return remoteKey;
        }

        /**
         * Gets the direction that the item will sync in. If unspecified, will return
         * {@link #DEFAULT_SYNC_DIRECTION}.
         *
         * @return {@link #SYNC_BOTH}, {@link #SYNC_NONE}, {@link #SYNC_TO}, or {@link #SYNC_FROM}
         */
        public int getDirection() {
            final int directionFlags = flags & 0x3;

            if ((flags & SYNC_NONE) != 0) {
                return SYNC_NONE;
            }

            if (directionFlags == 0) {
                return DEFAULT_SYNC_DIRECTION;
            }

            return directionFlags;
        }

        /**
         * @param syncDirection
         *            one of {@link #SYNC_BOTH}, {@link #SYNC_FROM}, {@link #SYNC_TO}, or
         *            {@link #SYNC_NONE}
         * @return true if the item will sync in the given direction.
         */
        public boolean isDirection(int syncDirection) {
            return (getDirection() & syncDirection) != 0;
        }

        public boolean isOptional() {
            return (flags & FLAG_OPTIONAL) != 0;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append('"');
            sb.append(remoteKey);
            sb.append('"');

            sb.append("; direction: ");
            switch (getDirection()) {
                case SYNC_BOTH:
                    sb.append("SYNC_BOTH");
                    break;
                case SYNC_FROM:
                    sb.append("SYNC_FROM");
                    break;
                case SYNC_TO:
                    sb.append("SYNC_TO");
                    break;
                case SYNC_NONE:
                    sb.append("SYNC_NONE");
                    break;
            }
            sb.append(isOptional() ? "; optional " : "; not optional ");
            sb.append(String.format("(flags %x)", flags));
            return sb.toString();
        }

        /**
         * Translate a local database entry into JSON.
         *
         * @param context
         *            Android context
         * @param localItem
         *            uri of the local item
         * @param c
         *            cursor pointing to the item
         * @param lProp
         *            the local property where the data should be stored in the CV
         * @return JSONObject, JSONArray or any other type that JSONObject.put() supports.
         * @throws JSONException
         * @throws NetworkProtocolException
         * @throws IOException
         */
        public abstract Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
                throws JSONException, NetworkProtocolException, IOException;

        /**
         * Translate a JSON object into a set of ContentValues that will be inserted/updated into
         * the local item.
         *
         * @param context
         *            Android context
         * @param localItem
         *            uri of the local item or null if it is new
         * @param item
         *            the JSONObject of the item. It's your job to pull out the desired field(s)
         *            here.
         * @param lProp
         *            the local property where the data should be stored in the CV
         * @return a new ContentValues, that will be merged into the new ContentValues object
         * @throws JSONException
         * @throws NetworkProtocolException
         * @throws IOException
         */
        public abstract ContentValues fromJSON(Context context, Uri localItem, JSONObject item,
                String lProp) throws JSONException, NetworkProtocolException, IOException;

        /**
         * This method is called after an item has been successfully synchronized with the server.
         *
         * @param context
         * @param account
         * @param localItem
         *            the local item's URI. This will never be null.
         * @param item
         *            the original JSON from the server
         * @param updated
         *            true if the item has been updated; false if no changes have been made
         * @throws SyncException
         * @throws IOException
         */
        public void onPostSyncItem(Context context, Account account, Uri localItem,
                JSONObject item, boolean updated) throws SyncException, IOException {
        }

    }

    /**
     * A custom sync item. Use this if the automatic field mappers aren't flexible enough to
     * read/write from JSON.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static abstract class SyncCustom extends SyncItem {

        public SyncCustom(String remoteKey) {
            super(remoteKey);
        }

        public SyncCustom(String remoteKey, int flags) {
            super(remoteKey, flags);
        }
    }

    /**
     * A simple field mapper. This maps a JSON object key to a local DB field.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static class SyncFieldMap extends SyncItem {
        private final int type;

        public SyncFieldMap(String remoteKey, int type) {
            this(remoteKey, type, SyncItem.SYNC_BOTH);
        }

        public SyncFieldMap(String remoteKey, int type, int flags) {
            super(remoteKey, flags);
            this.type = type;
        }

        public int getType() {
            return type;
        }

        public final static int STRING = 0, INTEGER = 1, BOOLEAN = 2, LIST_STRING = 3, DATE = 4,
                DOUBLE = 5, LIST_DOUBLE = 6, LIST_INTEGER = 7, LOCATION = 8, DURATION = 9;

        @Override
        public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
                throws JSONException, NetworkProtocolException, IOException {
            final ContentValues cv = new ContentValues();

            switch (getType()) {
                case SyncFieldMap.STRING:
                    cv.put(lProp, item.getString(remoteKey));
                    break;

                case SyncFieldMap.INTEGER:
                    cv.put(lProp, item.getInt(remoteKey));
                    break;

                case SyncFieldMap.DOUBLE:
                    cv.put(lProp, item.getDouble(remoteKey));
                    break;

                case SyncFieldMap.BOOLEAN:
                    cv.put(lProp, item.getBoolean(remoteKey));
                    break;

                case SyncFieldMap.LIST_INTEGER:
                case SyncFieldMap.LIST_STRING:
                case SyncFieldMap.LIST_DOUBLE: {
                    final JSONArray ar = item.getJSONArray(remoteKey);
                    final List<String> l = new Vector<String>(ar.length());
                    for (int i = 0; i < ar.length(); i++) {
                        switch (getType()) {
                            case SyncFieldMap.LIST_STRING:
                                l.add(ar.getString(i));
                                break;

                            case SyncFieldMap.LIST_DOUBLE:
                                l.add(String.valueOf(ar.getDouble(i)));
                                break;

                            case SyncFieldMap.LIST_INTEGER:
                                l.add(String.valueOf(ar.getInt(i)));
                                break;
                        }
                    }
                    cv.put(lProp, TextUtils.join(LIST_DELIM, l));
                }
                    break;

                case SyncFieldMap.DATE:
                    try {
                        cv.put(lProp, NetworkClient.parseDate(item.getString(remoteKey)).getTime());
                    } catch (final ParseException e) {
                        final NetworkProtocolException ne = new NetworkProtocolException(
                                "bad date format");
                        ne.initCause(e);
                        throw ne;
                    }
                    break;

                case SyncFieldMap.DURATION: {
                    final Matcher m = durationPattern.matcher(item.getString(remoteKey));
                    if (!m.matches()) {
                        throw new NetworkProtocolException("bad duration format");
                    }
                    final int durationSeconds = 1200 * Integer.parseInt(m.group(1)) + 60
                            * Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3));
                    cv.put(lProp, durationSeconds);
                }
                    break;
            }
            return cv;
        }

        @Override
        public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
                throws JSONException, NetworkProtocolException, IOException {

            Object retval;
            final int columnIndex = c.getColumnIndex(lProp);
            switch (getType()) {
                case SyncFieldMap.STRING:
                    retval = c.getString(columnIndex);
                    break;

                case SyncFieldMap.INTEGER:
                    retval = c.getInt(columnIndex);
                    break;

                case SyncFieldMap.DOUBLE:
                    retval = c.getDouble(columnIndex);
                    break;

                case SyncFieldMap.BOOLEAN:
                    retval = c.getInt(columnIndex) != 0;
                    break;

                case SyncFieldMap.LIST_STRING:
                case SyncFieldMap.LIST_DOUBLE:
                case SyncFieldMap.LIST_INTEGER: {
                    final JSONArray ar = new JSONArray();
                    final String joined = c.getString(columnIndex);
                    if (joined == null) {
                        throw new NullPointerException("Local value for '" + lProp
                                + "' cannot be null.");
                    }
                    if (joined.length() > 0) {
                        for (final String s : joined.split(TaggableItem.LIST_SPLIT)) {
                            switch (getType()) {
                                case SyncFieldMap.LIST_STRING:
                                    ar.put(s);
                                    break;
                                case SyncFieldMap.LIST_DOUBLE:
                                    ar.put(Double.valueOf(s));
                                    break;
                                case SyncFieldMap.LIST_INTEGER:
                                    ar.put(Integer.valueOf(s));
                                    break;
                            }
                        }
                    }
                    retval = ar;
                }
                    break;

                case SyncFieldMap.DATE:

                    retval = NetworkClient.dateFormat.format(new Date(c.getLong(columnIndex)));
                    break;

                case SyncFieldMap.DURATION: {
                    final int durationSeconds = c.getInt(columnIndex);
                    // hh:mm:ss
                    retval = String.format("%02d:%02d:%02d", durationSeconds / 1200,
                            (durationSeconds / 60) % 60, durationSeconds % 60);
                }
                    break;
                default:
                    throw new IllegalArgumentException(this.toString() + " has an invalid type.");
            }
            return retval;
        }
    }

    /**
     * Given a URI as the value, resolves the item(s) and stores the relations in the database. Each
     * URI represents a list of items that should be related to the parent object.
     *
     * The type of the child is defined by the type of the URI that is returned in the Relationship.
     * Synchronization of this field simply stores the public URI in the given local field as a
     * string and then calls a synchronization on that URI with the context of the destination URI
     * (as defined by the Relationship).
     *
     * For example, one could define a simple comment system to allow commenting on a Cast. In the
     * Cast's sync map,
     *
     * <pre>
     * SYNC_MAP.put(_COMMENTS_URI, new SyncChildRelation(&quot;comments&quot;,
     *         new SyncChildRelation.SimpleRelationship(&quot;castcomments&quot;), SyncFieldMap.SYNC_FROM
     *                 | SyncFieldMap.FLAG_OPTIONAL));
     * </pre>
     *
     * This stores the public URI that's in the JSON object's "comments" value in the Cast's
     * _COMMENTS_URI field. Additionally, it starts a sync on that same public URI with the extra
     * information of {@code content://casts/1/castcomments} so that the results of the public URI
     * sync will be stored in the local URI provided. The type of the child object is entirely
     * determined by the type of the local URI.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static class SyncChildRelation extends SyncItem {
        private final Relationship mRelationship;
        private final boolean mStartChildSync;

        /**
         *
         * @param remoteKey
         *            the key in the JSON
         * @param relationship
         *            how this field relates to the local database
         * @param startChildSync
         *            if true, requests that the child be sync'd when the parent is sync'd. Defaults
         *            to true.
         * @param flags
         *            standard {@link SyncItem} flags
         */
        public SyncChildRelation(String remoteKey, Relationship relationship,
                boolean startChildSync, int flags) {
            super(remoteKey, flags);
            mRelationship = relationship;
            mStartChildSync = startChildSync;
        }

        /**
         * By default, starts a child sync.
         *
         * @param remoteKey
         *            the key in the JSON
         * @param relationship
         *            how this field relates to the local database
         * @param flags
         *            standard {@link SyncItem} flags
         */
        public SyncChildRelation(String remoteKey, Relationship relationship, int flags) {
            this(remoteKey, relationship, true, flags);
        }

        @Override
        public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
                throws JSONException, NetworkProtocolException, IOException {
            // This doesn't need to do anything, as the sync framework will automatically handle
            // JSON creation.
            return null;
        }

        @Override
        public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
                throws JSONException, NetworkProtocolException, IOException {
            final ContentValues cv = new ContentValues();
            final String childPubUri = item.getString(remoteKey);
            // store the URI so we can refresh from it later
            cv.put(lProp, childPubUri);

            return cv;
        }

        @Override
        public void onPostSyncItem(Context context, Account account, Uri uri, JSONObject item,
                boolean updated) throws SyncException, IOException {
            if (!mStartChildSync) {
                return;
            }
            final Uri childDir = mRelationship.getChildDirUri(uri);
            try {
                final String childPubUri = item.getString(remoteKey);
                // TODO optimize so it doesn't need to create a whole new instance
                final NetworkClient nc = ((LocastApplicationCallbacks) context
                        .getApplicationContext()).getNetworkClient(context, account);
                final Uri serverUri = nc.getFullUrl(childPubUri);

                LocastSyncService.startSync(context, serverUri, childDir, false);

            } catch (final JSONException e) {
                final IOException ioe = new IOException("JSON encoding error");
                ioe.initCause(e);
                throw ioe;
            }
        }

        /**
         * @param context
         * @param uri
         *            the URI of the item whose field should be queried
         * @param field
         *            the string name of the field
         * @return
         */
        public static String getPathFromField(Context context, Uri uri, String field) {
            String path = null;
            final String[] generalProjection = { JsonSyncableItem._ID, field };
            final Cursor c = context.getContentResolver().query(uri, generalProjection, null, null,
                    null);
            try {
                if (c.getCount() == 1 && c.moveToFirst()) {
                    final String storedPath = c.getString(c.getColumnIndex(field));
                    path = storedPath;
                } else {
                    throw new IllegalArgumentException("could not get path from field '" + field
                            + "' in uri " + uri);
                }
            } finally {
                c.close();
            }
            return path;
        }

        /**
         * A simple relationship where the local URI path is the relationship name. eg.
         *
         * Given the parent {@code content://itinerary/1} with a relationship of "casts", the child
         * items are all at {@code content://itinerary/1/casts}
         *
         * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
         *
         */
        public static class SimpleRelationship extends Relationship {

            /**
             * Create a new simple relationship.
             *
             * @param relationship
             *            local uri path suffix for the item.
             */
            public SimpleRelationship(String relationship) {
                super(relationship);
            }

            @Override
            public Uri getChildDirUri(Uri parent) {
                return Uri.withAppendedPath(parent, getRelation());
            }
        }

        /**
         * Defines a relationship between one object and another. For most cases, you'll want to use
         * {@link SimpleRelationship}.
         *
         * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
         *
         */
        public static abstract class Relationship {
            private final String mRelation;

            public Relationship(String relationship) {
                mRelation = relationship;
            }

            public abstract Uri getChildDirUri(Uri parent);

            public String getRelation() {
                return mRelation;
            }
        }
    }

    /**
     * An item that recursively goes into a JSON object and can map properties from that. When
     * outputting JSON, will create the object again.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static class SyncMapChain extends SyncItem {
        private final SyncMap chain;

        public SyncMapChain(String remoteKey, SyncMap chain) {
            super(remoteKey);
            this.chain = chain;
        }

        public SyncMapChain(String remoteKey, SyncMap chain, int direction) {
            super(remoteKey, direction);
            this.chain = chain;
        }

        public SyncMap getChain() {
            return chain;
        }

        @Override
        public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
                throws JSONException, NetworkProtocolException, IOException {

            return JsonSyncableItem.fromJSON(context, localItem, item.getJSONObject(remoteKey),
                    getChain());
        }

        @Override
        public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
                throws JSONException, NetworkProtocolException, IOException {

            return JsonSyncableItem.toJSON(context, localItem, c, getChain());
        }

        @Override
        public void onPostSyncItem(Context context, Account account, Uri uri, JSONObject item,
                boolean updated) throws SyncException, IOException {
            super.onPostSyncItem(context, account, uri, item, updated);
            chain.onPostSyncItem(context, account, uri, item, updated);
        }
    }

    /**
     * One-directional sync of a child item.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static class SyncChildField extends SyncMapChain {
        public SyncChildField(String localKey, String remoteKey, String remoteField, int fieldType) {
            this(localKey, remoteKey, remoteField, fieldType, 0);

        }

        public SyncChildField(String localKey, String remoteKey, String remoteField, int fieldType,
                int fieldFlags) {
            super(remoteKey, new SyncMap(), SyncMapChain.SYNC_FROM);

            getChain().put(localKey, new SyncFieldMap(remoteField, fieldType, fieldFlags));
        }
    }

    /**
     * Store multiple remote fields into one local field.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static abstract class SyncMapJoiner extends SyncItem {
        private final SyncItem[] children;

        /**
         * @param children
         *            The SyncItems that you wish to join. These should probably be of the same
         *            type, but don't need to be. You'll have to figure out how to join them by
         *            defining your joinContentValues().
         */
        public SyncMapJoiner(SyncItem... children) {
            super("_syncMapJoiner", SyncItem.SYNC_BOTH);
            this.children = children;

        }

        public SyncItem getChild(int index) {
            return children[index];
        }

        public int getChildCount() {
            return children.length;
        }

        @Override
        public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
                throws JSONException, NetworkProtocolException, IOException {
            final ContentValues[] cvArray = new ContentValues[children.length];
            for (int i = 0; i < children.length; i++) {
                if (children[i].isDirection(SYNC_FROM)) {
                    cvArray[i] = children[i].fromJSON(context, localItem, item, lProp);
                }
            }
            return joinContentValues(cvArray);
        }

        @Override
        public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
                throws JSONException, NetworkProtocolException, IOException {
            final Object[] jsonObjects = new Object[children.length];
            for (int i = 0; i < children.length; i++) {
                if (children[i].isDirection(SYNC_TO)) {
                    jsonObjects[i] = children[i].toJSON(context, localItem, c, lProp);
                }
            }
            return joinJson(jsonObjects);
        }

        @Override
        public void onPostSyncItem(Context context, Account account, Uri uri, JSONObject item,
                boolean updated) throws SyncException, IOException {
            super.onPostSyncItem(context, account, uri, item, updated);
            for (final SyncItem child : children) {
                child.onPostSyncItem(context, account, uri, item, updated);
            }
        }

        /**
         * Implement this to tell the joiner how to join the result of the children's fromJson()
         * into the same ContentValues object.
         *
         * @param cv
         *            all results from fromJson()
         * @return a joined version of the ContentValues
         */
        public abstract ContentValues joinContentValues(ContentValues[] cv);

        public Object joinJson(Object[] jsonObjects) {
            final MultipleJsonObjectKeys multKeys = new MultipleJsonObjectKeys();
            for (int i = 0; i < getChildCount(); i++) {
                multKeys.put(getChild(i).remoteKey, jsonObjects[i]);
            }
            return multKeys;
        }
    }

    /**
     * Used for outputting a literal into a JSON object. If the format requires some strange
     * literal, like "type": "point" this can add it.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static class SyncLiteral extends SyncItem {
        private final Object literal;

        public SyncLiteral(String remoteKey, Object literal) {
            super(remoteKey);
            this.literal = literal;
        }

        public Object getLiteral() {
            return literal;
        }

        @Override
        public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
                throws JSONException, NetworkProtocolException, IOException {
            return null;
        }

        @Override
        public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
                throws JSONException, NetworkProtocolException, IOException {
            return literal;
        }

    }
}
