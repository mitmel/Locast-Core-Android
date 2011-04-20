package edu.mit.mobile.android.locast.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

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

	public static final class DBHelper {
		private final String mFromTable, mToTable, mJoinTable;

		public DBHelper(String leftTable, String rightTable) {
			mFromTable = leftTable;
			mToTable = rightTable;
			mJoinTable = mFromTable + "_" + mToTable;

		}

		public String getJoinTableName(){
			return mJoinTable;
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
	}
}
