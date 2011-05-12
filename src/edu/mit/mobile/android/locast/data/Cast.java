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
import java.io.IOException;

import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class Cast extends TaggableItem implements Favoritable.Columns, Locatable.Columns, Commentable.Columns {
	public final static String TAG = "LocastSyncCast";
	public final static String PATH = "casts";
	public final static Uri
		CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String SERVER_PATH = "cast/";


	public static final String
		_TITLE 			= "title",
		_DESCRIPTION 	= "description",
		_OFFICIAL		= "official";

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
		_CREATED_DATE,
		_MODIFIED_DATE,
		_THUMBNAIL_URI,
		_FAVORITED,
		_LATITUDE,
		_LONGITUDE,
		_OFFICIAL,
		_DRAFT };

	public static final String
		SORT_ORDER_DEFAULT = Cast._FAVORITED + " DESC," + Cast._MODIFIED_DATE+" DESC";

	private Context context;

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
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
			put(_OFFICIAL,			new SyncFieldMap("official", SyncFieldMap.BOOLEAN, SyncItem.SYNC_FROM));
			put(_MEDIA_PUBLIC_URI,   new SyncChildRelation("media", new JsonSyncableItem.SyncChildRelation.SimpleRelationship(CastMedia.PATH), SyncItem.SYNC_FROM));

			//put("_contents", new OrderedList.SyncMapItem("media", new CastVideo(), CastVideo.PATH));
		}
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	@Override
	public void onPostSyncItem(Context context, Uri uri, JSONObject item, boolean updated) throws SyncException, IOException {
		this.context = context;

		final ContentResolver cr = context.getContentResolver();

//		OrderedList.onUpdate(context, uri, item, "media", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_FROM, new CastVideo(), CastVideo.PATH);
//		final Uri castVideoDirUri = Uri.withAppendedPath(uri, CastVideo.PATH);
//		final String pubCastVideoUri = MediaProvider.getPublicPath(cr, castVideoDirUri);
//
//		final Cursor cast = cr.query(uri, PROJECTION, null, null, null);
//		cast.moveToFirst();
//		final Cursor castVideo = cr.query(castVideoDirUri, CastVideo.PROJECTION, null, null, null);
//		boolean haveAnyLocMedia = cast.getInt(cast.getColumnIndex(_MEDIA_LOCAL_URI)) != 0;
//
//		try {
//			final int mediaUrlCol = castVideo.getColumnIndex(CastVideo._MEDIA_URL);
//			final int localUriCol = castVideo.getColumnIndex(CastVideo._LOCAL_URI);
//			final int idxCol = castVideo.getColumnIndex(CastVideo._LIST_IDX);
//			final int mediaContentTypeCol = castVideo.getColumnIndex(CastVideo._MIME_TYPE);
//			final int locIdCol = castVideo.getColumnIndex(CastVideo._ID);
//
//			for (castVideo.moveToFirst(); ! castVideo.isAfterLast(); castVideo.moveToNext()){
//				final Uri locMediaUri = castVideo.isNull(localUriCol) ? null : parseMaybeUri(castVideo.getString(localUriCol));
//				final String pubMediaUri = castVideo.getString(mediaUrlCol);
//				final boolean hasLocMediaUri = locMediaUri != null;
//				final boolean hasPubMediaUri = pubMediaUri != null && pubMediaUri.length() > 0;
//				haveAnyLocMedia = haveAnyLocMedia || hasLocMediaUri;
//
//				if (hasLocMediaUri && !hasPubMediaUri){
//					// upload
//					try {
//						final NetworkClient nc = NetworkClient.getInstance(context);
//						nc.uploadContentWithNotification(context,
//								getCanonicalUri(context, uri),
//								pubCastVideoUri + castVideo.getLong(idxCol)+"/",
//								locMediaUri,
//								castVideo.getString(mediaContentTypeCol));
//					} catch (final Exception e){
//						final SyncException se = new SyncException(context.getString(R.string.error_uploading_cast_video));
//						se.initCause(e);
//						throw se;
//					}
//				Log.d(TAG, "Cast Media #" + castVideo.getPosition() + " is " + castVideo.getString(castVideo.getColumnIndex(CastVideo._MEDIA_URL)));
//				}
//			}
//
//			if (!haveAnyLocMedia){
//				Log.d(TAG, "There are no local videos, so looking to see if we should download");
//				final Set<String> systemTags = getTags(cr, uri, TaggableItem.SYSTEM_PREFIX);
//				MediaProvider.dumpCursorToLog(cast, Cast.PROJECTION);
//				final int pubMediaUrlCol = cast.getColumnIndex(_MEDIA_PUBLIC_URI);
//				final String pubMediaUri = cast.getString(pubMediaUrlCol);
//				final boolean hasPubMediaUri = pubMediaUri != null && pubMediaUri.length() > 0;
//
//				if (hasPubMediaUri && systemTags.contains("_featured")){
//					Log.d(TAG, "cast is featured, so we'll download it.");
//					final Uri pubMediaUriUri = Uri.parse(pubMediaUri);
//					// only have a public copy, so download it and store locally.
//					final File destfile = getFilePath(pubMediaUriUri);
//					String newLocUri = null;
//					if (!downloadCastVideo(context, destfile, uri, pubMediaUri)){
//						newLocUri = checkForMediaEntry(context, uri, pubMediaUriUri);
//					}
//				}
//			}
//		}finally{
//			castVideo.close();
//			cast.close();
//		}
	} // onPostSyncItem()

	/**
	 * @param castUri uri for the cast.
	 * @return The CastVideo URI of the given cast.
	 */
	public static final Uri getCastVideoUri(Uri castUri){
		return Uri.withAppendedPath(castUri, CastVideo.PATH);
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

		return cast;/*
		Uri canonical = null;

		final Cursor c = context.getContentResolver().query(cast, new String[]{Cast._ID, Cast._PROJECT_ID}, null, null, null);
		if (c.moveToFirst()){
			final long castId = c.getLong(c.getColumnIndex(Cast._ID));
			final Uri project = getProjectUri(c);
			if (project != null){
				canonical = project.buildUpon().appendPath(PATH).appendPath(Long.toString(castId)).build();
			}else{
				canonical = cast;
			}
		}
		c.close();
		return canonical;*/
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
		final Uri project = getProjectUri(c);
		if (project != null){
			canonical = project.buildUpon().appendPath(PATH).appendPath(Long.toString(castId)).build();
		}else{
			canonical = ContentUris.withAppendedId(CONTENT_URI, castId);
		}
		return canonical;
	}

	/**
	 * @param cast a cursor pointing to a cast.
	 * @return the uri of the project associated with this cast, or null if there is none.
	 */
	public static final Uri getProjectUri(Cursor cast){
		/*
		final int projectIdx = cast.getColumnIndex(Cast._PROJECT_ID);
		if (cast.isNull(projectIdx)){
			return null;
		}

		return ContentUris.withAppendedId(Project.CONTENT_URI, cast.getLong(projectIdx));
		*/
		return null;
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

	/*
	public void updateCastVideo(String castVideoPath, String mimeType){
		if (msc == null){
			this.msc = new MediaScannerConnection(context, this);
			this.msc.connect();

		}else if (msc.isConnected()){
			msc.scanFile(castVideoPath, mimeType);

		}else{
			scanMap.put(castVideoPath, new ScanQueueItem(castUri, contentType));
			toScan.add(filePath);
		}
	}*/

}
