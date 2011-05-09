package edu.mit.mobile.android.locast.data;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import edu.mit.mobile.android.locast.data.ManyToMany.DBHelper.IdenticalChildFinder;

/**
 * Database helper to make it easier to create many-to-many relationships between two arbitrary tables.
 *
 * <pre>
 *     relation
 *        ↓
 * [from] → [to]
 *        → [to 2]
 * </pre>
 *
 * For example, you could have an Itinerary that has a relation to multiple Casts.
 *
 * To use, first {@link ManyToMany#createJoinTable(SQLiteDatabase)}. Then you can create relations between between tables by {@link #addRelation(SQLiteDatabase, long, long)}.
 *
 * @author steve
 *
 */
public class ManyToMany {
	public static class ManyToManyColumns implements BaseColumns {

		public static final String
			TO_ID = "to_id",
			FROM_ID = "from_id";
	}

	/**
	 * A helper class that will do handle standard CRUD queries for URIs that represent relationships between a from and to. For example, it expects that the URIs passed to it be in the form of <code>/parent/1/child</code> or <code>/parent/1/child/2</code> where 1 is the ID of the parent and 2 is the ID of the child.
	 *
	 * The easiest way to use this is to have it be a static object of the {@link ContentProvider} where the provider defaults to calling the CRUD methods on the helper if no other matches are found.
	 *
	 * @author steve
	 *
	 */
	public static final class DBHelperMapper {
		private final Map<Integer, DBHelperMapItem> mDbhMap = new HashMap<Integer, DBHelperMapItem>();

		/**
		 * Makes a mapping from the code to the given DBHelper. This helper will be used to handle any queries for items that match the given code. All other items will throw an error. Check {@link #canHandle(int)} and {@link #canQuery(int)}, etc. first to ensure that a query will complete.
		 *
		 * @param code A unique ID representing the given URI; usually a {@link UriMatcher} code
		 * @param helper The helper that should be used for this code.
		 * @param type The type of requests that should be handled by the helper. Any other requests will throw an error. Types can be joined together, eg. <code>TYPE_INSERT | TYPE_QUERY</code>
		 */
		public void addDirMapping(int code, DBHelper helper, int type){
			mDbhMap.put(code, new DBHelperMapItem(type, false, helper));
		}

		public void addItemMapping(int code, DBHelper helper, int type){
			mDbhMap.put(code, new DBHelperMapItem(type, true, helper));
		}

		public boolean canHandle(int code){
			return mDbhMap.containsKey(code);
		}

		public boolean canInsert(int code){
			final DBHelperMapItem item = mDbhMap.get(code);
			return item != null && item.allowType(TYPE_INSERT);
		}

		public boolean canQuery(int code){
			final DBHelperMapItem item = mDbhMap.get(code);
			return item != null && item.allowType(TYPE_QUERY);
		}

		public boolean canUpdate(int code){
			final DBHelperMapItem item = mDbhMap.get(code);
			return item != null && item.allowType(TYPE_UPDATE);
		}

		public boolean canDelete(int code){
			final DBHelperMapItem item = mDbhMap.get(code);
			return item != null && item.allowType(TYPE_DELETE);
		}

		private String getType(int type){
			String typeString = null;
			if      ((type & TYPE_INSERT) != 0){
				typeString = "insert";

			}else if ((type & TYPE_QUERY) != 0){
				typeString = "query";

			}else if ((type & TYPE_UPDATE) != 0){
				typeString = "update";

			}else if ((type & TYPE_DELETE) != 0){
				typeString = "delete";
			}
			return typeString;
		}

		private DBHelperMapItem getMap(int type, int code){
			final DBHelperMapItem dbhmi = mDbhMap.get(code);

			if (dbhmi == null){
				throw new IllegalArgumentException("No mapping for code "+ code);
			}
			if ((dbhmi.type & type) == 0){
				throw new IllegalArgumentException("Cannot "+getType(type)+" for code " + code);
			}
			return dbhmi;
		}

		public Uri insert(int code, ContentProvider provider, SQLiteDatabase db, Uri uri, ContentValues values){
			final DBHelperMapItem dbhmi = getMap(TYPE_INSERT, code);

			return dbhmi.dbHelper.insertWithRelation(db, provider, uri, values, new JSONSyncableIdenticalChildFinder());
		}

		public Cursor query(int code, ContentProvider provider, SQLiteDatabase db, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder){
			final DBHelperMapItem dbhmi = getMap(TYPE_QUERY, code);

			return dbhmi.dbHelper.query(db, dbhmi.isItem, uri, projection, selection, selectionArgs, sortOrder);
		}

		public int update(int code, ContentProvider provider, SQLiteDatabase db, Uri uri, ContentValues cv, String selection, String[] selectionArgs){
			final DBHelperMapItem dbhmi = getMap(TYPE_QUERY, code);

			if (dbhmi.isItem){
				return dbhmi.dbHelper.updateItem(db, provider, uri, cv, selection, selectionArgs);
			}else{
				return dbhmi.dbHelper.updateDir(db, provider, uri, cv, selection, selectionArgs);
			}
		}

		public int delete(int code, ContentProvider provider, SQLiteDatabase db, Uri uri, String selection, String[] selectionArgs){
			final DBHelperMapItem dbhmi = getMap(TYPE_QUERY, code);

			if (dbhmi.isItem){
				return dbhmi.dbHelper.deleteItem(db, provider,uri, selection, selectionArgs);
			}else{
				return dbhmi.dbHelper.deleteDir(db, provider, uri, selection, selectionArgs);
			}
		}

		private class DBHelperMapItem {
			public DBHelperMapItem(int type, boolean isItem, DBHelper dbHelper) {
				this.type = type;
				this.dbHelper = dbHelper;
				this.isItem = isItem;
			}

			public boolean allowType(int type){
				return (this.type & type) != 0;
			}

			final DBHelper dbHelper;
			final int type;
			final boolean isItem;

		}

		public static final int
			TYPE_INSERT = 1,
			TYPE_QUERY  = 2,
			TYPE_UPDATE = 4,
			TYPE_DELETE = 8,
			TYPE_ALL = TYPE_INSERT | TYPE_QUERY | TYPE_UPDATE | TYPE_DELETE;
	}

	public static final class DBHelper {
		private final String mFromTable, mToTable, mJoinTable;
		private final Uri mToContentUri;

		/**
		 * @param fromTable
		 * @param toTable
		 * @param toContentUri will make calls to the content provider to do updates using this URI
		 */
		public DBHelper(String fromTable, String toTable, Uri toContentUri) {
			mFromTable = fromTable;
			mToTable = toTable;
			mJoinTable = mFromTable + "_" + mToTable;

			mToContentUri = toContentUri;

		}

		/**
		 * Provides a bunch of CRUD routines for manipulating items and their relationship to one another.
		 *
		 * @param fromTable
		 * @param toTable
		 */
		public DBHelper(String fromTable, String toTable) {
			mFromTable = fromTable;
			mToTable = toTable;
			mJoinTable = mFromTable + "_" + mToTable;

			mToContentUri = null;

		}

		public String getJoinTableName(){
			return mJoinTable;
		}

		public String getFromTable(){
			return mFromTable;
		}

		public String getToTable() {
			return mToTable;
		}

		/**
		 * Generates a join table.
		 *
		 * @return
		 */
		public void createJoinTable(SQLiteDatabase db){
			db.execSQL("CREATE TABLE "+mJoinTable + " ("
			+ ManyToManyColumns._ID 			+ " INTEGER PRIMARY KEY,"
			// TODO foreign keys are not supported in 2.1 or below
			//+ "cast_id REFERENCES "+CAST_TABLE_NAME +  	"("+Cast._ID+")"
			+ ManyToManyColumns.TO_ID   		+ " INTEGER,"
			+ ManyToManyColumns.FROM_ID 		+ " INTEGER"
			+ ");");
		}

		/**
		 * Deletes the join table.
		 *
		 * @param db
		 */
		public void deleteJoinTable(SQLiteDatabase db){
			db.execSQL("DROP TABLE IF EXISTS "+mJoinTable);
		}

		/**
		 * Creates a link from `from' to `to'.
		 *
		 * @param db database that has the many-to-many table
		 * @param from ID of the item in the FROM table
		 * @param to ID of the item in the TO table
		 * @return ID of the newly created relation
		 */
		public long addRelation(SQLiteDatabase db, long from, long to){
			final ContentValues relation = new ContentValues();
			// make a many-to-many relation
			relation.put(ManyToManyColumns.FROM_ID, from);
			relation.put(ManyToManyColumns.TO_ID, to);
			return db.insert(mJoinTable, null, relation);
		}

		public int removeRelation(SQLiteDatabase db, long from, long to){
			return db.delete(mJoinTable,
					ManyToManyColumns.TO_ID + "=? AND " + ManyToManyColumns.FROM_ID + "=?",
					new String[]{Long.toString(to),					Long.toString(from)});
		}

		/**
		 * Inserts a child into the database and adds a relation to its parent. If the item described by values is already present, only adds the relation.
		 *
		 * @param db
		 * @param uri URI to insert into. This must be a be a hierarchical URI that points to the directory of the desired parent's children. Eg. "/itinerary/1/casts/"
		 * @param values values for the child
		 * @param childFinder a finder that will look for
		 * @return the URI of the child that was either related or inserted.
		 */
		public Uri insertWithRelation(SQLiteDatabase db, ContentProvider provider, Uri parentChildDir, ContentValues values, IdenticalChildFinder childFinder) {
			final Uri parent = MediaProvider.removeLastPathSegment(parentChildDir);

			final long parentId = ContentUris.parseId(parent);
			Uri newItem;

			db.beginTransaction();
			try {

				if (childFinder != null){
					newItem = childFinder.getIdenticalChild(this, parentChildDir, db, mToTable, values);
				}else{
					newItem = null;
				}

				long childId = -1;
				if (newItem == null){
					if (mToContentUri != null){
						newItem = provider.insert(mToContentUri, values);
						childId = ContentUris.parseId(newItem);
					}else{
						childId = db.insert(mToTable, null, values);
						if (childId != -1){
							newItem = ContentUris.withAppendedId(parentChildDir, childId);
						}
					}
				}
				if (newItem != null && childId != -1){
					addRelation(db, parentId, childId);
				}

				db.setTransactionSuccessful();
			}finally{
				db.endTransaction();
			}
			return newItem;
		}

		/**
		 * Updates the item in the "to" table whose URI is specified.
		 *
		 * XXX Does not verify that there's actually a relationship between from and to.
		 *
		 * @param db
		 * @param provider
		 * @param uri the URI of the child. Child uri must end in its ID
		 * @param values
		 * @param where
		 * @param whereArgs
		 * @return
		 */
		public int updateItem(SQLiteDatabase db, ContentProvider provider, Uri uri, ContentValues values, String where, String[] whereArgs){
			int count;
			if (mToContentUri != null){
				count = provider.update(ContentUris.withAppendedId(mToContentUri, ContentUris.parseId(uri)), values, where, whereArgs);
			}else{
				count = db.update(mToTable, values, MediaProvider.addExtraWhere(where, JsonSyncableItem._ID+"=?"), MediaProvider.addExtraWhereArgs(whereArgs, uri.getLastPathSegment()));
			}

			return count;
		}

		// TODO does not yet verify a relationship.
		public int updateDir(SQLiteDatabase db, ContentProvider provider, Uri uri, ContentValues values, String where, String[] whereArgs){
			throw new RuntimeException("not implemented yet");
			//return provider.update(mToContentUri, values, where, whereArgs);
		}

		public int deleteItem(SQLiteDatabase db, ContentProvider provider, Uri uri,String where, String[] whereArgs){
			int count;
			try {
				db.beginTransaction();
				final long childId = ContentUris.parseId(uri);
				final Uri parent = MediaProvider.removeLastPathSegments(uri, 2);

				if (mToContentUri != null){
					count = provider.delete(ContentUris.withAppendedId(mToContentUri, childId), where, whereArgs);
				}else{
					count = db.delete(mToTable, MediaProvider.addExtraWhere(where, JsonSyncableItem._ID+"=?"), MediaProvider.addExtraWhereArgs(whereArgs, String.valueOf(childId)));
				}

				final int rows = removeRelation(db, ContentUris.parseId(parent), childId);

				if (rows == 0){
					throw new IllegalArgumentException("There is no relation between "+ parent + " and " + mToTable + ": ID "+ childId);
				}

			}finally{
				db.endTransaction();
			}
			return count;
		}

		// TODO implement me!
		public int deleteDir(SQLiteDatabase db, ContentProvider provider, Uri uri,String where, String[] whereArgs){
			throw new RuntimeException("not implemented yet");
			//return db.delete(mToTable, where, whereArgs);
		}


		public interface IdenticalChildFinder {
			/**
			 * Search the database and see if there is a child that is identical (using whatever criteria you prefer) to the one described in values.
			 *
			 * @param m2m the DBHelper for the parent/child relationship
			 * @param parentChildDir the URI of the parent's children
			 * @param db the database to do lookups on
			 * @param childTable the child table to look into
			 * @param values the values that describe the child in question.
			 * @return if an identical child is found, returns its Uri. If none are found, returns null.
			 */
			public Uri getIdenticalChild(DBHelper m2m, Uri parentChildDir, SQLiteDatabase db, String childTable, ContentValues values);
		}

		/**
		 * Selects rows from the TO table that have a relation from the given item in the FROM table.
		 *
		 * @param fromId _ID of the item that's being
		 * @param db DB that contains all the tables
		 * @param toProjection projection for the TO table
		 * @param selection any extra selection query or null
		 * @param selectionArgs any extra selection arguments or null
		 * @param sortOrder the desired sort order or null
		 * @return
		 */
		public Cursor queryTo(long fromId, SQLiteDatabase db, String[] toProjection, String selection, String[] selectionArgs, String sortOrder){
			// XXX hack to get around ambiguous column names. Is there a better way to write this query?
			if (selection != null){
				selection = selection.replaceAll("(\\w+=\\?)", mToTable + ".$1");
			}

			return db.query(mToTable
					+ " INNER JOIN " + mJoinTable
					+ " ON " + mJoinTable+"."+ManyToManyColumns.TO_ID + "=" + mToTable + "." + BaseColumns._ID,
					MediaProvider.addPrefixToProjection(mToTable, toProjection),
					MediaProvider.addExtraWhere(selection, mJoinTable + "." + ManyToManyColumns.FROM_ID + "=?"),
					MediaProvider.addExtraWhereArgs(selectionArgs, Long.toString(fromId)), null, null, sortOrder);
		}

		public Cursor query(SQLiteDatabase db, boolean isItem, Uri uri, String[] projection, String selection,
				String[] selectionArgs, String sortOrder){
			Cursor c;

			if (isItem){
				final Uri parent = MediaProvider.removeLastPathSegments(uri, 2);

				final long parentId = ContentUris.parseId(parent);

				final String childId = uri.getLastPathSegment();

				c = queryTo(parentId,
						db,
						projection,
						MediaProvider.addExtraWhere(selection, JsonSyncableItem._ID+"=?"),
						MediaProvider.addExtraWhereArgs(selectionArgs, childId),
						sortOrder);
			}else{
				final Uri parent = MediaProvider.removeLastPathSegment(uri);

				final long parentId = ContentUris.parseId(parent);
				c = queryTo(parentId, db, projection, selection, selectionArgs, sortOrder);

			}
			return c;
		}
	}

	public static class JSONSyncableIdenticalChildFinder implements IdenticalChildFinder {
		@Override
		public Uri getIdenticalChild(DBHelper m2m, Uri parentChildDir, SQLiteDatabase db, String childTable,
				ContentValues values) {
			Uri existingChild = null;
			if (values.containsKey(JsonSyncableItem._PUBLIC_URI)){
				final Cursor existingItem = db.query(childTable, new String[]{JsonSyncableItem._ID, JsonSyncableItem._PUBLIC_URI}, JsonSyncableItem._PUBLIC_URI+"=?", new String[]{values.getAsString(JsonSyncableItem._PUBLIC_URI)}, null, null, null);
				if (existingItem.moveToFirst()){
					existingChild = ContentUris.withAppendedId(parentChildDir, existingItem.getLong(existingItem.getColumnIndex(JsonSyncableItem._ID)));
				}
				existingItem.close();
			}
			return existingChild;
		}
	}
}
