 package edu.mit.mel.locast.mobile.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

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
import edu.mit.mel.locast.mobile.ListUtils;

public class MediaProvider extends ContentProvider {
	public final static String AUTHORITY = "edu.mit.mel.locast.mobile.provider";
	
	public final static String 
		TYPE_CONTENT_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mel.locast.mobile.content",
		TYPE_CONTENT_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mel.locast.mobile.content",
		
		TYPE_PROJECT_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mel.locast.mobile.projects",
		TYPE_PROJECT_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mel.locast.mobile.projects",

		TYPE_COMMENT_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mel.locast.mobile.comments",
		TYPE_COMMENT_DIR  = "vnd.android.cursor.dir/vnd.edu.mit.mel.locast.mobile.comments",
		
		// XXX needed? TYPE_TAG_ITEM     = "vnd.android.cursor.item/vnd.edu.mit.mel.locast.mobile.tags",
		TYPE_TAG_DIR      = "vnd.android.cursor.dir/vnd.edu.mit.mel.locast.mobile.tags";

	private static final String 
		CONTENT_TABLE_NAME = "content",
		PROJECT_TABLE_NAME = "projects",
		COMMENT_TABLE_NAME = "comments",
		TAG_TABLE_NAME     = "tags";
	
	private static UriMatcher uriMatcher;
	
	private static final int MATCHER_CONTENT_DIR  = 1,
	 						 MATCHER_CONTENT_ITEM = 2,
	 						 MATCHER_PROJECT_DIR  = 3,
	 						 MATCHER_PROJECT_ITEM = 4,
	 						 MATCHER_COMMENT_DIR  = 5,
	 						 MATCHER_COMMENT_ITEM = 6,
	 						 MATCHER_PROJECT_CONTENT_DIR  = 7,
	 						 MATCHER_PROJECT_CONTENT_ITEM = 8,
	 						 MATCHER_CHILD_COMMENT_DIR    = 9,
	 						 MATCHER_CHILD_COMMENT_ITEM   = 10,
	 						 MATCHER_PROJECT_BY_TAGS      = 11,
	 						 MATCHER_CONTENT_BY_TAGS      = 12,
	 						 MATCHER_TAG_DIR              = 13,
	 						 MATCHER_ITEM_TAGS    		  = 14;

	private static class DatabaseHelper extends SQLiteOpenHelper {
		private static final String DB_NAME = "content.db";
		private static final int DB_VER = 15;
		
		public DatabaseHelper(Context context) {
			super(context, DB_NAME, null, DB_VER);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + CONTENT_TABLE_NAME + " ("
					+ Cast._ID 			+ " INTEGER PRIMARY KEY,"
					+ Cast.PUBLIC_ID 	+ " INTEGER,"
					+ Cast.TITLE 		+ " TEXT,"
					+ Cast.AUTHOR 		+ " TEXT,"
					+ Cast.DESCRIPTION 	+ " TEXT,"
					+ Cast.PUBLIC_URI 	+ " TEXT,"
					+ Cast.LOCAL_URI 	+ " TEXT,"
					+ Cast.CONTENT_TYPE + " TEXT,"
					+ Cast.MODIFIED_DATE+ " INTEGER,"
					+ Cast.CREATED_DATE + " INTEGER,"
					+ Cast.PRIVACY 		+ " TEXT,"
					+ Cast.LATITUDE 	+ " REAL,"
					+ Cast.LONGITUDE 	+ " REAL,"
					+ Cast.THUMBNAIL_URI+ " TEXT,"
					+ Cast.THUMBNAIL 	+ " BINARY"
					+ ");"
					);
			db.execSQL("CREATE TABLE " + PROJECT_TABLE_NAME + " ("
					+ Project._ID 			+ " INTEGER PRIMARY KEY,"
					+ Project.PUBLIC_ID 	+ " INTEGER,"
					+ Project.TITLE 		+ " TEXT,"
					+ Project.AUTHOR		+ " TEXT,"
					+ Project.DESCRIPTION 	+ " TEXT,"
					+ Project.CASTS  		+ " TEXT,"
					+ Project.CASTS_EXTERNAL+ " TEXT,"
					+ Project.MEMBERS 		+ " TEXT,"
					+ Project.PRIVACY 		+ " TEXT,"
					+ Project.START_DATE    + " INTEGER,"
					+ Project.END_DATE      + " INTEGER,"
					+ Project.MODIFIED_DATE + " INTEGER"
					+ ");"		
			);
			db.execSQL("CREATE TABLE " + COMMENT_TABLE_NAME + " ("
					+ Comment._ID 			+ " INTEGER PRIMARY KEY,"
					+ Comment.PUBLIC_ID 	+ " INTEGER,"
					+ Comment.AUTHOR		+ " TEXT,"
					+ Comment.AUTHOR_ICON	+ " TEXT,"
					+ Comment.MODIFIED_DATE + " INTEGER,"
					+ Comment.PARENT_ID     + " INTEGER,"
					+ Comment.PARENT_CLASS  + " TEXT,"
					+ Comment.COMMENT_NUMBER+ " TEXT,"
					+ Comment.DESCRIPTION 	+ " TEXT"
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
			
			
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + CONTENT_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + PROJECT_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + COMMENT_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + TAG_TABLE_NAME);
			onCreate(db);
		}

	}

	private DatabaseHelper dbHelper;

	@Override
	public boolean onCreate() {
		dbHelper = new DatabaseHelper(getContext());
		return true;
	}
	
	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		final SQLiteDatabase db = dbHelper.getWritableDatabase();
		final long id;
		
		switch (uriMatcher.match(uri)){
		case MATCHER_CONTENT_DIR:
			db.delete(CONTENT_TABLE_NAME, where, whereArgs);
			break;
			
		case MATCHER_CONTENT_ITEM:
			id = ContentUris.parseId(uri);
			db.delete(CONTENT_TABLE_NAME, Cast._ID + "="+ id + 
					(where != null && where.length() > 0 ? " AND (" + where + ")" : ""),
					whereArgs);
			break;
			
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
			
		case MATCHER_COMMENT_ITEM:
			id = ContentUris.parseId(uri);
			db.delete(COMMENT_TABLE_NAME, Comment._ID + "="+ id + 
					(where != null && where.length() > 0 ? " AND (" + where + ")" : ""),
					whereArgs);
			break;
			
		case MATCHER_ITEM_TAGS:{
			final List<String> pathSegments = uri.getPathSegments();
			final String where2 = Tag._REF_CLASS+"=\""+pathSegments.get(0) + "\" AND " +
			Tag._REF_ID+"="+pathSegments.get(1) + 
			(where != null && where.length() > 0 ? " AND (" + where + ")" : "");
			
			db.delete(TAG_TABLE_NAME, where2, whereArgs);
			break;
			
		}
		
		case MATCHER_TAG_DIR:{
			db.delete(TAG_TABLE_NAME, where, whereArgs);
			break;
		}
			
			default:
				throw new IllegalArgumentException("Unknown URI: "+uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return 0;
	}

	public boolean canSync(Uri uri){
		switch (uriMatcher.match(uri)){
		case MATCHER_ITEM_TAGS:
		case MATCHER_TAG_DIR:
			return false;
			
		case MATCHER_CHILD_COMMENT_DIR:
		case MATCHER_CHILD_COMMENT_ITEM:
		case MATCHER_COMMENT_DIR:
		case MATCHER_COMMENT_ITEM:
			
		case MATCHER_CONTENT_BY_TAGS:
		case MATCHER_CONTENT_DIR:
		case MATCHER_CONTENT_ITEM:
			
		case MATCHER_PROJECT_BY_TAGS:
		case MATCHER_PROJECT_CONTENT_DIR:
		case MATCHER_PROJECT_CONTENT_ITEM:
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
		case MATCHER_CONTENT_DIR:
		case MATCHER_PROJECT_CONTENT_DIR:
			return TYPE_CONTENT_DIR;
			
		case MATCHER_CONTENT_ITEM:
		case MATCHER_PROJECT_CONTENT_ITEM:
			return TYPE_CONTENT_ITEM;
			
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
			
		case MATCHER_CONTENT_BY_TAGS:
			return TYPE_CONTENT_DIR;
			
		case MATCHER_PROJECT_BY_TAGS:
			return TYPE_PROJECT_DIR;
			
		default:
			throw new IllegalArgumentException("Cannot get type for URI "+uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		final SQLiteDatabase db = dbHelper.getWritableDatabase();
		
		long rowid;
		final boolean syncable = canSync(uri);
		if (syncable && !values.containsKey(Project.MODIFIED_DATE)){
			values.put(Project.MODIFIED_DATE, new Date().getTime());
		}
		values.remove(JsonSyncableItem._ID);
		
		Uri newItem = null;
		
		switch (uriMatcher.match(uri)){
		case MATCHER_CONTENT_DIR:{
			final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);
			
			rowid = db.insert(CONTENT_TABLE_NAME, null, values);
			if (rowid > 0){
				getContext().getContentResolver().notifyChange(uri, null);
				
				newItem = ContentUris.withAppendedId(Cast.CONTENT_URI, rowid);
				
				update(Uri.withAppendedPath(newItem, Tag.PATH), cvTags, null, null);
			}
			break;
		}
		case MATCHER_PROJECT_DIR:{
			final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);
			rowid = db.insert(PROJECT_TABLE_NAME, null, values);
			if (rowid > 0){
				getContext().getContentResolver().notifyChange(uri, null);
				newItem = ContentUris.withAppendedId(Project.CONTENT_URI, rowid);
				update(Uri.withAppendedPath(newItem, Tag.PATH), cvTags, null, null);
			}
			break;
		}
		case MATCHER_COMMENT_DIR:
			rowid = db.insert(COMMENT_TABLE_NAME, null, values);
			if (rowid > 0){
				getContext().getContentResolver().notifyChange(uri, null);
				newItem = ContentUris.withAppendedId(Comment.CONTENT_URI, rowid);
			}
			break;
			
		case MATCHER_CHILD_COMMENT_DIR:
			values.put(Comment.PARENT_CLASS, uri.getPathSegments().get(0));
			values.put(Comment.PARENT_ID, uri.getPathSegments().get(1));
			rowid = db.insert(COMMENT_TABLE_NAME, null, values);
			if (rowid > 0){
				getContext().getContentResolver().notifyChange(uri, null);
				newItem = ContentUris.withAppendedId(uri, rowid);
			}
			break;
			
		case MATCHER_ITEM_TAGS:{
			
			values.put(Tag._REF_CLASS, uri.getPathSegments().get(0));
			values.put(Tag._REF_ID, uri.getPathSegments().get(1));
			
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
						
		default:
			throw new IllegalArgumentException("Unknown URI: "+uri);
		}
		if (newItem == null){
			throw new SQLException("Failed to insert row into "+uri);
		}
		
		if (syncable){
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
		case MATCHER_CONTENT_DIR:
			qb.setTables(CONTENT_TABLE_NAME);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;

		case MATCHER_CONTENT_ITEM:
			qb.setTables(CONTENT_TABLE_NAME);
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
			qb.setTables(COMMENT_TABLE_NAME);
			final String projectId = uri.getPathSegments().get(1);
			qb.appendWhere(Comment.PARENT_ID + "="+projectId);
			qb.appendWhere(" AND " + Comment.PARENT_CLASS+"='"+uri.getPathSegments().get(0)+"'");
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}

		case MATCHER_CHILD_COMMENT_ITEM:{
			qb.setTables(COMMENT_TABLE_NAME);
			final String projectId = uri.getPathSegments().get(1);
			qb.appendWhere(Comment.PARENT_ID + "="+projectId);
			qb.appendWhere(" AND " + Comment.PARENT_CLASS+"='"+uri.getPathSegments().get(0)+"'");
			
			id = ContentUris.parseId(uri);
			qb.appendWhere(" AND " + Comment._ID + "="+id);
			
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}
			
		case MATCHER_PROJECT_CONTENT_DIR:{
			final String projectId = uri.getPathSegments().get(1);
			final String[] projProjection = {Project._ID, Project.CASTS};
			final SQLiteQueryBuilder qb2 = new SQLiteQueryBuilder();
			c = qb2.query(db, projProjection, null, null, null, null, sortOrder);
			
			qb2.setTables(PROJECT_TABLE_NAME);
			
			qb2.appendWhere(Project._ID + "="+projectId);
			id = ContentUris.parseId(uri);
			qb.appendWhere(Cast._ID + "="+id);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}

		case MATCHER_PROJECT_CONTENT_ITEM:
			qb.setTables(CONTENT_TABLE_NAME);
			id = ContentUris.parseId(uri);
			qb.appendWhere(Cast._ID + "="+id);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
			
		case MATCHER_ITEM_TAGS:{
			qb.setTables(TAG_TABLE_NAME);
			final List<String> pathSegments = uri.getPathSegments();
			qb.appendWhere(Tag._REF_CLASS+"=\""+pathSegments.get(0)+"\"");
			qb.appendWhere(" AND " + Tag._REF_ID+"="+pathSegments.get(1));
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}
		
		case MATCHER_TAG_DIR:{
			qb.setTables(TAG_TABLE_NAME);
			c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;
		}
		
		case MATCHER_CONTENT_BY_TAGS:
			taggableItemTable = CONTENT_TABLE_NAME;
			
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
		if (canSync && !values.containsKey(JsonSyncableItem.MODIFIED_DATE)){
			values.put(JsonSyncableItem.MODIFIED_DATE, new Date().getTime());
			needSync = true;
		}
		
		switch (uriMatcher.match(uri)){
		case MATCHER_CONTENT_DIR:
			count = db.update(CONTENT_TABLE_NAME, values, where, whereArgs);
			break;
		case MATCHER_CONTENT_ITEM:{
			id = ContentUris.parseId(uri);
			final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);
			count = db.update(CONTENT_TABLE_NAME, values, 
					Cast._ID+"="+id+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
			update(Uri.withAppendedPath(uri, Tag.PATH), cvTags, null, null);
			
			break;
		}
		
		case MATCHER_PROJECT_DIR:
			count = db.update(PROJECT_TABLE_NAME, values, where, whereArgs);
			break;
		case MATCHER_PROJECT_ITEM:{
			id = ContentUris.parseId(uri);
			final ContentValues cvTags = extractContentValueItem(values, Tag.PATH);
			count = db.update(PROJECT_TABLE_NAME, values, 
					Project._ID+"="+id+ (where != null && where.length() > 0 ? " AND ("+where+")":""),
					whereArgs);
			update(Uri.withAppendedPath(uri, Tag.PATH), cvTags, null, null);
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
			final String where2 = Comment.PARENT_CLASS + "=?" 
				+ " AND "+ Comment.PARENT_ID + "=?"
				+ (where != null && where.length() > 0 ? " AND ("+where+")":"");
			final List<String>whereArgsList = (whereArgs == null) ? new Vector<String>() : Arrays.asList(whereArgs);
			whereArgsList.add(uri.getPathSegments().get(0));
			whereArgsList.add(uri.getPathSegments().get(1));
			count = db.update(COMMENT_TABLE_NAME, values, where2, whereArgsList.toArray(new String[]{}));
			break;
		}
		
		case MATCHER_CHILD_COMMENT_ITEM:{
			id = ContentUris.parseId(uri);
			final String where2 = Comment._ID + "=? AND " 
				+ Comment.PARENT_CLASS + "=?" 
				+ " AND "+ Comment.PARENT_ID+"=?"
				+ (where != null && where.length() > 0 ? " AND ("+where+")":"");
			
			final List<String>whereArgsList = (whereArgs == null) ? new Vector<String>() : Arrays.asList(whereArgs);
			whereArgsList.add(String.valueOf(id));
			whereArgsList.add(uri.getPathSegments().get(0));
			whereArgsList.add(uri.getPathSegments().get(1));
			
			count = db.update(COMMENT_TABLE_NAME, values, where2,  whereArgsList.toArray(new String[]{}));
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
	
	public static String getPublicPath(ContentResolver cr, Uri uri){
		return getPublicPath(cr, uri, null);
	}
	
	public static String getPublicPath(ContentResolver cr, Uri uri, Long publicId){
		final String[] projection = {JsonSyncableItem._ID, JsonSyncableItem.PUBLIC_ID};
		Cursor c;
		String path = null;
		final int match = uriMatcher.match(uri);
		switch (match){
		case MATCHER_CONTENT_ITEM:
		case MATCHER_PROJECT_ITEM:
			c = cr.query(uri, projection, null, null, null);
			if (c.moveToFirst()){
				if (match == MATCHER_PROJECT_ITEM){
					path = Project.SERVER_PATH;
				}else if(match == MATCHER_CONTENT_ITEM){
					path = Cast.SERVER_PATH;
				}
				
				if (!c.isNull(c.getColumnIndex(JsonSyncableItem.PUBLIC_ID))){
					path += c.getLong(c.getColumnIndex(JsonSyncableItem.PUBLIC_ID)) + "/";
				}else if (publicId != null && publicId > 0){
					path += publicId + "/";
				}else {
					throw new RuntimeException("Asked for public path of "+uri+", but it has no public ID");
				}
			}
			c.close();
			break;
			
		case MATCHER_CONTENT_DIR:
			path = Cast.SERVER_PATH;
			if (publicId != null){
				path += publicId;
			}
			break;
			
		case MATCHER_PROJECT_DIR:
			path = Project.SERVER_PATH;
			if (publicId != null){
				path += publicId;
			}
			break;
			
		case MATCHER_CHILD_COMMENT_DIR:
			return getPublicPath(cr, removeLastPathSegment(uri)) + Comment.SERVER_PATH;
			
		}
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
	private static Uri removeLastPathSegment(Uri uri){
		final List<String> pathWithoutLast = new Vector<String>(uri.getPathSegments());
		pathWithoutLast.remove(pathWithoutLast.size() - 1);
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
	
	static {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI(AUTHORITY, Cast.PATH, MATCHER_CONTENT_DIR);
		uriMatcher.addURI(AUTHORITY, Cast.PATH+"/#", MATCHER_CONTENT_ITEM);
		
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
		
		// /project/1/content, etc
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH, MATCHER_PROJECT_CONTENT_DIR);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/" + Cast.PATH + "/#", MATCHER_PROJECT_CONTENT_ITEM);
		
		// /content/1/tags
		uriMatcher.addURI(AUTHORITY, Cast.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);
		uriMatcher.addURI(AUTHORITY, Project.PATH + "/#/"+Tag.PATH, MATCHER_ITEM_TAGS);
		
		// /content/tags/tag1,tag2
		uriMatcher.addURI(AUTHORITY, Cast.PATH +'/'+ Tag.PATH + "/*", MATCHER_CONTENT_BY_TAGS);
		uriMatcher.addURI(AUTHORITY, Project.PATH +'/'+Tag.PATH + "/*", MATCHER_PROJECT_BY_TAGS);
		
		// tag list
		uriMatcher.addURI(AUTHORITY, Tag.PATH, MATCHER_TAG_DIR);
	}
}
