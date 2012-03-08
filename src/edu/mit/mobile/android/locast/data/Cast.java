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
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.content.ForeignKeyManager;

public class Cast extends TaggableItem implements Favoritable.Columns, Locatable.Columns, Commentable.Columns {
	public final static String TAG = "LocastSyncCast";
	public final static String PATH = "casts";
	public final static Uri
		CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String SERVER_PATH = "cast/";


	public static final String
		_TITLE 			= "title",
 _DESCRIPTION = "description";

	public static final String
		_MEDIA_PUBLIC_URI = "public_uri",
		_THUMBNAIL_URI = "thumbnail_uri";

	public static final String[] PROJECTION =
	{   _ID,
		_PUBLIC_URI,
		_MEDIA_PUBLIC_URI,
		_TITLE,
		_DESCRIPTION,
		_PRIVACY,
		_AUTHOR,
		_AUTHOR_URI,
		_CREATED_DATE,
		_MODIFIED_DATE,
		_THUMBNAIL_URI,
		_FAVORITED,
		_LATITUDE,
		_LONGITUDE,
		_DRAFT };

	public static final String
		SORT_ORDER_DEFAULT = Cast._DRAFT + " DESC," + Cast._FAVORITED + " DESC," + Cast._MODIFIED_DATE+" DESC";

	public static final Uri FEATURED = getTagUri(CONTENT_URI, addPrefixToTag(Cast.SYSTEM_PREFIX, "_featured"));
	public static final Uri FAVORITE = Favoritable.getFavoritedUri(Cast.CONTENT_URI, true);

	public Cast(Cursor c) {
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
			putAll(Favoritable.SYNC_MAP);
			putAll(Locatable.SYNC_MAP);
			putAll(Commentable.SYNC_MAP);

			put(_DESCRIPTION, 		new SyncFieldMap("description", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL));
			put(_TITLE, 			new SyncFieldMap("title", SyncFieldMap.STRING));

			put(_THUMBNAIL_URI, 	new SyncFieldMap("preview_image", SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
			//put(_MEDIA_PUBLIC_URI,  new SyncFieldMap("file_url",   SyncFieldMap.STRING, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
			put(_MEDIA_PUBLIC_URI,  new SyncChildRelation("media", new JsonSyncableItem.SyncChildRelation.SimpleRelationship(CastMedia.PATH), SyncItem.SYNC_FROM));

			//put("_contents", new OrderedList.SyncMapItem("media", new CastVideo(), CastVideo.PATH));
		}
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	/**
	 * @param castUri uri for the cast.
	 * @return The CastMedia URI of the given cast.
	 */
	public static final Uri getCastMediaUri(Uri castUri){
		return Uri.withAppendedPath(castUri, CastMedia.PATH);
	}

	/**
	 * Gets the preferred full URI of the given cast. If it's a cast that's in a project,
	 * returns that uri.
	 *
	 * Makes a single query.
	 *
	 * @param context
	 * @param cast
	 * @return the canonical URI for the given cast.
	 */
	public static Uri getCanonicalUri(Context context, Uri cast){

		return cast;
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

		final long castId = c.getLong(c.getColumnIndex(Cast._ID));
		canonical = ContentUris.withAppendedId(CONTENT_URI, castId);
		return canonical;
	}

	@Override
	public Uri getCanonicalUri() {
		return getCanonicalUri(this);
	}

	/**
	 * @param context
	 * @param cast
	 * @return the title of the cast or null if there's an error.
	 */
	public static String getTitle(Context context, Uri cast){
		final String[] projection = {Cast._ID, Cast._TITLE};
		final Cursor c = context.getContentResolver().query(cast, projection, null, null, null);
		String castTitle = null;
		if (c.moveToFirst()){
			castTitle = c.getString(c.getColumnIndex(Cast._TITLE));
		}
		c.close();
		return castTitle;
	}

	public static final ForeignKeyManager CAST_MEDIA = new ForeignKeyManager(CastMedia.class);

}
