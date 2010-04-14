package com.rmozone.mobilevideo;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.rmozone.mobilevideo.AnnotationDBKey.Annotation;


public class AnnotationData extends ContentProvider {
	
	private static class DatabaseHelper extends SQLiteOpenHelper {
		//based on http://developer.android.com/guide/samples/NotePad/src/com/example/android/notepad/NotePadProvider.html
		
		private static final String DATABASE_NAME = "annotation";
		private static final String TABLE_NAME = "annotation";
		private static final int DATABASE_VERSION = 1;

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
					+ Annotation._ID + " INTEGER PRIMARY KEY,"
					+ Annotation.VIDEO_UID + " TEXT,"
					+ Annotation.ANNOTATION_TIME + " INTEGER,"
					+ Annotation.ANNOTATION + " TEXT"
					+ ");"			
			);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w("AnnotationData", "Warning: db upgrade will clear all annotation data (it's probably too late now for you to do much about it).");
			db.execSQL("DROP TABLE IF EXISTS " + DatabaseHelper.TABLE_NAME);
			this.onCreate(db);
			
		}
		
	}
	
	private DatabaseHelper mOpenHelper;
	private static HashMap<String, String> annProjectionMap;
	private static UriMatcher annUriMatcher;
	public static final String AUTHORITY = "com.rmozone.mobilevideo";
	
	private static final int ANNOTATIONS = 1;
	private static final int ANNOTATION_ID = 2;
	
	@Override
	public boolean onCreate() {
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}


	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch(annUriMatcher.match(uri)) {
		case ANNOTATIONS:
			count = db.delete(DatabaseHelper.TABLE_NAME, selection, selectionArgs);
			break;
			
		case ANNOTATION_ID:
			final String annId = uri.getPathSegments().get(1);
			count = db.delete(DatabaseHelper.TABLE_NAME, Annotation._ID + "=" +annId 
					+ (!TextUtils.isEmpty(selection) ? " AND (" + selection + ")" : ""), selectionArgs);
			break;
			
		default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch(annUriMatcher.match(uri)) {
		case ANNOTATIONS:
			return Annotation.CONTENT_TYPE;
		case ANNOTATION_ID:
			return Annotation.CONTENT_ITEM_TYPE;
			
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		
		//validate uri
		if (annUriMatcher.match(uri) != ANNOTATIONS)
			throw new IllegalArgumentException("Unknown URI " + uri);
		
		//TODO: validate values
		
		final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		final long rowId = db.insert(DatabaseHelper.TABLE_NAME, Annotation.ANNOTATION, values);
		if(rowId > 0) {
			final Uri annUri = ContentUris.withAppendedId(Annotation.CONTENT_URI, rowId);
			getContext().getContentResolver().notifyChange(annUri, null);
			return annUri;
		}
		
		throw new SQLException("Failed ot insert row into " + uri);
	}

	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		
		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(DatabaseHelper.TABLE_NAME);
		qb.setProjectionMap(annProjectionMap);
		
		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = Annotation.DEFAULT_SORT_ORDER;
		}
		else {
			orderBy = sortOrder;
		}
		
		if(annUriMatcher.match(uri) == ANNOTATION_ID) {
			final String annId = uri.getPathSegments().get(1);
			selection = Annotation._ID + "=" + annId;
		}
		
		//get db & run query
		final SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		final Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
		
        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch(annUriMatcher.match(uri)) {
		case ANNOTATIONS:
			count = db.update(DatabaseHelper.TABLE_NAME, values, selection, selectionArgs);
			break;
		case ANNOTATION_ID:
			final String annId = uri.getPathSegments().get(1);
			count = db.update(DatabaseHelper.TABLE_NAME, values, Annotation._ID + "=" + annId
					+ (!TextUtils.isEmpty(selection) ? " AND (" + selection + ")" : ""), selectionArgs);
			break;
		default:
            throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
	
	
	static {
		annUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		annUriMatcher.addURI(AUTHORITY, "annotations", ANNOTATIONS);
		annUriMatcher.addURI(AUTHORITY, "annotations/#", ANNOTATION_ID);
		
		annProjectionMap = new HashMap<String, String>();
		annProjectionMap.put(Annotation._ID, Annotation._ID);
		annProjectionMap.put(Annotation.VIDEO_UID, Annotation.VIDEO_UID);
		annProjectionMap.put(Annotation.ANNOTATION_TIME, Annotation.ANNOTATION_TIME);
		annProjectionMap.put(Annotation.ANNOTATION, Annotation.ANNOTATION);

	}


}
