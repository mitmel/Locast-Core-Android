package edu.mit.mobile.android.locast.data.tags;

import java.util.HashSet;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import edu.mit.mobile.android.content.ContentItem;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.SQLGenerationException;
import edu.mit.mobile.android.content.dbhelper.ContentItemDBHelper;
import edu.mit.mobile.android.content.m2m.M2MColumns;
import edu.mit.mobile.android.content.m2m.M2MDBHelper;

public class TaggableWrapper extends ContentItemDBHelper {

    private final ContentItemDBHelper mWrapped;
    private final M2MDBHelper mTags;

    public TaggableWrapper(ContentItemDBHelper wrapped, M2MDBHelper tags) {
        super(wrapped.getContentItem(false), wrapped.getContentItem(true));
        mWrapped = wrapped;
        mTags = tags;
    }

    @Override
    public Uri insertDir(SQLiteDatabase db, ContentProvider provider, Uri uri, ContentValues values)
            throws SQLException {

        // short-circuit when there are no tags
        if (!values.containsKey(Tag.TAGS_SPECIAL_CV_KEY)) {
            return mWrapped.insertDir(db, provider, uri, values);
        }

        db.beginTransaction();

        try {

            final String tags = ProviderUtils.extractContentValueItem(values,
                    Tag.TAGS_SPECIAL_CV_KEY).toString();

            final Uri item = mWrapped.insertDir(db, provider, uri, values);

            final Uri itemTags = Taggable.getTagPath(item);

            addTags(db, provider, itemTags, Tag.toSet(tags));

            db.setTransactionSuccessful();

            return item;

        } finally {
            db.endTransaction();
        }
    }

    /**
     * Adds tags to a given item. Doesn't verify that they're not there yet, nor does it remove
     * other tags. Tags will be filtered before insertion using {@link Tag#filterTag(String)}.
     *
     * @param provider
     * @param item
     * @param tags
     */
    public void addTags(SQLiteDatabase db, ContentProvider provider, Uri itemTags, Set<String> tags) {

        db.beginTransaction();

        try {
            final ContentValues cv = new ContentValues();

            for (String tag : tags) {
                tag = Tag.filterTag(tag);
                if (tag.length() == 0) {
                    continue;
                }
                cv.put(Tag.COL_NAME, tag);
                mTags.insertDir(db, provider, itemTags, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Removes tags from a given item. Doesn't verify that they're present, nor does it add other
     * tags. Tags will be filtered before insertion using {@link Tag#filterTag(String)}.
     *
     * @param provider
     * @param item
     * @param tags
     */
    public void deleteTags(SQLiteDatabase db, ContentProvider provider, Uri itemTags,
            Set<String> tags) {

        final Uri parent = ProviderUtils.removeLastPathSegment(itemTags);

        final long id = ContentUris.parseId(parent);

        final String tagTable = mTags.getToTable();

        db.beginTransaction();

        try {
            for (String tag : tags) {
                tag = Tag.filterTag(tag);
                if (tag.length() == 0) {
                    continue;
                }

                mTags.removeRelation(db, id, mTags.getJoinTableName() + "." + M2MColumns.TO_ID
                        + " IN (SELECT " + Tag._ID + " FROM " + tagTable + " WHERE " + tagTable
                        + "." + Tag.COL_NAME + "=?" + ")", new String[] { tag });
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void updateTags(SQLiteDatabase db, ContentProvider provider, Uri itemTags,
            Set<String> tags) {
        updateTags(db, provider, itemTags, tags, getTags(db, itemTags));
    }

    private void updateTags(SQLiteDatabase db, ContentProvider provider, Uri itemTags,
            Set<String> tags, Set<String> existingTags) {

        // tags that need to be removed.
        final Set<String> toDelete = new HashSet<String>(existingTags);
        toDelete.removeAll(tags);

        final Set<String> toAdd = new HashSet<String>(tags);
        toAdd.remove(existingTags);

        if (toAdd.size() == 0 && toDelete.size() == 0) {
            // done!
            return;
        }

        db.beginTransaction();

        try {
            addTags(db, provider, itemTags, toAdd);

            deleteTags(db, provider, itemTags, toDelete);

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    public Set<String> getTags(ContentProvider provider, Uri itemTags) {
        final Cursor c = provider.query(itemTags, Tag.DEFAULT_PROJECTION, null, null, null);
        final HashSet<String> tags = new HashSet<String>();

        try {
            final int nameCol = c.getColumnIndex(Tag.COL_NAME);
            while (c.moveToNext()) {
                tags.add(c.getString(nameCol));
            }
        } finally {
            c.close();
        }
        return tags;
    }

    public Set<String> getTags(SQLiteDatabase db, Uri itemTags) {
        final Cursor c = mTags.queryDir(db, itemTags, Tag.DEFAULT_PROJECTION, null, null, null);
        final HashSet<String> tags = new HashSet<String>();

        try {
            final int nameCol = c.getColumnIndex(Tag.COL_NAME);
            while (c.moveToNext()) {
                tags.add(c.getString(nameCol));
            }
        } finally {
            c.close();
        }
        return tags;
    }

    @Override
    public int updateItem(SQLiteDatabase db, ContentProvider provider, Uri uri,
            ContentValues values, String where, String[] whereArgs) {

        // short-circuit when there are no tags
        if (!values.containsKey(Tag.TAGS_SPECIAL_CV_KEY)) {
            return mWrapped.updateItem(db, provider, uri, values, where, whereArgs);
        }

        db.beginTransaction();

        try {

            final String tags = ProviderUtils.extractContentValueItem(values,
                    Tag.TAGS_SPECIAL_CV_KEY).toString();

            final int updateCount = mWrapped
                    .updateItem(db, provider, uri, values, where, whereArgs);

            final Uri itemTags = Taggable.getTagPath(uri);

            updateTags(db, provider, itemTags, Tag.toSet(tags));

            db.setTransactionSuccessful();

            return updateCount;

        } finally {
            db.endTransaction();
        }
    }

    @Override
    public int updateDir(SQLiteDatabase db, ContentProvider provider, Uri uri,
            ContentValues values, String where, String[] whereArgs) {

        return mWrapped.updateDir(db, provider, uri, values, where, whereArgs);
    }

    @Override
    public int deleteItem(SQLiteDatabase db, ContentProvider provider, Uri uri, String where,
            String[] whereArgs) {

        return mWrapped.deleteItem(db, provider, uri, where, whereArgs);
    }

    @Override
    public int deleteDir(SQLiteDatabase db, ContentProvider provider, Uri uri, String where,
            String[] whereArgs) {

        return mWrapped.deleteDir(db, provider, uri, where, whereArgs);
    }

    @Override
    public Cursor queryDir(SQLiteDatabase db, Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        final String tagsParam = uri.getQueryParameter("tags");

        // short circuit if there is no tags query parameter
        if (tagsParam == null) {
            return mWrapped.queryDir(db, uri, projection, selection, selectionArgs, sortOrder);
        }

        final String mWrappedTable = mWrapped.getTargetTable();

        final String joinTable = mTags.getJoinTableName();

        // content://blahblah/foo/?tags=foo,bar
        final String[] tags = tagsParam.split(",");

        final StringBuilder placeholders = new StringBuilder();

        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }

        return db.query(
                /* select the three tables and alias them */
                joinTable + " jt, " + mWrappedTable + " f, " + mTags.getToTable() + " t",

                /* prefix the wrapped table in the projection to avoid conflicts */
                ProviderUtils.addPrefixToProjection("f", projection),

                /* from http://tagging.pui.ch/post/37027745720/tags-database-schemas#toxi */
                ProviderUtils.addExtraWhere(selection, "jt." + M2MColumns.TO_ID + "=t."
                        + BaseColumns._ID, "t." + Tag.COL_NAME + " IN (" + placeholders.toString()
                        + ") AND (" + "f." + BaseColumns._ID + "=jt." + M2MColumns.FROM_ID + ")"),

                /* selection arguments, with tags added in */
                ProviderUtils.addExtraWhereArgs(selectionArgs, tags),

                /* group by */
                "f." + BaseColumns._ID,

                /* having */
                "COUNT(f." + BaseColumns._ID + ")=" + tags.length, sortOrder);
    }

    @Override
    public Cursor queryItem(SQLiteDatabase db, Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        return mWrapped.queryItem(db, uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public String getDirType(String authority, String path) {

        return mWrapped.getDirType(authority, path);
    }

    @Override
    public String getItemType(String authority, String path) {
        return mWrapped.getItemType(authority, path);
    }

    @Override
    public void createTables(SQLiteDatabase db) throws SQLGenerationException {
        mWrapped.createTables(db);
    }

    @Override
    public void upgradeTables(SQLiteDatabase db, int oldVersion, int newVersion)
            throws SQLGenerationException {
        mWrapped.upgradeTables(db, oldVersion, newVersion);
    }

    @Override
    public String getTargetTable() {
        return mWrapped.getTargetTable();
    }

    @Override
    public Class<? extends ContentItem> getContentItem(boolean isItem) {
        return mWrapped.getContentItem(isItem);
    }
}
