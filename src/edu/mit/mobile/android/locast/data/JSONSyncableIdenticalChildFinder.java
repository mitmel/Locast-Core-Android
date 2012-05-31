package edu.mit.mobile.android.locast.data;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import edu.mit.mobile.android.content.DBHelper;
import edu.mit.mobile.android.content.m2m.IdenticalChildFinder;

public class JSONSyncableIdenticalChildFinder implements IdenticalChildFinder {
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