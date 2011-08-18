package edu.mit.mobile.android.locast.data;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import edu.mit.mobile.android.content.DBHelper;
import edu.mit.mobile.android.content.DBHelperMapper;
import edu.mit.mobile.android.content.GenericDBHelper;
import edu.mit.mobile.android.content.ManyToMany;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.accounts.AuthenticationService;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.utils.ListUtils;

public class MediaProvider extends ContentProvider {
	@SuppressWarnings("unused")
	private final static String TAG = MediaProvider.class.getSimpleName();
	public final static String NAMESPACE = "edu.mit.mobile.android.locast.ver2";
	public final static String AUTHORITY = NAMESPACE + ".provider";


	private static final String
		CAST_TABLE_NAME       = "casts",
		CASTMEDIA_TABLE_NAME = "castmedia", // casts with multiple media objects
		COMMENT_TABLE_NAME    = "comments",
		TAG_TABLE_NAME        = "tags",
		ITINERARY_TABLE_NAME  = "itineraries",
		EVENT_TABLE_NAME      = "events";

	public final static String
		TYPE_CAST_ITEM = "vnd.android.cursor.item/vnd."+NAMESPACE+".casts",
		TYPE_CAST_DIR  = "vnd.android.cursor.dir/vnd."+NAMESPACE+".casts",

		TYPE_CASTMEDIA_ITEM = "vnd.android.cursor.item/vnd."+NAMESPACE+".castmedia",
		TYPE_CASTMEDIA_DIR  = "vnd.android.cursor.dir/vnd."+NAMESPACE+".castmedia",

		TYPE_COMMENT_ITEM = "vnd.android.cursor.item/vnd."+NAMESPACE+".comments",
		TYPE_COMMENT_DIR  = "vnd.android.cursor.dir/vnd."+NAMESPACE+".comments",

		TYPE_TAG_DIR      = "vnd.android.cursor.dir/vnd."+NAMESPACE+".tags",

		TYPE_ITINERARY_DIR  =  "vnd.android.cursor.dir/vnd."+NAMESPACE+".itineraries",
		TYPE_ITINERARY_ITEM = "vnd.android.cursor.item/vnd."+NAMESPACE+".itineraries",

		TYPE_EVENT_DIR  =  "vnd.android.cursor.dir/vnd."+NAMESPACE+"."+EVENT_TABLE_NAME,
		TYPE_EVENT_ITEM = "vnd.android.cursor.item/vnd."+NAMESPACE+"."+EVENT_TABLE_NAME
		;


	private static final JSONSyncableIdenticalChildFinder mChildFinder = new JSONSyncableIdenticalChildFinder();
	private static final ManyToMany.M2MDBHelper
		ITINERARY_CASTS_DBHELPER = new ManyToMany.M2MDBHelper(ITINERARY_TABLE_NAME, CAST_TABLE_NAME, mChildFinder, Cast.CONTENT_URI),
		CASTS_CASTMEDIA_DBHELPER = new ManyToMany.M2MDBHelper(CAST_TABLE_NAME, CASTMEDIA_TABLE_NAME, mChildFinder);

	private static final DBHelper
		EVENT_DBHELPER = new GenericDBHelper(EVENT_TABLE_NAME, Event.CONTENT_URI);

	private final static UriMatcher uriMatcher;

	private final static DBHelperMapper mDBHelperMapper = new DBHelperMapper();

	private static final int
		MATCHER_CAST_DIR             = 1,
		MATCHER_CAST_ITEM            = 2,
		MATCHER_COMMENT_DIR          = 5,
		MATCHER_COMMENT_ITEM         = 6,
		MATCHER_EVENT_DIR 			 = 7,
		MATCHER_EVENT_ITEM			 = 8,
		MATCHER_CHILD_COMMENT_DIR    = 9,
		MATCHER_CHILD_COMMENT_ITEM   = 10,
		MATCHER_TAG_DIR              = 13,
		MATCHER_ITEM_TAGS    		 = 14,
		MATCHER_ITINERARY_DIR        = 21,
		MATCHER_ITINERARY_ITEM       = 22,
		MATCHER_CHILD_CAST_DIR   	 = 23,
		MATCHER_CHILD_CAST_ITEM  	 = 24,
		MATCHER_ITINERARY_BY_TAGS    = 27,
		MATCHER_CHILD_CASTMEDIA_DIR  = 28,
		MATCHER_CHILD_CASTMEDIA_ITEM = 29;
		;

	private static class DatabaseHelper extends SQLiteOpenHelper {
		private static final String DB_NAME = "content.db";
		private static final int DB_VER = 41;

		public DatabaseHelper(Context context) {
			super(context, DB_NAME, null, DB_VER);
		}

		private static final String JSON_SYNCABLE_ITEM_FIELDS =
			JsonSyncableItem._ID 			 + " INTEGER PRIMARY KEY,"
			+ JsonSyncableItem._PUBLIC_URI 	 + " TEXT UNIQUE,"
			+ JsonSyncableItem._MODIFIED_DATE+ " INTEGER,"
			+ JsonSyncableItem._CREATED_DATE + " INTEGER,";

		private static final String JSON_COMMENTABLE_FIELDS =
			Commentable.Columns._COMMENT_DIR_URI  + " TEXT,";

		private static final String LOCATABLE_FIELDS =
			Locatable.Columns._GEOCELL + " TEXT,"
			+ Locatable.Columns._LATITUDE 	+ " REAL,"
			+ Locatable.Columns._LONGITUDE 	+ " REAL,";

		@Override
		public void onCreate(SQLiteDatabase db) {

			db.execSQL("CREATE TABLE "  + CAST_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ JSON_COMMENTABLE_FIELDS
					+ LOCATABLE_FIELDS
					+ Cast._TITLE 		+ " TEXT,"
					+ Cast._AUTHOR 		+ " TEXT,"
					+ Cast._AUTHOR_URI	+ " TEXT,"
					+ Cast._DESCRIPTION + " TEXT,"
					+ Cast._MEDIA_PUBLIC_URI 	+ " TEXT,"

					+ Cast._PRIVACY 	+ " TEXT,"

					+ Cast._FAVORITED   + " BOOLEAN,"
					+ Cast._DRAFT       + " BOOLEAN,"
					+ Cast._OFFICIAL    + " BOOLEAN,"

					+ Cast._THUMBNAIL_URI+ " TEXT"
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

			db.execSQL("CREATE TABLE "+ CASTMEDIA_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ CastMedia._AUTHOR        + " TEXT,"
					+ CastMedia._AUTHOR_URI	   + " TEXT,"
					+ CastMedia._TITLE		   + " TEXT,"
					+ CastMedia._DESCRIPTION   + " TEXT,"
					+ CastMedia._LANGUAGE      + " TEXT,"

					+ CastMedia._MEDIA_URL     + " TEXT,"
					+ CastMedia._LOCAL_URI     + " TEXT,"
					+ CastMedia._MIME_TYPE     + " TEXT,"
					+ CastMedia._THUMBNAIL     + " TEXT,"
					+ CastMedia._THUMB_LOCAL   + " TEXT,"
					+ CastMedia._KEEP_OFFLINE  + " BOOLEAN,"
					+ CastMedia._DURATION      + " INTEGER"
			+ ")"
			);

			db.execSQL("CREATE TABLE " + ITINERARY_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ Itinerary._TITLE 		+ " TEXT,"
					+ Itinerary._AUTHOR		+ " TEXT,"
					+ Itinerary._AUTHOR_URI		+ " TEXT,"
					+ Itinerary._DESCRIPTION 	+ " TEXT,"
					+ Itinerary._PRIVACY 		+ " TEXT,"
					+ Itinerary._CASTS_URI	+ " TEXT,"

					+ Itinerary._PATH 		+ " TEXT,"
					+ Itinerary._CASTS_COUNT + " INTEGER,"
					+ Itinerary._FAVORITES_COUNT + " INTEGER,"
					+ Itinerary._FAVORITED   + " BOOLEAN,"

					+ Itinerary._THUMBNAIL     + " TEXT,"

					+ Itinerary._DRAFT        + " BOOLEAN"

					+ ");"
			);

			db.execSQL("CREATE TABLE "  + EVENT_TABLE_NAME + " ("
					+ JSON_SYNCABLE_ITEM_FIELDS
					+ LOCATABLE_FIELDS
					+ Event._TITLE 		 + " TEXT,"
					+ Event._AUTHOR 	 + " TEXT,"
					+ Event._AUTHOR_URI	 + " TEXT,"
					+ Event._DESCRIPTION + " TEXT,"
					+ Event._START_DATE  + " INTEGER,"
					+ Event._END_DATE    + " INTEGER,"

					+ Event._DRAFT       + " BOOLEAN,"

					+ Event._THUMBNAIL_URI+ " TEXT"
					+ ");"
					);

			ITINERARY_CASTS_DBHELPER.createJoinTable(db);
			CASTS_CASTMEDIA_DBHELPER.createJoinTable(db);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// If it's a step greater than 1, step through all older versions and incrementally upgrade to the latest
			for (; oldVersion < newVersion - 1; oldVersion++){
				onUpgrade(db, oldVersion, oldVersion + 1);
			}

			db.execSQL("DROP TABLE IF EXISTS " + CAST_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + COMMENT_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + TAG_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + CASTMEDIA_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + CASTMEDIA_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + ITINERARY_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + EVENT_TABLE_NAME);
			ITINERARY_CASTS_DBHELPER.deleteJoinTable(db);
			CASTS_CASTMEDIA_DBHELPER.deleteJoinTable(db);
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

		case MATCHER_COMMENT_ITEM:
		case MATCHER_CHILD_COMMENT_ITEM:

			return false;

		case MATCHER_CHILD_COMMENT_DIR:

		case MATCHER_COMMENT_DIR:


		case MATCHER_CAST_DIR:
		case MATCHER_CAST_ITEM:

		case MATCHER_CHILD_CASTMEDIA_DIR:
		case MATCHER_CHILD_CASTMEDIA_ITEM:

		case MATCHER_CHILD_CAST_DIR:
		case MATCHER_CHILD_CAST_ITEM:
		case MATCHER_ITINERARY_DIR:
		case MATCHER_ITINERARY_ITEM:

		case MATCHER_EVENT_DIR:
		case MATCHER_EVENT_ITEM:
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

		case MATCHER_CHILD_CASTMEDIA_DIR:
			return TYPE_CASTMEDIA_DIR;

		case MATCHER_CHILD_CASTMEDIA_ITEM:
			return TYPE_CASTMEDIA_ITEM;

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

			//////////////// itineraries

		case MATCHER_CHILD_CAST_DIR:
			return TYPE_CAST_DIR;
		case MATCHER_CHILD_CAST_ITEM:
			return TYPE_CAST_ITEM;

		case MATCHER_ITINERARY_DIR:
			return TYPE_ITINERARY_DIR;
		case MATCHER_ITINERARY_ITEM:
			return TYPE_ITINERARY_ITEM;

		case MATCHER_EVENT_DIR:
			return TYPE_EVENT_DIR;
		case MATCHER_EVENT_ITEM:
			return TYPE_EVENT_ITEM;

		default:
			throw new IllegalArgumentException("Cannot get type for URI "+uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		final SQLiteDatabase db = dbHelper.getWritableDatabase();
		final Context context = getContext();
		long rowid;
		final boolean syncable = canSync(uri);
		if (syncable && !values.containsKey(JsonSyncableItem._MODIFIED_DATE)){
			values.put(JsonSyncableItem._MODIFIED_DATE, new Date().getTime());
		}
		values.remove(JsonSyncableItem._ID);

		Uri newItem = null;
		boolean isDraft = false;

		final int code = uriMatcher.match(uri);
		switch (code){
		//////////////////////////////////////////////////////////////////////////////////
		case MATCHER_CAST_DIR:{
			Assert.assertNotNull(values);
			final ContentValues cvTags = ProviderUtils.extractContentValueItem(values, Tag.PATH);

			rowid = db.insert(CAST_TABLE_NAME, null, values);
			if (rowid > 0){
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
		//////////////////////////////////////////////////////////////////////////////////
		case MATCHER_COMMENT_DIR:{
			Assert.assertNotNull(values);
			rowid = db.insert(COMMENT_TABLE_NAME, null, values);
			if (rowid > 0){
				newItem = ContentUris.withAppendedId(Comment.CONTENT_URI, rowid);
			}
		}break;
		//////////////////////////////////////////////////////////////////////////////////
		case MATCHER_CHILD_COMMENT_DIR:{
			final List<String> pathSegs = uri.getPathSegments();
			values.put(Comment._PARENT_CLASS, pathSegs.get(pathSegs.size() - 3));
			values.put(Comment._PARENT_ID, pathSegs.get(pathSegs.size() - 2));
			rowid = db.insert(COMMENT_TABLE_NAME, null, values);
			if (rowid > 0){
				newItem = ContentUris.withAppendedId(uri, rowid);
			}
		}break;
		//////////////////////////////////////////////////////////////////////////////////
		case MATCHER_ITEM_TAGS:{
			final List<String> pathSegments = uri.getPathSegments();
			values.put(Tag._REF_CLASS, pathSegments.get(pathSegments.size() - 3));
			values.put(Tag._REF_ID, pathSegments.get(pathSegments.size() - 2));

			rowid = 0;

			final ContentValues cv2 = new ContentValues(values);
			cv2.remove(Tag.PATH);
			try {
				db.beginTransaction();
				for (final String tag : TaggableItem.getList(values.getAsString(Tag.PATH))){
					cv2.put(Tag._NAME, tag);
					rowid = db.insert(TAG_TABLE_NAME, null, cv2);
				}
				db.setTransactionSuccessful();
			}finally{
				db.endTransaction();
			}

			newItem = ContentUris.withAppendedId(uri, rowid);

			break;
		}

		//////////////////////////////////////////////////////////////////////////////////
		case MATCHER_ITINERARY_BY_TAGS:
		case MATCHER_ITINERARY_DIR:{
			newItem = insertWithTags(values, db, ITINERARY_TABLE_NAME, Itinerary.CONTENT_URI);
			if (newItem != null){
				if (values.containsKey(Itinerary._DRAFT)){
					isDraft = values.getAsBoolean(Itinerary._DRAFT);
				}else{
					isDraft = false;
				}
			}
		} break;
		//////////////////////////////////////////////////////////////////////////////////

		//////////////////////////////////////////////////////////////////////////////////

		default:
			if (mDBHelperMapper.canInsert(code)){
				// XXX draft should probably be looked at better.
				if (values.containsKey(TaggableItem._DRAFT)){
					isDraft = values.getAsBoolean(TaggableItem._DRAFT);
				}else{
					isDraft = false;
				}
				newItem = mDBHelperMapper.insert(code, this, db, uri, values);
			}else{
				throw new IllegalArgumentException("Unknown URI: "+uri);
			}
		}

		if (newItem != null){
			context.getContentResolver().notifyChange(uri, null);
		}else{
			throw new SQLException("Failed to insert row into "+uri);
		}

		// XXX figure out sync
//		if (syncable && !isDraft){
//			context.startService(new Intent(Intent.ACTION_SYNC, uri));
//		}

		return newItem;
	}

	/**
	 * Performs an insert on the database, taking the tags parameter from the ContentValues,
	 * parsing it and inserting it into the appropriate tables.
	 *
	 * @param values standard ContentValues, but with a special Tag.PATH parameter.
	 * @param db
	 * @param table
	 * @param contentUri
	 * @return
	 */
	private Uri insertWithTags(ContentValues values, SQLiteDatabase db, String table, Uri contentUri){
		Uri newItem = null;
		final ContentValues cvTags = ProviderUtils.extractContentValueItem(values, Tag.PATH);
		db.beginTransaction();
		try {
			final long rowid = db.insert(table, null, values);
			if (rowid > 0){
				newItem = ContentUris.withAppendedId(contentUri, rowid);
				update(Uri.withAppendedPath(newItem, Tag.PATH), cvTags, null, null);
				db.setTransactionSuccessful();
			}
		}finally{
			db.endTransaction();
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
		final int code = uriMatcher.match(uri);
		switch (code){
		case MATCHER_CAST_DIR:{
			qb.setTables(CAST_TABLE_NAME);

			final String tags = uri.getQueryParameter(TaggableItem.SERVER_QUERY_PARAMETER);
			final String dist = uri.getQueryParameter(Locatable.SERVER_QUERY_PARAMETER);
			final Boolean favorited = Favoritable.decodeFavoritedUri(uri);

			if (favorited != null){
				selection = ProviderUtils.addExtraWhere(selection, Favoritable.Columns._FAVORITED + "=?");
				selectionArgs = ProviderUtils.addExtraWhereArgs(selectionArgs, favorited ? "1" : "0");
			}

			if (tags != null){
				c = queryByTags(qb, db, tags, CAST_TABLE_NAME, projection, selection, selectionArgs, sortOrder);
			}else if (dist != null){
				c = queryByLocation(qb, db, dist, CAST_TABLE_NAME, projection, selection, selectionArgs, sortOrder);
			}else{
				c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			}
		}break;

		case MATCHER_CAST_ITEM:
			qb.setTables(CAST_TABLE_NAME);
			id = ContentUris.parseId(uri);
			qb.appendWhere(Cast._ID + "="+id);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;


		case MATCHER_EVENT_DIR:{
			qb.setTables(EVENT_TABLE_NAME);
			final String tags = uri.getQueryParameter(TaggableItem.SERVER_QUERY_PARAMETER);
			final String dist = uri.getQueryParameter(Locatable.SERVER_QUERY_PARAMETER);

			if (tags != null){
				c = queryByTags(qb, db, tags, EVENT_TABLE_NAME, projection, selection, selectionArgs, sortOrder);
			}else if (dist != null){
				c = queryByLocation(qb, db, dist, EVENT_TABLE_NAME, projection, selection, selectionArgs, sortOrder);
			}else{
				c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			}
		}break;

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
					ProviderUtils.addExtraWhere(selection, 			Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					ProviderUtils.addExtraWhereArgs(selectionArgs, 	pathSegs.get(pathSegs.size() - 2), 	pathSegs.get(pathSegs.size() - 3)), null, null, sortOrder);
			break;
		}

		case MATCHER_CHILD_COMMENT_ITEM:{
			final List<String> pathSegs = uri.getPathSegments();

			id = ContentUris.parseId(uri);

			c = db.query(COMMENT_TABLE_NAME, projection,
					ProviderUtils.addExtraWhere(selection, 			Comment._ID+"=?", 	Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					ProviderUtils.addExtraWhereArgs(selectionArgs, 	String.valueOf(id), pathSegs.get(pathSegs.size() - 3), 	pathSegs.get(pathSegs.size() - 4))
					, null, null, sortOrder);
			break;
		}

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

		case MATCHER_ITINERARY_DIR:{
			if (sortOrder == null){
				sortOrder = Itinerary.SORT_DEFAULT;
			}
			c = db.query(ITINERARY_TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);

		}break;

		case MATCHER_ITINERARY_ITEM:{
			final String itemId = uri.getLastPathSegment();
			c = db.query(ITINERARY_TABLE_NAME,
					projection,
					ProviderUtils.addExtraWhere(selection, Itinerary._ID+"=?"),
					ProviderUtils.addExtraWhereArgs(selectionArgs, itemId),
					null, null, sortOrder);
		}break;

			default:
				if (mDBHelperMapper.canQuery(code)){
					c = mDBHelperMapper.query(code, this, db, uri, projection, selection, selectionArgs, sortOrder);
				}else{
					throw new IllegalArgumentException("unknown URI "+uri);
				}
		}

		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	// TODO rework tag system to use m2m relationship or at least rework this to use the builder
	private Cursor queryByTags(SQLiteQueryBuilder qb, SQLiteDatabase db, String tagString, String taggableItemTable, String[] projection, String selection, String[] selectionArgs, String sortOrder){
		qb.setTables(taggableItemTable + " AS c, "+TAG_TABLE_NAME +" AS t");

		final Set<String> tags = Tag.toSet(tagString.toLowerCase());
		final List<String> tagFilterList = new ArrayList<String>(tags.size());

		qb.appendWhere("t."+Tag._REF_ID+"=c."+TaggableItem._ID);
		for (final String tag : tags){
			tagFilterList.add(DatabaseUtils.sqlEscapeString(tag));
		}
		qb.appendWhere(" AND (t."+Tag._NAME+" IN ("+ListUtils.join(tagFilterList, ",")+"))");

		// limit to only items of the given object class
		qb.appendWhere(" AND t."+Tag._REF_CLASS + "=\""+taggableItemTable+"\"");

		// Modify the projection so that _ID explicitly refers to that of the objects being searched,
		// not the tags. Without this, _ID is ambiguous and the query fails.
		final String[] projection2 = ProviderUtils.addPrefixToProjection("c", projection);

		return qb.query(db, projection2, selection, selectionArgs,
				"c."+TaggableItem._ID,
				"COUNT ("+"c."+TaggableItem._ID+")="+tags.size(),
				sortOrder);
	}

	private static final Pattern LOC_STRING_REGEX =  Pattern.compile("^([\\d\\.-]+),([\\d\\.-]+),([\\d\\.]+)");
	private Cursor queryByLocation(SQLiteQueryBuilder qb, SQLiteDatabase db, String locString, String locatableItemTable, String[] projection, String selection, String[] selectionArgs, String sortOrder){

		qb.setTables(locatableItemTable);
		final Matcher m = LOC_STRING_REGEX.matcher(locString);
		if (!m.matches()){
			throw new IllegalArgumentException("bad location string '"+locString+"'");
		}
		final String lon = m.group(1);
		final String lat = m.group(2);
		final String dist = m.group(3);

		//final GeocellQuery gq = new GeocellQuery();
		//GeocellUtils.compute(new Point(Double.valueOf(lat), Double.valueOf(lon)), resolution);
		//String extraWhere = "(lat - 2) > ? AND (lon - 2) > ? AND (lat + 2) < ? AND (lat + 2) < ?";
		final String[] extraArgs = {lat, lon};
		return qb.query(db, projection, ProviderUtils.addExtraWhere(selection, Locatable.SELECTION_LAT_LON), ProviderUtils.addExtraWhereArgs(selectionArgs, extraArgs), null, null, sortOrder);
	}

	/**
	 * Add this key to the values to tell update() to not mark the data as being dirty. This is useful
	 * for updating local-only information that will not be synchronized and avoid triggering synchronization.
	 */
	public static final String CV_FLAG_DO_NOT_MARK_DIRTY = "_CV_FLAG_DO_NOT_MARK_DIRTY";

	@Override
	public int update(Uri uri, ContentValues values, String where,
			String[] whereArgs) {
		final SQLiteDatabase db = dbHelper.getWritableDatabase();
		int count;
		final long id;
		boolean needSync = false;
		final boolean canSync = canSync(uri);
		if (!values.containsKey(CV_FLAG_DO_NOT_MARK_DIRTY) &&
				canSync && !values.containsKey(JsonSyncableItem._MODIFIED_DATE)){
			values.put(JsonSyncableItem._MODIFIED_DATE, new Date().getTime());
			needSync = true;
		}
		values.remove(CV_FLAG_DO_NOT_MARK_DIRTY);

		final int code = uriMatcher.match(uri);
		switch (code){
		case MATCHER_CAST_DIR:
			count = db.update(CAST_TABLE_NAME, values, where, whereArgs);
			break;
		case MATCHER_CHILD_CAST_ITEM:
		case MATCHER_CAST_ITEM:{
			id = ContentUris.parseId(uri);
			if ( values.size() == 2 && values.containsKey(Favoritable.Columns._FAVORITED)){
				values.put(JsonSyncableItem._MODIFIED_DATE, 0);
			}
			count = db.update(CAST_TABLE_NAME, values,
					Cast._ID+"="+id+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
			break;
		}

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
					ProviderUtils.addExtraWhere(where, 			Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					ProviderUtils.addExtraWhereArgs(whereArgs, 	pathSegs.get(pathSegs.size() - 2), 	pathSegs.get(pathSegs.size() - 3))
					);
			break;
		}

		case MATCHER_CHILD_COMMENT_ITEM:{
			id = ContentUris.parseId(uri);
			final List<String>pathSegs = uri.getPathSegments();

			count = db.update(COMMENT_TABLE_NAME, values,
					ProviderUtils.addExtraWhere(where, 			Comment._ID+"=?", 	Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					ProviderUtils.addExtraWhereArgs(whereArgs, 	String.valueOf(id), pathSegs.get(pathSegs.size() - 3), 	pathSegs.get(pathSegs.size() - 4))
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

		case MATCHER_ITINERARY_DIR:{
			count = db.update(ITINERARY_TABLE_NAME, values, where, whereArgs);
		}break;

		case MATCHER_ITINERARY_ITEM:{
			final String itemId = uri.getLastPathSegment();
			count = db.update(ITINERARY_TABLE_NAME, values,
					ProviderUtils.addExtraWhere(where, Itinerary._ID+"=?"),
					ProviderUtils.addExtraWhereArgs(whereArgs, itemId));
		}break;

			default:
				if (mDBHelperMapper.canUpdate(code)){
					count = mDBHelperMapper.update(code, this, db, uri, values, where, whereArgs);
				}else{
					throw new IllegalArgumentException("unknown URI "+uri);
				}
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

		int count;
		final int code = uriMatcher.match(uri);
		switch (code){
		case MATCHER_CAST_DIR:
			count = db.delete(CAST_TABLE_NAME, where, whereArgs);
			break;

		case MATCHER_CAST_ITEM:
			id = ContentUris.parseId(uri);
			count = db.delete(CAST_TABLE_NAME, Cast._ID + "="+ id +
					(where != null && where.length() > 0 ? " AND (" + where + ")" : ""),
					whereArgs);
			break;

		case MATCHER_COMMENT_DIR:
			count = db.delete(COMMENT_TABLE_NAME, where, whereArgs);
			break;

		case MATCHER_COMMENT_ITEM:{
			id = ContentUris.parseId(uri);
			count = db.delete(COMMENT_TABLE_NAME,
					ProviderUtils.addExtraWhere(where, Comment._ID + "=?"),
					ProviderUtils.addExtraWhereArgs(whereArgs, String.valueOf(id)));
		}break;

		case MATCHER_CHILD_COMMENT_ITEM:{

			final List<String>pathSegs = uri.getPathSegments();
			id = ContentUris.parseId(uri);

			count = db.delete(COMMENT_TABLE_NAME,
					ProviderUtils.addExtraWhere(where, Comment._ID+"=?", 				Comment._PARENT_ID+"=?", 			Comment._PARENT_CLASS+"=?"),
					ProviderUtils.addExtraWhereArgs(whereArgs, Long.toString(id),  	pathSegs.get(pathSegs.size() - 3), 	pathSegs.get(pathSegs.size() - 4)));
		}break;

		case MATCHER_ITEM_TAGS:{

			final List<String> pathSegments = uri.getPathSegments();

			count = db.delete(TAG_TABLE_NAME,
					ProviderUtils.addExtraWhere(where, 			Tag._REF_CLASS + "=?", 						Tag._REF_ID+"=?"),
					ProviderUtils.addExtraWhereArgs(whereArgs, 	pathSegments.get(pathSegments.size() - 3), 	pathSegments.get(pathSegments.size() - 2)));
			break;
		}

		case MATCHER_TAG_DIR:{
			count = db.delete(TAG_TABLE_NAME, where, whereArgs);
			break;
		}

		case MATCHER_ITINERARY_DIR:{
			count = db.delete(ITINERARY_TABLE_NAME, where, whereArgs);
		}break;

		case MATCHER_ITINERARY_ITEM:{
			final String itemId = uri.getLastPathSegment();
			count = db.delete(ITINERARY_TABLE_NAME, ProviderUtils.addExtraWhere(where, Itinerary._ID+"=?"), ProviderUtils.addExtraWhereArgs(whereArgs, itemId));
		}break;

			default:
				if (mDBHelperMapper.canDelete(code)){
					count = mDBHelperMapper.delete(code, this, db, uri, where, whereArgs);
				}else{
					throw new IllegalArgumentException("Unknown URI: "+uri);
				}
		}
		if (!db.inTransaction()){
			db.execSQL("VACUUM");
		}
		getContext().getContentResolver().notifyChange(uri, null, true);
		return count;
	}

	/**
	 * @param cr
	 * @param uri
	 * @return The path that one should post to for the given content item. Should always point to an item, not a dir.
	 */
	public static String getPostPath(Context context, Uri uri){
		return getPublicPath(context, uri, null, true);
	}

	public static String getPublicPath(Context context, Uri uri){
		return getPublicPath(context, uri, null, false);
	}

	/**
	 * Returns a public ID to an item, given the parent URI and a public ID
	 *
	 * @param context
	 * @param uri URI of the parent item
	 * @param publicId public ID of the child item
	 * @return
	 */
	public static String getPublicPath(Context context, Uri uri, Long publicId){
		return getPublicPath(context, uri, publicId, false);
	}

	/**
	 * @param context
	 * @param uri the URI of the item whose field should be queried
	 * @param field the string name of the field
	 * @return
	 */
	private static String getPathFromField(Context context, Uri uri, String field){
		String path = null;
		final String[] generalProjection = {JsonSyncableItem._ID, field};
		final Cursor c = context.getContentResolver().query(uri, generalProjection, null, null, null);
		try{
			if (c.getCount() == 1 && c.moveToFirst()){
				final String storedPath = c.getString(c.getColumnIndex(field));
				if (storedPath != null){
					path = storedPath;
				}
			}else{
				throw new IllegalArgumentException("could not get path from field '"+field+"' in uri "+uri);
			}
		}finally{
			c.close();
		}
		return path;
	}

	public static String getPublicPath(Context context, Uri uri, Long publicId, boolean parent){
		String path;

		final int match = uriMatcher.match(uri);

		// first check to see if the path is stored already.
		switch (match){

		// these should be the only hard-coded paths in the system.

		case MATCHER_EVENT_DIR:
			path = internalToPublicQueryMap(context, Event.SERVER_PATH, uri);
			break;

		case MATCHER_CAST_DIR:{
			path = internalToPublicQueryMap(context, Cast.SERVER_PATH, uri);

		}break;

		case MATCHER_ITINERARY_DIR:{
			path = Itinerary.SERVER_PATH;
		}break;

		case MATCHER_COMMENT_DIR:
			path = Comment.SERVER_PATH;
			break;

		case MATCHER_CHILD_COMMENT_DIR:
			path = getPathFromField(context, ProviderUtils.removeLastPathSegment(uri), Commentable.Columns._COMMENT_DIR_URI);
			break;

		case MATCHER_CAST_ITEM:
		case MATCHER_CHILD_COMMENT_ITEM:
		case MATCHER_CHILD_CAST_ITEM:
		case MATCHER_COMMENT_ITEM:
		{
			if (parent || publicId != null){
				path = getPublicPath(context, ProviderUtils.removeLastPathSegment(uri));
			}else{
				path = getPathFromField(context, uri, JsonSyncableItem._PUBLIC_URI);
			}

			}break;

		case MATCHER_CHILD_CAST_DIR:
			path = getPathFromField(context, ProviderUtils.removeLastPathSegment(uri), Itinerary._CASTS_URI);
			break;

		case MATCHER_ITINERARY_BY_TAGS:{
			final Set<String> tags = TaggableItem.removePrefixesFromTags(Tag.toSet(uri.getQuery()));

			path = getPublicPath(context, ProviderUtils.removeLastPathSegment(uri)) + "?tags=" + ListUtils.join(tags, ",");
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

		return path;
	}

	private static String internalToPublicQueryMap(Context context, String serverPath, Uri uri){
		final String tags = uri.getQueryParameter(TaggableItem.SERVER_QUERY_PARAMETER);
		final String dist = uri.getQueryParameter(Locatable.SERVER_QUERY_PARAMETER);
		final Boolean favorite = Favoritable.decodeFavoritedUri(uri);
		String query = null;

		// TODO figure out a better way to do this without needing to hard-code this logic.
		if (tags != null){
			final Set<String> tagSet = TaggableItem.removePrefixesFromTags(Tag.toSet(tags));
			query = TaggableItem.SERVER_QUERY_PARAMETER+"=" + ListUtils.join(tagSet, ",");
		}

		if (dist != null){
			if (query != null){
				query += "&";
			}else{
				query = "";
			}
			query += Locatable.SERVER_QUERY_PARAMETER+"=" + dist;
		}

		if (favorite != null){
			final Account[] accounts = Authenticator.getAccounts(context);
			if (accounts.length > 0){
				final String id = AccountManager.get(context).getUserData(accounts[0], AuthenticationService.USERDATA_USERID);
				if(id != null){
					if (query != null){
						query += "&";
					}else{
						query = "";
					}
					query += "favorited_by=" + id;
				}
			}
		}

		return serverPath + (query != null ? "?"+query : "");
	}


	public static UriMatcher getUriMatcher(){
		return uriMatcher;
	}

	static {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI(AUTHORITY, Cast.PATH, MATCHER_CAST_DIR);
		uriMatcher.addURI(AUTHORITY, Cast.PATH+"/#", MATCHER_CAST_ITEM);

		// /cast/1/media
		uriMatcher.addURI(AUTHORITY, Cast.PATH+"/#/"+CastMedia.PATH, MATCHER_CHILD_CASTMEDIA_DIR);
		uriMatcher.addURI(AUTHORITY, Cast.PATH+"/#/"+CastMedia.PATH+"/#", MATCHER_CHILD_CASTMEDIA_ITEM);

		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/" + Cast.PATH + "/#/" + CastMedia.PATH, MATCHER_CHILD_CASTMEDIA_DIR);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/" + Cast.PATH + "/#/" + CastMedia.PATH + "/#/", MATCHER_CHILD_CASTMEDIA_ITEM);

		// /comments/1, etc.
		uriMatcher.addURI(AUTHORITY, Comment.PATH, MATCHER_COMMENT_DIR);
		uriMatcher.addURI(AUTHORITY, Comment.PATH + "/#", MATCHER_COMMENT_ITEM);

		// project/1/comments, etc.
		uriMatcher.addURI(AUTHORITY, Cast.PATH + "/#/" + Comment.PATH, MATCHER_CHILD_COMMENT_DIR);
		uriMatcher.addURI(AUTHORITY, Cast.PATH + "/#/" + Comment.PATH + "/#", MATCHER_CHILD_COMMENT_ITEM);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/" + Comment.PATH, MATCHER_CHILD_COMMENT_DIR);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/" + Comment.PATH + "/#", MATCHER_CHILD_COMMENT_ITEM);

		// /event
		uriMatcher.addURI(AUTHORITY, Event.PATH, MATCHER_EVENT_DIR);
		uriMatcher.addURI(AUTHORITY, Event.PATH + "/#", MATCHER_EVENT_ITEM);

		// /content/1/tags
		uriMatcher.addURI(AUTHORITY, Cast.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);
		uriMatcher.addURI(AUTHORITY, Event.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/"+Cast.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);

		// /content/tags?tag1,tag2
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH +'/'+Tag.PATH, MATCHER_ITINERARY_BY_TAGS);

		// tag list
		uriMatcher.addURI(AUTHORITY, Tag.PATH, MATCHER_TAG_DIR);

		// Itineraries
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH, 							MATCHER_ITINERARY_DIR);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#", 					MATCHER_ITINERARY_ITEM);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/" + Cast.PATH,		MATCHER_CHILD_CAST_DIR);
		uriMatcher.addURI(AUTHORITY, Itinerary.PATH + "/#/" + Cast.PATH + "/#", MATCHER_CHILD_CAST_ITEM);

		mDBHelperMapper.addDirMapping(MATCHER_CHILD_CAST_DIR, ITINERARY_CASTS_DBHELPER, DBHelperMapper.TYPE_ALL);
		mDBHelperMapper.addItemMapping(MATCHER_CHILD_CAST_ITEM, ITINERARY_CASTS_DBHELPER, DBHelperMapper.TYPE_ALL);

		mDBHelperMapper.addDirMapping(MATCHER_CHILD_CASTMEDIA_DIR, CASTS_CASTMEDIA_DBHELPER, DBHelperMapper.TYPE_ALL);
		mDBHelperMapper.addItemMapping(MATCHER_CHILD_CASTMEDIA_ITEM, CASTS_CASTMEDIA_DBHELPER, DBHelperMapper.TYPE_ALL);

		mDBHelperMapper.addDirMapping(MATCHER_EVENT_DIR, EVENT_DBHELPER, DBHelperMapper.TYPE_ALL);
		mDBHelperMapper.addItemMapping(MATCHER_EVENT_ITEM, EVENT_DBHELPER, DBHelperMapper.TYPE_ALL);
	}
}
