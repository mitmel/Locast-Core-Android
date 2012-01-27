package edu.mit.mobile.android.locast.data;
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
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class Event extends TaggableItem implements Locatable.Columns {
	public final static String TAG = "LocastSyncCast";
	public final static String PATH = "events";
	public final static Uri
		CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String SERVER_PATH = "event/";

	// event-specific fields
	public static final String
		_TITLE 			= "title",
		_DESCRIPTION 	= "description",
		_START_DATE     = "start_date",
		_END_DATE       = "end_date",
		_THUMBNAIL_URI  = "thumbnail_uri";

	public static final String[] PROJECTION =
	{   _ID,
		_PUBLIC_URI,
		_TITLE,
		_DESCRIPTION,
		_AUTHOR,
		_AUTHOR_URI,
		_CREATED_DATE,
		_MODIFIED_DATE,
		_START_DATE,
		_END_DATE,
		_THUMBNAIL_URI,
		_LATITUDE,
		_LONGITUDE,
		_DRAFT };

	// TODO fix the sort order
	public static final String
		SORT_ORDER_DEFAULT = Event._START_DATE+" ASC";

	public Event(Cursor c) {
		super(c);
	}

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends TaggableItem.TaggableItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = -6513174961005635755L;

		public ItemSyncMap() {
			super();
			putAll(Locatable.SYNC_MAP);

			put(_TITLE, 			new SyncFieldMap("title", SyncFieldMap.STRING));
			put(_DESCRIPTION, 		new SyncFieldMap("description", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL));

			put(_START_DATE,		new SyncFieldMap("start_date", SyncFieldMap.DATE));
			put(_END_DATE,			new SyncFieldMap("end_date", SyncFieldMap.DATE));

			put(_THUMBNAIL_URI, 	new SyncFieldMap("preview_image", SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));

			remove(_PRIVACY);
		}
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	/**
	 * Gets the preferred full URI of the given cast. If it's a cast that's in a project,
	 * returns that uri.
	 *
	 * Makes a single query.
	 *
	 * @param c a cursor pointing to a cast. Ensure the cursor has selected the Cast._PROJECT_ID field.
	 * @return the canonical URI for the given cast.
	 */
	public static Uri getCanonicalUri(Cursor c){
		Uri canonical = null;

		final long castId = c.getLong(c.getColumnIndex(Event._ID));
		canonical = ContentUris.withAppendedId(CONTENT_URI, castId);
		return canonical;
	}

	/**
	 * @param context
	 * @param cast
	 * @return the title of the cast or null if there's an error.
	 */
	public static String getTitle(Context context, Uri cast){
		final String[] projection = {Event._ID, Event._TITLE};
		final Cursor c = context.getContentResolver().query(cast, projection, null, null, null);
		String castTitle = null;
		if (c.moveToFirst()){
			castTitle = c.getString(c.getColumnIndex(Event._TITLE));
		}
		c.close();
		return castTitle;
	}


}
