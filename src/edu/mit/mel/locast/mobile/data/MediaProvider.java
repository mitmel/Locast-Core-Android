package edu.mit.mel.locast.mobile.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import junit.framework.Assert;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;
import edu.mit.mel.locast.mobile.ListUtils;

public class MediaProvider extends ContentProvider {
	private final static String TAG = MediaProvider.class.getSimpleName();
	public final static String AUTHORITY = "edu.mit.mobile.android.locast.provider";

	public final static String
		TYPE_CAST_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.locast.casts",
		TYPE_CAST_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.locast.casts",

		TYPE_CAST_MEDIA_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.locast.castmedia",
		TYPE_CAST_MEDIA_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.locast.castmedia",

		TYPE_PROJECT_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.locast.projects",
		TYPE_PROJECT_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.locast.projects",

		TYPE_PROJECT_CAST_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.locast.projects.casts",
		TYPE_PROJECT_CAST_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.locast.projects.casts",

		TYPE_COMMENT_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.locast.comments",
		TYPE_COMMENT_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.locast.comments",

		// XXX needed? TYPE_TAG_ITEM     = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.locast.tags",
		TYPE_TAG_DIR      = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.locast.tags",

		TYPE_SHOTLIST_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.locast.shotlist",
		TYPE_SHOTLIST_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.locast.shotlist"
		;

	private static final String
		CAST_TABLE_NAME       = "casts",
		CAST_MEDIA_TABLE_NAME = "castmedia",
		PROJECT_TABLE_NAME    = "projects",
		COMMENT_TABLE_NAME    = "comments",
		TAG_TABLE_NAME        = "tags",
		SHOTLIST_TABLE_NAME   = "shotlist";

	private static UriMatcher uriMatcher;

	private static final int
		MATCHER_CAST_DIR             = 1,
		MATCHER_CAST_ITEM            = 2,
		MATCHER_PROJECT_DIR          = 3,
		MATCHER_PROJECT_ITEM         = 4,
		MATCHER_COMMENT_DIR          = 5,
		MATCHER_COMMENT_ITEM         = 6,
		MATCHER_PROJECT_CAST_DIR     = 7,
		MATCHER_PROJECT_CAST_ITEM    = 8,
		MATCHER_CHILD_COMMENT_DIR    = 9,
		MATCHER_CHILD_COMMENT_ITEM   = 10,
		MATCHER_PROJECT_BY_TAGS      = 11,
		MATCHER_CAST_BY_TAGS         = 12,
		MATCHER_TAG_DIR              = 13,
		MATCHER_ITEM_TAGS    		 = 14,
		MATCHER_CAST_MEDIA_DIR       = 15,
		MATCHER_CAST_MEDIA_ITEM      = 16,
		MATCHER_PROJECT_SHOTLIST_DIR = 17,
		MATCHER_PROJECT_SHOTLIST_ITEM= 18,
		MATCHER_SHOTLIST_DIR         = 19,
		MATCHER_PROJECT_CAST_CASTMEDIA_DIR = 20,
		MATCHER_PROJECT_CAST_CASTMEDIA_ITEM= 21, // wow...
		MATCHER_CAST_CASTMEDIA_DIR 	 = 22;

	private static class DatabaseHelper extends SQLiteOpenHelper {
		private static final String DB_NAME = "content.db";
		private static final int DB_VER = 26;

		public DatabaseHelper(Context context) {
			super(context, DB_NAME, null, DB_VER);
		}

		private static final String JSON_SYNCABLE_ITEM_FIELDS =
			JsonSyncableItem._ID 			 + " INTEGER PRIMARY KEY,"
			+ JsonSyncableItem._PUBLIC_URI 	 + " TEXT UNIQUE,"
			+ JsonSyncableItem._PUBLIC_ID 	 + " INTEGER UNIQUE,"
			+ JsonSyncableItem._MODIFIED_DATE+ " INTEGER,"
			+ JsonSyncableItem._CREATED_DATE + " INTEGER,";

		private static final String JSON_COMMENTABLE_FIELDS =
			Commentable.Columns._COMMENT_DIR_URI  + " TEXT,";

		@Override
		public void onCreate(SQLiteDatabase db) {

			db.execSQL("CREATE TABLE "  + CAST_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ JSON_COMMENTABLE_FIELDS
					+ Cast._TITLE 		+ " TEXT,"
					+ Cast._AUTHOR 		+ " TEXT,"
					+ Cast._DESCRIPTION + " TEXT,"
					+ Cast._MEDIA_PUBLIC_URI 	+ " TEXT,"
					+ Cast._MEDIA_LOCAL_URI 	+ " TEXT,"
					+ Cast._CONTENT_TYPE + " TEXT,"

					+ Cast._PROJECT_ID  + " INTEGER,"
					+ Cast._PROJECT_URI + " TEXT,"
					+ Cast._CASTMEDIA_DIR_URI + " TEXT,"
					+ Cast._PRIVACY 	+ " TEXT,"

					+ Cast._LATITUDE 	+ " REAL,"
					+ Cast._LONGITUDE 	+ " REAL,"

					+ Cast._FAVORITED   + " BOOLEAN,"
					+ Cast._DRAFT       + " BOOLEAN,"

					+ Cast._THUMBNAIL_URI+ " TEXT"
					+ ");"
					);
			db.execSQL("CREATE TABLE " + PROJECT_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ JSON_COMMENTABLE_FIELDS
					+ Project._TITLE 		+ " TEXT,"
					+ Project._AUTHOR		+ " TEXT,"
					+ Project._DESCRIPTION 	+ " TEXT,"
					+ Project._PRIVACY 		+ " TEXT,"
					+ Project._CASTS_URI	+ " TEXT,"

					+ Project._LATITUDE 	+ " REAL,"
					+ Project._LONGITUDE 	+ " REAL,"

					+ Project._FAVORITED    + " BOOLEAN,"
					+ Project._DRAFT        + " BOOLEAN"

					+ ");"
			);
			db.execSQL("CREATE TABLE " + COMMENT_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ Comment._AUTHOR		+ " TEXT,"
					+ Comment._AUTHOR_ICON	+ " TEXT,"
					+ Comment._PARENT_ID     + " INTEGER,"
					+ Comment._PARENT_CLASS  + " TEXT,"
					+ Comment._COMMENT_NUMBER+ " TEXT,"
					+ Comment._DESCRIPTION 	+ " TEXT"
					+ ");"
			);
			db.execSQL("CREATE TABLE " + TAG_TABLE_NAME + " ("
					+ Tag._ID 			+ " INTEGER PRIMARY KEY,"
					+ Tag._REF_ID 	    + " INTEGER,"
					+ Tag._REF_CLASS  	+ " TEXT,"
					+ Tag._NAME    	    + " TEXT,"
					// easiest way to prevent tag duplicates
					+ "CONSTRAINT tag_unique UNIQUE ("+ Tag._REF_ID+ ","+Tag._REF_CLASS+","+Tag._NAME+") ON CONFLICT IGNORE"
					+ ");"
			);
			db.execSQL("CREATE TABLE "+ CAST_MEDIA_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ CastMedia._PARENT_ID     + " INTEGER,"
					+ CastMedia._LIST_IDX      + " INTEGER,"
					+ CastMedia._MEDIA_URL     + " TEXT,"
					+ CastMedia._LOCAL_URI     + " TEXT,"
					+ CastMedia._MIME_TYPE     + " TEXT,"
					+ CastMedia._PREVIEW_URL   + " TEXT,"
					+ CastMedia._SCREENSHOT    + " TEXT,"
					+ CastMedia._DURATION      + " INTEGER,"
					// this ensures that each cast has only one cast media in each list position.
					// List_idx is the index in the cast media array.
					+ "CONSTRAINT cast_media_unique UNIQUE ("+ CastMedia._LIST_IDX+ ","+CastMedia._PARENT_ID+") ON CONFLICT REPLACE"
			+ ")"
			);

			db.execSQL("CREATE TABLE "+ SHOTLIST_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ ShotList._PARENT_ID     + " INTEGER,"
					+ ShotList._LIST_IDX      + " INTEGER,"
					+ ShotList._DIRECTION     + " TEXT,"
					+ ShotList._DURATION      + " INTEGER,"
					// this ensures that each cast has only one cast media in each list position.
					// List_idx is the index in the cast media array.
					+ "CONSTRAINT shot_list_unique UNIQUE ("+ ShotList._LIST_IDX+ ","+ShotList._PARENT_ID+") ON CONFLICT REPLACE"
			+ ")"
			);

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + CAST_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + PROJECT_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + COMMENT_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + TAG_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + CAST_MEDIA_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + SHOTLIST_TABLE_NAME);
			onCreate(db);
		}

	}

	private DatabaseHelper dbHelper;

	@Override
	public boolean onCreate() {
		dbHelper = new DatabaseHelper(getContext());
		return true;
	}


	/**
	 * @param uri
	 * @return true if the matching URI can sync.
	 */
	public static boolean canSync(Uri uri){
		switch (uriMatcher.match(uri)){
		case MATCHER_ITEM_TAGS:
		case MATCHER_TAG_DIR:

		case MATCHER_CAST_MEDIA_DIR:
		case MATCHER_CAST_MEDIA_ITEM:
		case MATCHER_CAST_CASTMEDIA_DIR:
		case MATCHER_PROJECT_CAST_CASTMEDIA_DIR:
		case MATCHER_PROJECT_CAST_CASTMEDIA_ITEM:

		case MATCHER_COMMENT_ITEM:
		case MATCHER_CHILD_COMMENT_ITEM:

		case MATCHER_PROJECT_SHOTLIST_DIR:
		case MATCHER_PROJECT_SHOTLIST_ITEM:

			return false;

		case MATCHER_CHILD_COMMENT_DIR:

		case MATCHER_COMMENT_DIR:


		case MATCHER_CAST_BY_TAGS:
		case MATCHER_CAST_DIR:
		case MATCHER_CAST_ITEM:


		case MATCHER_PROJECT_BY_TAGS:
		case MATCHER_PROJECT_CAST_DIR:
		case MATCHER_PROJECT_CAST_ITEM:
		case MATCHER_PROJECT_DIR:
		case MATCHER_PROJECT_ITEM:
			return true;

		default:
			throw new IllegalArgumentException("Cannot get syncability for URI "+uri);
		}
	}

	@Override
	public String getType(Uri uri) {
		switch (uriMatcher.match(uri)){
		case MATCHER_CAST_DIR:
			return TYPE_CAST_DIR;

		case MATCHER_CAST_ITEM:
			return TYPE_CAST_ITEM;

		case MATCHER_PROJECT_CAST_DIR:
			return TYPE_PROJECT_CAST_DIR;

		case MATCHER_PROJECT_CAST_ITEM:
			return TYPE_PROJECT_CAST_ITEM;

		case MATCHER_CAST_MEDIA_DIR:
		case MATCHER_CAST_CASTMEDIA_DIR:
		case MATCHER_PROJECT_CAST_CASTMEDIA_DIR:
			return TYPE_CAST_MEDIA_DIR;

		case MATCHER_CAST_MEDIA_ITEM:
			return TYPE_CAST_MEDIA_ITEM;

		case MATCHER_PROJECT_DIR:
			return TYPE_PROJECT_DIR;

		case MATCHER_PROJECT_ITEM:
			return TYPE_PROJECT_ITEM;

		case MATCHER_COMMENT_DIR:
		case MATCHER_CHILD_COMMENT_DIR:
			return TYPE_COMMENT_DIR;

		case MATCHER_COMMENT_ITEM:
		case MATCHER_CHILD_COMMENT_ITEM:
			return TYPE_COMMENT_ITEM;

	// tags
		case MATCHER_TAG_DIR:
		case MATCHER_ITEM_TAGS:
			return TYPE_TAG_DIR;

		case MATCHER_CAST_BY_TAGS:
			return TYPE_CAST_DIR;

		case MATCHER_PROJECT_BY_TAGS:
			return TYPE_PROJECT_DIR;


		case MATCHER_SHOTLIST_DIR:
		case MATCHER_PROJECT_SHOTLIST_DIR:
			return TYPE_SHOTLIST_DIR;

		case MATCHER_PROJECT_SHOTLIST_ITEM:
			return TYPE_SHOTLIST_ITEM;

		default:
			throw new IllegalArgumentException("Cannot get type for URI "+uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		final SQLiteDatabase db = dbHelper.getWritableDatabase();

		long rowid;
		final boolean syncable = canSync(uri);
		if (syncable && !values.containsKey(Project._MODIFIED_DATE)){
			values.put(Project._MODIFIED_DATE, new Date().getTime());
		}
		values.remove(JsonSyncableItem._ID);

		// XXX remove this when the API updates
		translateIdToUri(getContext(), values, syncable, uri);

		Uri newItem = null;
		boolean isDraft = false;

		switch (uriMatcher.match(uri)){
		case MATCHER_CAST_DIR:{
			Assert.assertNotNull(values);
			final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);

			rowid = db.insert(CAST_TABLE_NAME, null, values);
			if (rowid > 0){
				//Log.d(TAG, "just inserted: " + values);

				getContext().getContentResolver().notifyChange(uri, null);

				newItem = ContentUris.withAppendedId(Cast.CONTENT_URI, rowid);

				update(Uri.withAppendedPath(newItem, Tag.PATH), cvTags, null, null);

				if (values.containsKey(Cast._DRAFT)){
					isDraft = values.getAsBoolean(Cast._DRAFT);
				}else{
					isDraft = false;
				}
			}
			break;
		}
		case MATCHER_PROJECT_DIR:{
			Assert.assertNotNull(values);
			final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);
			rowid = db.insert(PROJECT_TABLE_NAME, null, values);
			if (rowid > 0){
				getContext().getContentResolver().notifyChange(uri, null);
				newItem = ContentUris.withAppendedId(Project.CONTENT_URI, rowid);
				update(Uri.withAppendedPath(newItem, Tag.PATH), cvTags, null, null);
				if (values.containsKey(Project._DRAFT)){
					isDraft = values.getAsBoolean(Project._DRAFT);
				}else{
					isDraft = false;
				}

			}
			break;
		}
		case MATCHER_COMMENT_DIR:
			Assert.assertNotNull(values);
			rowid = db.insert(COMMENT_TABLE_NAME, null, values);
			if (rowid > 0){
				getContext().getContentResolver().notifyChange(uri, null);
				newItem = ContentUris.withAppendedId(Comment.CONTENT_URI, rowid);
			}
			break;

		case MATCHER_CHILD_COMMENT_DIR:{
			final List<String> pathSegs = uri.getPathSegments();
			values.put(Comment._PARENT_CLASS, pathSegs.get(pathSegs.size() - 3));
			values.put(Comment._PARENT_ID, pathSegs.get(pathSegs.size() - 2));
			rowid = db.insert(COMMENT_TABLE_NAME, null, values);
			if (rowid > 0){
				getContext().getContentResolver().notifyChange(uri, null);
				newItem = ContentUris.withAppendedId(uri, rowid);
			}
		}break;

		case MATCHER_ITEM_TAGS:{
			final List<String> pathSegments = uri.getPathSegments();
			values.put(Tag._REF_CLASS, pathSegments.get(pathSegments.size() - 3));
			values.put(Tag._REF_ID, pathSegments.get(pathSegments.size() - 2));

			rowid = 0;
			for (final String tag : TaggableItem.getList(values.getAsString(Tag.PATH))){
				final ContentValues cv2 = new ContentValues(values);
				cv2.remove(Tag.PATH);
				cv2.put(Tag._NAME, tag);
				rowid = db.insert(TAG_TABLE_NAME, null, cv2);
			}
			newItem = ContentUris.withAppendedId(uri, rowid);

			break;
		}

		case MATCHER_CAST_CASTMEDIA_DIR:{
			final String castId = uri.getPathSegments().get(1);
			values.put(CastMedia._PARENT_ID, castId);
			rowid = db.insert(CAST_MEDIA_TABLE_NAME, null, values);

			newItem = ContentUris.withAppendedId(uri, rowid);

		} break;

		case MATCHER_PROJECT_SHOTLIST_DIR:{
			final String projectId = uri.getPathSegments().get(1);
			values.put(ShotList._PARENT_ID, projectId);
			rowid = db.insert(SHOTLIST_TABLE_NAME, null, values);

			newItem = ContentUris.withAppendedId(uri, rowid);

		} break;

		case MATCHER_PROJECT_CAST_DIR:{
			final String projectId = uri.getPathSegments().get(1);
			values.put(Cast._PROJECT_ID, projectId);

			// keep the actual isDraft flag, but mark it a draft when recursing in
			// order to have the sync only happen on the outermost URI
			if (values.containsKey(Project._DRAFT)){
				isDraft = values.getAsBoolean(Project._DRAFT);
			}
			values.put(Cast._DRAFT, true);

			newItem = insert(Cast.CONTENT_URI, values);

		} break;

		case MATCHER_PROJECT_CAST_CASTMEDIA_DIR:{
			final String castId = uri.getPathSegments().get(3);
			values.put(CastMedia._PARENT_ID, castId);

			// TODO figure out how to have it not sync twice here
			newItem = insert(CastMedia.CONTENT_URI, values);
		} break;

		default:
			throw new IllegalArgumentException("Unknown URI: "+uri);
		}
		if (newItem == null){
			throw new SQLException("Failed to insert row into "+uri);
		}

		if (syncable && !isDraft){
			getContext().startService(new Intent(Intent.ACTION_SYNC, uri));
		}
		return newItem;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		final SQLiteDatabase db = dbHelper.getReadableDatabase();

		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		final long id;
		Cursor c;

		String taggableItemTable = null;

		switch (uriMatcher.match(uri)){
		case MATCHER_CAST_DIR:
			qb.setTables(CAST_TABLE_NAME);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;

		case MATCHER_CAST_ITEM:
			qb.setTables(CAST_TABLE_NAME);
			id = ContentUris.parseId(uri);
			qb.appendWhere(Cast._ID + "="+id);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;

		case MATCHER_PROJECT_DIR:
			qb.setTables(PROJECT_TABLE_NAME);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;

		case MATCHER_PROJECT_ITEM:
			qb.setTables(PROJECT_TABLE_NAME);
			id = ContentUris.parseId(uri);
			qb.appendWhere(Project._ID + "="+id);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;

		case MATCHER_COMMENT_DIR:{
			qb.setTables(COMMENT_TABLE_NAME);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}

		case MATCHER_COMMENT_ITEM:{
			qb.setTables(COMMENT_TABLE_NAME);
			id = ContentUris.parseId(uri);
			qb.appendWhere(Comment._ID + "="+id);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}

		case MATCHER_CHILD_COMMENT_DIR:{
			final List<String> pathSegs = uri.getPathSegments();
			c = db.query(COMMENT_TABLE_NAME, projection,
					addExtraWhere(selection, 			Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					addExtraWhereArgs(selectionArgs, 	pathSegs.get(pathSegs.size() - 2), 	pathSegs.get(pathSegs.size() - 3)), null, null, sortOrder);
			break;
		}

		case MATCHER_CHILD_COMMENT_ITEM:{
			final List<String> pathSegs = uri.getPathSegments();

			id = ContentUris.parseId(uri);

			c = db.query(COMMENT_TABLE_NAME, projection,
					addExtraWhere(selection, 			Comment._ID+"=?", 	Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					addExtraWhereArgs(selectionArgs, 	String.valueOf(id), pathSegs.get(pathSegs.size() - 3), 	pathSegs.get(pathSegs.size() - 4))
					, null, null, sortOrder);
			break;
		}

		case MATCHER_PROJECT_CAST_DIR:{
			final String projectId = uri.getPathSegments().get(1);
			qb.setTables(CAST_TABLE_NAME);
			qb.appendWhere(Cast._PROJECT_ID + "="+projectId);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
		} break;

		case MATCHER_PROJECT_CAST_ITEM:{
			qb.setTables(CAST_TABLE_NAME);
			id = ContentUris.parseId(uri);
			qb.appendWhere(Cast._ID + "="+id);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
		}break;

		case MATCHER_ITEM_TAGS:{
			qb.setTables(TAG_TABLE_NAME);
			final List<String> pathSegments = uri.getPathSegments();
			qb.appendWhere(Tag._REF_CLASS+"=\""+pathSegments.get(pathSegments.size() - 3)+"\"");
			qb.appendWhere(" AND " + Tag._REF_ID+"="+pathSegments.get(pathSegments.size() - 2));
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}

		case MATCHER_TAG_DIR:{
			qb.setTables(TAG_TABLE_NAME);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}

		case MATCHER_CAST_BY_TAGS:
			taggableItemTable = CAST_TABLE_NAME;

		case MATCHER_PROJECT_BY_TAGS:{
			if (taggableItemTable == null){
				taggableItemTable = PROJECT_TABLE_NAME;
			}
			qb.setTables(taggableItemTable + " AS c, "+TAG_TABLE_NAME +" AS t");
			final Set<String> tags = Tag.toSet(uri.getLastPathSegment());
			final List<String> tagFilterList = new ArrayList<String>(tags.size());
			// as sets can't be gone through by index, this should help translate to a list
			qb.appendWhere("t."+Tag._REF_ID+"=c."+TaggableItem._ID);
			int i = 0;
			for (final String tag : tags){
				tagFilterList.add(i, "'"+tag+"'");
				i++;
			}
			qb.appendWhere(" AND (t."+Tag._NAME+" IN ("+ListUtils.join(tagFilterList, ",")+"))");

			// limit to only items of the given object class
			qb.appendWhere(" AND t."+Tag._REF_CLASS + "=\""+taggableItemTable+"\"");

			c = qb.query(db, projection, selection, selectionArgs,
					"c."+TaggableItem._ID,
					"COUNT ("+"c."+TaggableItem._ID+")="+tags.size(),
					sortOrder);
			break;
		}

		case MATCHER_PROJECT_CAST_CASTMEDIA_DIR:{
			final String castId = uri.getPathSegments().get(3);

			qb.setTables(CAST_MEDIA_TABLE_NAME);
			qb.appendWhere(CastMedia._PARENT_ID + "="+castId);

			// the default sort is necessary to ensure items are returned in list index order.
			c = qb.query(db, projection, selection, selectionArgs, null, null, OrderedList.Columns.DEFAULT_SORT);

		} break;

		case MATCHER_CAST_CASTMEDIA_DIR:{
			final String castId = uri.getPathSegments().get(1);

			qb.setTables(CAST_MEDIA_TABLE_NAME);
			qb.appendWhere(CastMedia._PARENT_ID + "="+castId);

			// the default sort is necessary to ensure items are returned in list index order.
			c = qb.query(db, projection, selection, selectionArgs, null, null, OrderedList.Columns.DEFAULT_SORT);

		} break;

		case MATCHER_PROJECT_SHOTLIST_DIR:{
			final String projectId = uri.getPathSegments().get(1);

			qb.setTables(SHOTLIST_TABLE_NAME);
			qb.appendWhere(OrderedList.Columns._PARENT_ID + "="+projectId);

			// the default sort is necessary to ensure items are returned in list index order.
			c = qb.query(db, projection, selection, selectionArgs, null, null, OrderedList.Columns.DEFAULT_SORT);

		} break;

			default:
				throw new IllegalArgumentException("unknown URI "+uri);
		}


		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String where,
			String[] whereArgs) {
		final SQLiteDatabase db = dbHelper.getWritableDatabase();
		int count;
		final long id;
		boolean needSync = false;
		final boolean canSync = canSync(uri);
		if (canSync && !values.containsKey(JsonSyncableItem._MODIFIED_DATE)){
			values.put(JsonSyncableItem._MODIFIED_DATE, new Date().getTime());
			needSync = true;
		}

		// XXX remove this when the API updates
		translateIdToUri(getContext(), values, canSync, uri);

		switch (uriMatcher.match(uri)){
		case MATCHER_CAST_DIR:
			count = db.update(CAST_TABLE_NAME, values, where, whereArgs);
			break;
		case MATCHER_PROJECT_CAST_ITEM:
		case MATCHER_CAST_ITEM:{
			id = ContentUris.parseId(uri);
			//final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);
			if ( values.size() == 2 && values.containsKey(Favoritable.Columns._FAVORITED)){
				values.put(JsonSyncableItem._MODIFIED_DATE, 0);
			}
			count = db.update(CAST_TABLE_NAME, values,
					Cast._ID+"="+id+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
			//update(Uri.withAppendedPath(uri, Tag.PATH), cvTags, null, null);

			break;
		}

		case MATCHER_CAST_MEDIA_ITEM:{
			final String castId = uri.getPathSegments().get(1);
			final String castMediaId = uri.getPathSegments().get(3);
			count = db.update(CAST_MEDIA_TABLE_NAME, values,
					CastMedia._PARENT_ID+"="+castId
						+ " AND " + CastMedia._LIST_IDX+"=" + castMediaId
						+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
		} break;

		case MATCHER_PROJECT_CAST_CASTMEDIA_ITEM:{
			final String castId = uri.getPathSegments().get(3);
			final String castMediaId = uri.getPathSegments().get(5);
			count = db.update(CAST_MEDIA_TABLE_NAME, values,
					CastMedia._PARENT_ID+"="+castId
						+ " AND " + CastMedia._LIST_IDX+"=" + castMediaId
						+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
		} break;

		case MATCHER_PROJECT_DIR:
			count = db.update(PROJECT_TABLE_NAME, values, where, whereArgs);
			break;
		case MATCHER_PROJECT_ITEM:{
			id = ContentUris.parseId(uri);
			//final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);
			if ( values.size() == 2 && values.containsKey(Favoritable.Columns._FAVORITED)){
				values.put(JsonSyncableItem._MODIFIED_DATE, 0);
			}
			count = db.update(PROJECT_TABLE_NAME, values,
					Project._ID+"="+id+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);


			//update(Uri.withAppendedPath(uri, Tag.PATH), cvTags, null, null);
			break;
		}

		case MATCHER_PROJECT_SHOTLIST_ITEM: {
			final String projectId = uri.getPathSegments().get(1);
			final long shotIdx = ContentUris.parseId(uri);

			count = db.update(SHOTLIST_TABLE_NAME, values,
					ShotList._PARENT_ID+"="+projectId
					+ " AND " + ShotList._LIST_IDX + "=" + shotIdx
					+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
		} break;

		case MATCHER_COMMENT_DIR:
			count = db.update(COMMENT_TABLE_NAME, values, where, whereArgs);
			break;
		case MATCHER_COMMENT_ITEM:
			id = ContentUris.parseId(uri);

			count = db.update(COMMENT_TABLE_NAME, values,
					Comment._ID+"="+id+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
			break;

		case MATCHER_CHILD_COMMENT_DIR:{
			id = ContentUris.parseId(uri);
			final List<String>pathSegs = uri.getPathSegments();

			count = db.update(COMMENT_TABLE_NAME, values,
					addExtraWhere(where, 			Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					addExtraWhereArgs(whereArgs, 	pathSegs.get(pathSegs.size() - 2), 	pathSegs.get(pathSegs.size() - 3))
					);
			break;
		}

		case MATCHER_CHILD_COMMENT_ITEM:{
			id = ContentUris.parseId(uri);
			final List<String>pathSegs = uri.getPathSegments();

			count = db.update(COMMENT_TABLE_NAME, values,
					addExtraWhere(where, 			Comment._ID+"=?", 	Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					addExtraWhereArgs(whereArgs, 	String.valueOf(id), pathSegs.get(pathSegs.size() - 3), 	pathSegs.get(pathSegs.size() - 4))
					);
			break;
		}

		case MATCHER_ITEM_TAGS:{
			final String[] tag_projection = {Tag._REF_ID, Tag._REF_CLASS, Tag._NAME};

			final Cursor tagC = query(uri, tag_projection, null, null, null);
			final int tagIdx = tagC.getColumnIndex(Tag._NAME);
			final Set<String> existingTags = new HashSet<String>(tagC.getCount());
			for (tagC.moveToFirst(); !tagC.isAfterLast(); tagC.moveToNext()){
				existingTags.add(tagC.getString(tagIdx));
			}
			tagC.close();

			final Set<String> newTags = new HashSet<String>(TaggableItem.getList(values.getAsString(Tag.PATH)));

			// If the CV_TAG_PREFIX key is present, only work with tags that have that prefix.
			if (values.containsKey(TaggableItem.CV_TAG_PREFIX)){
				final String prefix = values.getAsString(TaggableItem.CV_TAG_PREFIX);
				TaggableItem.filterTagsInPlace(prefix, newTags);
				TaggableItem.filterTagsInPlace(prefix, existingTags);
				values.remove(TaggableItem.CV_TAG_PREFIX);
			}

			// tags that need to be removed.
			final Set<String> toDelete = new HashSet<String>(existingTags);
			toDelete.removeAll(newTags);

			final List<String> toDeleteWhere = new ArrayList<String>(toDelete);
			for (int i = 0; i < toDeleteWhere.size(); i++ ){
				toDeleteWhere.set(i, Tag._NAME + "=\"" + toDeleteWhere.get(i) + '"');
			}
			if (toDeleteWhere.size() > 0){
				final String delWhere = ListUtils.join(toDeleteWhere, " OR ");
				delete(uri, delWhere, null);
			}

			// duplicates will be ignored.
			final ContentValues cvAdd = new ContentValues();
			cvAdd.put(Tag.PATH, TaggableItem.toListString(newTags));
			insert(uri, cvAdd);
			count = 0;
			break;

		}

			default:
				throw new IllegalArgumentException("unknown URI "+uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		if (needSync && canSync){
			getContext().startService(new Intent(Intent.ACTION_SYNC, uri));
		}
		return count;
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		final SQLiteDatabase db = dbHelper.getWritableDatabase();
		final long id;

		switch (uriMatcher.match(uri)){
		case MATCHER_CAST_DIR:
			db.delete(CAST_TABLE_NAME, where, whereArgs);
			break;

		case MATCHER_CAST_ITEM:
			id = ContentUris.parseId(uri);
			db.delete(CAST_TABLE_NAME, Cast._ID + "="+ id +
					(where != null && where.length() > 0 ? " AND (" + where + ")" : ""),
					whereArgs);
			break;

		case MATCHER_PROJECT_CAST_ITEM:{
			final List<String> pathSegs = uri.getPathSegments();
			id = ContentUris.parseId(uri);
			db.delete(CAST_TABLE_NAME,
					addExtraWhere(where, 			Cast._ID+"=?",		Cast._PROJECT_ID+"=?"),
					addExtraWhereArgs(whereArgs,	Long.toString(id), 	pathSegs.get(pathSegs.size() - 3)));
		}break;

		case MATCHER_PROJECT_DIR:
			db.delete(PROJECT_TABLE_NAME, where, whereArgs);
			break;

		case MATCHER_PROJECT_ITEM:
			id = ContentUris.parseId(uri);
			db.delete(PROJECT_TABLE_NAME, Project._ID + "="+ id +
					(where != null && where.length() > 0 ? " AND (" + where + ")" : ""),
					whereArgs);
			break;

		case MATCHER_COMMENT_DIR:
			db.delete(COMMENT_TABLE_NAME, where, whereArgs);
			break;

		case MATCHER_COMMENT_ITEM:{
			id = ContentUris.parseId(uri);
			db.delete(COMMENT_TABLE_NAME,
					addExtraWhere(where, Comment._ID + "=?"),
					addExtraWhereArgs(whereArgs, String.valueOf(id)));
		}break;

		case MATCHER_CHILD_COMMENT_ITEM:{

			final List<String>pathSegs = uri.getPathSegments();
			id = ContentUris.parseId(uri);

			db.delete(COMMENT_TABLE_NAME,
					addExtraWhere(where, Comment._ID+"=?", 				Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					addExtraWhereArgs(whereArgs, Long.toString(id),  	pathSegs.get(pathSegs.size() - 3), 	pathSegs.get(pathSegs.size() - 4)));
		}break;

		case MATCHER_ITEM_TAGS:{

			final List<String> pathSegments = uri.getPathSegments();

			db.delete(TAG_TABLE_NAME,
					addExtraWhere(where, 			Tag._REF_CLASS + "=?", 						Tag._REF_ID+"=?"),
					addExtraWhereArgs(whereArgs, 	pathSegments.get(pathSegments.size() - 3), 	pathSegments.get(pathSegments.size() - 2)));
			break;

		}

		case MATCHER_TAG_DIR:{
			db.delete(TAG_TABLE_NAME, where, whereArgs);
			break;
		}

		case MATCHER_CAST_CASTMEDIA_DIR:
		case MATCHER_PROJECT_CAST_CASTMEDIA_DIR:{
			final List<String> pathSegs = uri.getPathSegments();

			db.delete(CAST_MEDIA_TABLE_NAME,
					addExtraWhere(where, 			CastMedia._PARENT_ID+"=?"),
					addExtraWhereArgs(whereArgs,	pathSegs.get(pathSegs.size() - 2)));
		}break;



		case MATCHER_CAST_MEDIA_DIR:{
			db.delete(CAST_MEDIA_TABLE_NAME, where, whereArgs);
			break;
		}

		case MATCHER_SHOTLIST_DIR:{

			db.delete(SHOTLIST_TABLE_NAME, where, whereArgs);
		}break;

			default:
				throw new IllegalArgumentException("Unknown URI: "+uri);
		}
		db.execSQL("VACUUM");
		getContext().getContentResolver().notifyChange(uri, null);
		return 0;
	}

	/**
	 * @param cr
	 * @param uri
	 * @return The path that one should post to for the given content item. Should always point to an item, not a dir.
	 */
	public static String getPostPath(ContentResolver cr, Uri uri){
		return getPublicPath(cr, uri, null, true);
	}

	public static String getPublicPath(ContentResolver cr, Uri uri){
		return getPublicPath(cr, uri, null, false);
	}

	/**
	 * Returns a public ID to an item, given the parent URI and a public ID
	 *
	 * @param cr
	 * @param uri URI of the parent item
	 * @param publicId public ID of the child item
	 * @return
	 */
	public static String getPublicPath(ContentResolver cr, Uri uri, Long publicId){
		return getPublicPath(cr, uri, publicId, false);
	}

	private static String getPathFromField(ContentResolver cr, Uri uri, String field){
		String path = null;
		final String[] generalProjection = {JsonSyncableItem._ID, field};
		final Cursor c = cr.query(uri, generalProjection, null, null, null);
		if (c.getCount() == 1 && c.moveToFirst()){
			final String storedPath = c.getString(c.getColumnIndex(field));
			if (storedPath != null){
				path = storedPath;
			}
		}
		c.close();
		return path;
	}

	public static String getPublicPath(ContentResolver cr, Uri uri, Long publicId, boolean parent){
		String path;

		final int match = uriMatcher.match(uri);

		// first check to see if the path is stored already.
		switch (match){

		// these should be the only hard-coded paths in the system.
		case MATCHER_CAST_DIR:{
			path = Cast.SERVER_PATH;

		}break;

		case MATCHER_PROJECT_DIR:{
			path = Project.SERVER_PATH;
		}break;

		case MATCHER_COMMENT_DIR:
			path = Comment.SERVER_PATH;
			break;

		case MATCHER_CHILD_COMMENT_DIR:
			path = getPathFromField(cr, removeLastPathSegment(uri), Commentable.Columns._COMMENT_DIR_URI);
			break;

		case MATCHER_CAST_ITEM:
		case MATCHER_CAST_MEDIA_ITEM:
		case MATCHER_CHILD_COMMENT_ITEM:
		case MATCHER_COMMENT_ITEM:
		case MATCHER_PROJECT_CAST_CASTMEDIA_ITEM:
		case MATCHER_PROJECT_CAST_ITEM:
		case MATCHER_PROJECT_ITEM:
		case MATCHER_PROJECT_SHOTLIST_ITEM:{
			if (parent || publicId != null){
				path = getPublicPath(cr, removeLastPathSegment(uri));
			}else{
				path = getPathFromField(cr, uri, JsonSyncableItem._PUBLIC_URI);
			}

			}break;

		case MATCHER_PROJECT_CAST_DIR:{
			path = getPathFromField(cr, removeLastPathSegment(uri), Project._CASTS_URI);
		}break;

		case MATCHER_PROJECT_CAST_CASTMEDIA_DIR:
		case MATCHER_CAST_CASTMEDIA_DIR:
		case MATCHER_CAST_MEDIA_DIR: {
			path = getPathFromField(cr, removeLastPathSegment(uri), Cast._CASTMEDIA_DIR_URI);
		}break;

		default:
			throw new IllegalArgumentException("Don't know how to get the public path for "+uri);
		}

		if (path == null){
			throw new RuntimeException("got null path for " + uri);
		}
		if (publicId != null){
			path += publicId + "/";
		}

		path = path.replaceAll("//", "/"); // hack to get around a tedious problem
		//Log.d("MediaProvider", "gave "+path+" for a public "+(parent ? "parent ": "") +"path for "+uri + ((publicId != null && publicId >= 0) ? " with public ID "+publicId : ""));

		return path;
	}


	public static UriMatcher getUriMatcher(){
		return uriMatcher;
	}

	/**
	 * Remove the last path segment of a URI
	 * @param uri
	 * @return
	 */
	public static Uri removeLastPathSegment(Uri uri){
		return removeLastPathSegments(uri, 1);
	}

	/**
	 * Remove count path segments from the end of a URI
	 * @param uri
	 * @param count
	 * @return
	 */
	public static Uri removeLastPathSegments(Uri uri, int count){
		final List<String> pathWithoutLast = new Vector<String>(uri.getPathSegments());
		for (int i = 0; i < count; i++){
			pathWithoutLast.remove(pathWithoutLast.size() - 1);
		}
		final String parentPath = ListUtils.join(pathWithoutLast, "/");
		return uri.buildUpon().path(parentPath).build();
	}

	/**
	 * Removes key from the given ContentValues and returns it in a new container.
	 *
	 * @param cv
	 * @param key
	 * @return
	 */
	private static ContentValues extractContentValueItem(ContentValues cv, String key){
		final String val = cv.getAsString(key);
		cv.remove(key);
		final ContentValues cvNew = new ContentValues();
		cvNew.put(key, val);
		return cvNew;
	}

	/**
	 * Adds extra where clauses
	 * @param where
	 * @param extraWhere
	 * @return
	 */
	public static String addExtraWhere(String where, String ... extraWhere){
		final String extraWhereJoined = "(" + ListUtils.join(Arrays.asList(extraWhere), ") AND (") + ")";
		return extraWhereJoined + (where != null && where.length() > 0 ? " AND ("+where+")":"");
	}

	/**
	 * Adds in extra arguments to a where query. You'll have to put in the appropriate
	 * @param whereArgs the original whereArgs passed in from the query. Can be null.
	 * @param extraArgs Extra arguments needed for the query.
	 * @return
	 */
	public static String[] addExtraWhereArgs(String[] whereArgs, String...extraArgs){
		final List<String> whereArgs2 = new ArrayList<String>();
		if (whereArgs != null){
			whereArgs2.addAll(Arrays.asList(whereArgs));
		}
		whereArgs2.addAll(0, Arrays.asList(extraArgs));
		return whereArgs2.toArray(new String[]{});
	}

	/**
	 * Handly helper
	 * @param c
	 * @param projection
	 */
	public static void dumpCursorToLog(Cursor c, String[] projection){
		final StringBuilder testOut = new StringBuilder();
		for (final String row: projection){
			testOut.append(row);
			testOut.append("=");

			if (c.isNull(c.getColumnIndex(row))){
				testOut.append("<<null>>");
			}else{
				testOut.append(c.getString(c.getColumnIndex(row)));

			}
			testOut.append("; ");
		}
		Log.d("CursorDump", testOut.toString());
	}

	/**
	 * Translates a numerical public ID stored in JsonSyncableItem._PUBLIC_ID to a full path.
	 * Stores results in JsonSyncableItem._PUBLIC_URI
	 *
	 * XXX hack!
	 * This is a workaround to help transition to an API with all objects having URIs
	 * This should be removed ASAP.
	 *
	 * @param context
	 * @param values the values that should be modified
	 * @param canSync if the object can sync
	 * @param uri full URI of the object
	 */
	public static void translateIdToUri(Context context, ContentValues values, boolean canSync, Uri uri){
		final int type = uriMatcher.match(uri);

		if (canSync || type == MATCHER_CHILD_COMMENT_ITEM){
			final ContentResolver cr = context.getContentResolver();
			final String pubUri = values.getAsString(JsonSyncableItem._PUBLIC_URI);
			if (pubUri == null && values.getAsLong(JsonSyncableItem._PUBLIC_ID) != null){
				String path = getPublicPath(cr, uri, values.getAsLong(JsonSyncableItem._PUBLIC_ID));
				if (type == MATCHER_CAST_DIR || type == MATCHER_CAST_ITEM ||
					type == MATCHER_PROJECT_CAST_ITEM || type == MATCHER_PROJECT_CAST_DIR){
					String projectUri = values.getAsString(Cast._PROJECT_URI);
					if (projectUri != null){
						if (!projectUri.contains("project")){
							projectUri = getPublicPath(cr, Project.CONTENT_URI, Long.valueOf(projectUri));
							values.put(Cast._PROJECT_URI, projectUri);
						}
						if (!path.contains("project")){
							path = projectUri + path;
						}
					}
				}
				path = path.replaceAll("//", "/");
				//Log.d("MediaProvider", "Setting public URI for "+uri + " (or item inserted into it) to " + path);
				values.put(JsonSyncableItem._PUBLIC_URI, path);

			}
		}

		// add in the various directory URIs if missing.
		switch (type){
		case MATCHER_CAST_DIR:
		case MATCHER_CAST_ITEM:
		case MATCHER_PROJECT_CAST_DIR:
		case MATCHER_PROJECT_CAST_ITEM:
			if (!values.containsKey(Cast._CASTMEDIA_DIR_URI)){
				values.put(Cast._CASTMEDIA_DIR_URI, values.getAsString(JsonSyncableItem._PUBLIC_URI) + CastMedia.SERVER_PATH);
			}
			if (!values.containsKey(Cast._COMMENT_DIR_URI)){
				values.put(Cast._COMMENT_DIR_URI, values.getAsString(JsonSyncableItem._PUBLIC_URI) + Comment.SERVER_PATH);
			}
			break;

		case MATCHER_PROJECT_DIR:
		case MATCHER_PROJECT_ITEM:
			if (!values.containsKey(Project._CASTS_URI)){
				values.put(Project._CASTS_URI, values.getAsString(JsonSyncableItem._PUBLIC_URI) + Cast.SERVER_PATH);
			}
			if (!values.containsKey(Project._COMMENT_DIR_URI)){
				values.put(Project._COMMENT_DIR_URI, values.getAsString(JsonSyncableItem._PUBLIC_URI) + Comment.SERVER_PATH);
			}
			break;
		}
	}


	static {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI(AUTHORITY, Cast.PATH, MATCHER_CAST_DIR);
		uriMatcher.addURI(AUTHORITY, Cast.PATH+"/#", MATCHER_CAST_ITEM);

		// cast media
		uriMatcher.addURI(AUTHORITY, Cast.PATH+"/#/"+CastMedia.PATH, MATCHER_CAST_CASTMEDIA_DIR);
		uriMatcher.addURI(AUTHORITY, Cast.PATH+"/#/"+CastMedia.PATH+"/#", MATCHER_CAST_MEDIA_ITEM);

		// cast media
		uriMatcher.addURI(AUTHORITY, CastMedia.PATH, MATCHER_CAST_MEDIA_DIR);
		uriMatcher.addURI(AUTHORITY, CastMedia.PATH+"/#", MATCHER_CAST_MEDIA_ITEM);

		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH + "/#/" + CastMedia.PATH, MATCHER_PROJECT_CAST_CASTMEDIA_DIR);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH + "/#/" + CastMedia.PATH + "/#/", MATCHER_PROJECT_CAST_CASTMEDIA_ITEM);

		uriMatcher.addURI(AUTHORITY, Project.PATH, MATCHER_PROJECT_DIR);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#", MATCHER_PROJECT_ITEM);

		// /comments/1, etc.
		uriMatcher.addURI(AUTHORITY, Comment.PATH, MATCHER_COMMENT_DIR);
		uriMatcher.addURI(AUTHORITY, Comment.PATH + "/#", MATCHER_COMMENT_ITEM);

		// project/1/comments, etc.
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Comment.PATH, MATCHER_CHILD_COMMENT_DIR);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Comment.PATH + "/#", MATCHER_CHILD_COMMENT_ITEM);
		uriMatcher.addURI(AUTHORITY, Cast.PATH + "/#/" + Comment.PATH, MATCHER_CHILD_COMMENT_DIR);
		uriMatcher.addURI(AUTHORITY, Cast.PATH + "/#/" + Comment.PATH + "/#", MATCHER_CHILD_COMMENT_ITEM);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH + "/#/" + Comment.PATH, MATCHER_CHILD_COMMENT_DIR);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH + "/#/" +Comment.PATH + "/#", MATCHER_CHILD_COMMENT_ITEM);

		// /project/1/content, etc
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH, MATCHER_PROJECT_CAST_DIR);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH + "/#", MATCHER_PROJECT_CAST_ITEM);

		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + ShotList.PATH, MATCHER_PROJECT_SHOTLIST_DIR);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + ShotList.PATH + "/#", MATCHER_PROJECT_SHOTLIST_ITEM);

		uriMatcher.addURI(AUTHORITY, ShotList.PATH, MATCHER_SHOTLIST_DIR);

		// /content/1/tags
		uriMatcher.addURI(AUTHORITY, Cast.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/"+Cast.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);

		// /content/tags/tag1,tag2
		uriMatcher.addURI(AUTHORITY, Cast.PATH +'/'+ Tag.PATH + "/*", MATCHER_CAST_BY_TAGS);
		uriMatcher.addURI(AUTHORITY, Project.PATH +'/'+Tag.PATH + "/*", MATCHER_PROJECT_BY_TAGS);

		// tag list
		uriMatcher.addURI(AUTHORITY, Tag.PATH, MATCHER_TAG_DIR);
	}
}
