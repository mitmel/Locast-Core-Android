package edu.mit.mobile.android.locast.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import edu.mit.mobile.android.content.ContentItem;
import edu.mit.mobile.android.content.ContentItemRegisterable;
import edu.mit.mobile.android.content.DBHelper;
import edu.mit.mobile.android.content.GenericDBHelper;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.SQLGenerationException;
import edu.mit.mobile.android.content.m2m.M2MColumns;
import edu.mit.mobile.android.content.m2m.M2MDBHelper;

public class TaggableWrapper extends DBHelper implements ContentItemRegisterable {

    private final GenericDBHelper mWrapped;
    private final M2MDBHelper mTags;

    public TaggableWrapper(GenericDBHelper wrapped, M2MDBHelper tags) {
        mWrapped = wrapped;
        mTags = tags;
    }

    @Override
    public Class<? extends ContentItem> getContentItem(boolean isItem) {
        return mWrapped.getContentItem(isItem);
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

            addTags(provider, itemTags, Tag.toSet(tags));

            db.setTransactionSuccessful();

            return item;

        } catch (final OperationApplicationException e) {
            final SQLException sqle = new SQLException("Error bulk-adding tags");
            sqle.initCause(e);
            throw sqle;
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
     * @throws OperationApplicationException
     */
    public void addTags(ContentProvider provider, Uri itemTags, Set<String> tags)
            throws OperationApplicationException {

        final ArrayList<ContentProviderOperation> cpos = new ArrayList<ContentProviderOperation>(
                tags.size());

        addTags(cpos, itemTags, tags);

        provider.applyBatch(cpos);
    }

    private ArrayList<ContentProviderOperation> addTags(ArrayList<ContentProviderOperation> cpos,
            Uri itemTags, Set<String> tags) {
        for (String tag : tags) {
            tag = Tag.filterTag(tag);
            cpos.add(ContentProviderOperation.newInsert(itemTags).withValue(Tag.COL_NAME, tag)
                    .build());
        }
        return cpos;
    }

    private ArrayList<ContentProviderOperation> deleteTags(
            ArrayList<ContentProviderOperation> cpos, Uri itemTags, Set<String> tags) {
        for (String tag : tags) {
            tag = Tag.filterTag(tag);
            cpos.add(ContentProviderOperation.newDelete(itemTags)
                    .withSelection(Tag.COL_NAME + "=?", new String[] { tag }).build());
        }
        return cpos;
    }

    public void updateTags(ContentProvider provider, Uri item, Set<String> tags)
            throws OperationApplicationException {
        final Uri itemTags = Taggable.getTagPath(item);

        final Set<String> existingTags = getTags(provider, itemTags);

        // tags that need to be removed.
        final Set<String> toDelete = new HashSet<String>(existingTags);
        toDelete.removeAll(tags);

        final Set<String> toAdd = new HashSet<String>(tags);
        toAdd.remove(existingTags);

        if (toAdd.size() == 0 && toDelete.size() == 0) {
            // done!
            return;
        }

        final ArrayList<ContentProviderOperation> cpos = new ArrayList<ContentProviderOperation>();

        addTags(cpos, itemTags, toAdd);

        provider.applyBatch(cpos);

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

    @Override
    public int updateItem(SQLiteDatabase db, ContentProvider provider, Uri uri,
            ContentValues values, String where, String[] whereArgs) {
        return mWrapped.updateItem(db, provider, uri, values, where, whereArgs);
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

        final String mWrappedTable = mWrapped.getTable();

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
                ProviderUtils.addPrefixToProjection(mWrappedTable, projection),

                /* from http://tagging.pui.ch/post/37027745720/tags-database-schemas#toxi */
                ProviderUtils.addExtraWhere(selection, "jt." + M2MColumns.TO_ID + "=t."
                        + BaseColumns._ID, "t." + Tag.COL_NAME + " IN (" + placeholders.toString()
                        + ")"),

                /* selection arguments, with tags added in */
                ProviderUtils.addExtraWhereArgs(selectionArgs, tags),

                /* group by */
                "f." + BaseColumns._ID,

                /* having */
                "COUNT(f." + BaseColumns._ID + ")=" + tags.length, sortOrder);

        // return db.query(mWrappedTable + " JOIN " + joinTable + " ON (" + M2MColumns.TO_ID + "="
        // + mWrappedTable + "." + BaseColumns._ID + ") JOIN " + mTags.getToTable() + " ON ("
        // + joinTable + "." + M2MColumns.TO_ID + "=" + mTags.getToTable() + "."
        // + BaseColumns._ID + ")", projection, selection != null ? "DISTINCT " + selection
        // : "DISTINCT *", selectionArgs, null, null, sortOrder);
        // return mWrapped.queryDir(db, uri, projection, selection, selectionArgs, sortOrder);
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
}
