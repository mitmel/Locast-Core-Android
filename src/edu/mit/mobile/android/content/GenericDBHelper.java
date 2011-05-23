package edu.mit.mobile.android.content;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Provides basic CRUD database calls to handle very simple object types, eg:
 *
 * content://AUTHORITY/item
 * content://AUTHORITY/item/1
 *
 *
 *
 * @author steve
 *
 */
public class GenericDBHelper implements DBHelper {

	private final String mTable;
	private final Uri mContentUri;

	/**
	 * @param table
	 *            the table that the items are stored in. Must have a
	 *            BaseColumns._ID column.
	 * @param contentUri
	 *            the URI of the content directory. Eg. content://AUTHORITY/item
	 */
	public GenericDBHelper(String table, Uri contentUri) {
		mTable = table;
		mContentUri = contentUri;
	}

	@Override
	public Uri insertDir(SQLiteDatabase db, ContentProvider provider, Uri uri,
			ContentValues values) {
		final long id = db.insert(mTable, null, values);
		if (id != -1){
			return ContentUris.withAppendedId(mContentUri, id);
		}else{
			throw new SQLException("error inserting into " + mTable);
		}
	}

	@Override
	public int updateItem(SQLiteDatabase db, ContentProvider provider, Uri uri,
			ContentValues values, String where, String[] whereArgs) {

		return db.update(mTable, values,
				ProviderUtils.addExtraWhere(where, BaseColumns._ID + "=?"),
				ProviderUtils.addExtraWhereArgs(whereArgs, uri.getLastPathSegment()));
	}

	@Override
	public int updateDir(SQLiteDatabase db, ContentProvider provider, Uri uri,
			ContentValues values, String where, String[] whereArgs) {
		return db.update(mTable, values, where, whereArgs);
	}

	@Override
	public int deleteItem(SQLiteDatabase db, ContentProvider provider, Uri uri,
			String where, String[] whereArgs) {
		return db.delete(mTable,
				ProviderUtils.addExtraWhere(where, BaseColumns._ID + "=?"),
				ProviderUtils.addExtraWhereArgs(whereArgs, uri.getLastPathSegment()));
	}

	@Override
	public int deleteDir(SQLiteDatabase db, ContentProvider provider, Uri uri,
			String where, String[] whereArgs) {
		return db.delete(mTable, where, whereArgs);
	}

	@Override
	public Cursor queryDir(SQLiteDatabase db, Uri uri,
			String[] projection, String selection, String[] selectionArgs,
			String sortOrder) {

		return db.query(
				mTable,
				projection,
				selection,
				selectionArgs,
				null,
				null,
				sortOrder);

	}

	@Override
	public Cursor queryItem(SQLiteDatabase db, Uri uri, String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {

		return db.query(
				mTable,
				projection,
				ProviderUtils.addExtraWhere(selection, BaseColumns._ID+"=?"),
				ProviderUtils.addExtraWhereArgs(selectionArgs, uri.getLastPathSegment()),
				null,
				null,
				sortOrder);
	}
}
