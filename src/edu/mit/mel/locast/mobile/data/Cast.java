package edu.mit.mel.locast.mobile.data;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Video.Media;
import android.util.Log;
import edu.mit.mel.locast.mobile.StreamUtils;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkProtocolException;

public class Cast extends TaggableItem implements MediaScannerConnectionClient {
	public final static String TAG = "LocastSyncCast";
	public final static String PATH = "casts";
	public final static Uri 
		CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	
	public final static String SERVER_PATH = "/cast/";
	public final static String DEVICE_EXTERNAL_MEDIA_PATH = "/locast/";
	
	public static final String
		_TITLE 			= "title",
		_DESCRIPTION 	= "description";

	public static final String 
		_LOCAL_URI = "local_uri",
		_PUBLIC_URI = "public_uri",
		_CONTENT_TYPE = "content_type",
		_PROJECT_ID   = "project_id",

		_THUMBNAIL_URI = "thumbnail_uri",

		_LATITUDE = "lat",
		_LONGITUDE = "lon";
	
	public static final String[] PROJECTION = 
	{   _ID,
		_TITLE,
		_DESCRIPTION,
		_PRIVACY,
		_AUTHOR,
		_CREATED_DATE,
		_PUBLIC_ID,
		_PROJECT_ID,
		_LOCAL_URI,
		_PUBLIC_URI,
		_CONTENT_TYPE,
		_MODIFIED_DATE,
		_THUMBNAIL_URI,
		_LATITUDE,
		_LONGITUDE };
	
	public static final String 
		DEFAULT_SORT = Cast._MODIFIED_DATE+" DESC",
		SELECTION_LAT_LON = Cast._LATITUDE + " - ? < 1 and "+Cast._LONGITUDE + " - ? < 1";

	private Context context;
	private AndroidNetworkClient nc;
	private MediaScannerConnection msc;
	private final Queue<String> toScan = new LinkedList<String>();
	private final Map<String, ScanQueueItem> scanMap = new TreeMap<String, ScanQueueItem>();
	
	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}
	
	public static Uri toGeoUri(Cursor c){
		if (c.isNull(c.getColumnIndex(_LATITUDE)) || c.isNull(c.getColumnIndex(_LONGITUDE))) {
			return null;
		}
		return Uri.parse("geo:"+c.getDouble(c.getColumnIndex(_LATITUDE))+","+c.getDouble(c.getColumnIndex(_LONGITUDE)));
	}
	
	public static Location toLocation(Cursor c){
		final int lat_idx = c.getColumnIndex(_LATITUDE);
		final int lon_idx = c.getColumnIndex(_LONGITUDE);
		if (c.isNull(lat_idx) || c.isNull(lon_idx)) {
			return null;
		}
		final Location l = new Location("internal");
		l.setLatitude(c.getDouble(lat_idx));
		l.setLongitude(c.getDouble(lon_idx));
		return l;
	}
	
	@Override
	public java.util.Map<String,SyncItem> getSyncMap() {
		final Map<String,SyncItem> syncMap = new HashMap<String, SyncItem> (super.getSyncMap());
		syncMap.put(_PUBLIC_ID, 	new SyncMap("id", SyncMap.INTEGER, true));
		
		syncMap.put("_location", new SyncCustomArray("location", true) {
			
			@Override
			public JSONArray toJSON(Context context, Uri localItem, Cursor c)
					throws JSONException {
				
				final int latCol = c.getColumnIndex(_LATITUDE);
				final int lonCol = c.getColumnIndex(_LONGITUDE);
				
				if (c.isNull(latCol) || c.isNull(lonCol)){
					return null;
				}
				
				final JSONArray coords = new JSONArray();
				coords.put(c.getDouble(lonCol));
				coords.put(c.getDouble(latCol));
				return coords;
			}
			
			@Override
			public ContentValues fromJSON(Context context, Uri localItem, JSONArray item)
					throws JSONException {
				final ContentValues cv = new ContentValues();
				cv.put(_LONGITUDE, item.getDouble(0));
				cv.put(_LATITUDE, item.getDouble(1));
				return cv;
			}
		});
		
		syncMap.put(_DESCRIPTION, 		new SyncMap("description", SyncMap.STRING));
		syncMap.put(_TITLE, 			new SyncMap("title", SyncMap.STRING));
		

		
		syncMap.put(_THUMBNAIL_URI, 	new SyncMap("screenshot", SyncMap.STRING, true, SyncItem.SYNC_FROM));
		

		syncMap.put("_contents", new SyncCustomArray("castvideos", SyncItem.SYNC_FROM) {
			
			@Override
			public JSONArray toJSON(Context context, Uri localItem, Cursor c)
					throws JSONException, NetworkProtocolException, IOException {
				
				final Cursor castMedia_c = context.getContentResolver().query(Uri.withAppendedPath(localItem, CastMedia.PATH), CastMedia.PROJECTION, null, null, null);
				final JSONArray ja = new JSONArray();
				for (castMedia_c.moveToFirst(); !castMedia_c.isAfterLast(); castMedia_c.moveToNext()){
					ja.put(CastMedia.toJSON(context, localItem, castMedia_c, CastMedia.SYNC_MAP));
				}
				castMedia_c.close();
				return ja;
			}
			
			@Override
			public ContentValues fromJSON(Context context, Uri localItem, JSONArray item)
					throws JSONException, NetworkProtocolException, IOException {
				// do nothing. We can't load from JSON until we have the local Cast URI to create the reference.
				return new ContentValues();
			}
		});
		
		return syncMap;
	}

	@Override
	public void onUpdateItem(Context context, Uri uri, JSONObject item) throws SyncException {
		this.context = context;
		final ContentResolver cr = context.getContentResolver();
		final Cursor c = cr.query(uri, PROJECTION, null, null, null);
		c.moveToFirst();
		if (nc == null){
			nc = AndroidNetworkClient.getInstance(context);
		}
		
		final Map<String, SyncItem> syncMap = new HashMap<String, SyncItem>();
		syncMap.put("_contents", new SyncCustomArray("castvideos") {
			
			@Override
			public JSONArray toJSON(Context context, Uri localItem, Cursor c)
					throws JSONException, NetworkProtocolException, IOException {
				// do nothing.
				return new JSONArray();
			}
			
			@Override
			public ContentValues fromJSON(Context context, Uri localItem, JSONArray item)
					throws JSONException, NetworkProtocolException, IOException {
				final ContentResolver cr = context.getContentResolver();
				
				for (int i = 0; i < item.length(); i++){
					final ContentValues cv = CastMedia.fromJSON(context, null, item.getJSONObject(i), CastMedia.SYNC_MAP);
					
					cv.put(CastMedia._LIST_IDX, i);
					cv.put(CastMedia._PARENT_ID, ContentUris.parseId(localItem));
					// this will actually overwrite any existing entries in the same index.
					cr.insert(Uri.withAppendedPath(localItem, CastMedia.PATH), cv);
				}
				return new ContentValues();
			}
		});
		
		try {
			Log.d(TAG, "trying to load cast videos from "+ item);
			fromJSON(context, uri, item, syncMap);
		} catch (final Exception e1) {
			final SyncException e = new SyncException("Error loading cast videos");
			e.initCause(e1);
			throw e;
		}
		
//		final String locUri = c.getString(c.getColumnIndex(_LOCAL_URI));
//		final String pubUri = c.getString(c.getColumnIndex(_PUBLIC_URI));
//		final boolean hasLocUri = locUri != null && locUri.length() > 0;
//		final boolean hasPubUri = pubUri != null && pubUri.length() > 0;
//		
//		if (hasLocUri && hasPubUri){
//			Log.i(TAG, "Content media item "+uri+ "appears to be in sync with server.");
//			
//		// only have a public copy, so download it and store locally.
//		}else if (!hasLocUri && hasPubUri){
//			/* temp disable
//			final File destfile = getFilePath(pubUri);
//			String newLocUri = null;
//			if (!downloadCastMedia(destfile, uri, pubUri)){
//				newLocUri = checkForMediaEntry(this.context, uri, pubUri);
//			}
//			*/
//		}else if (hasLocUri && !hasPubUri){
//			// upload
//			try {
//				nc.uploadContent(c.getInt(c.getColumnIndex(_PUBLIC_ID)), locUri, c.getString(c.getColumnIndex(_CONTENT_TYPE)));
//			} catch (final Exception e){
//				final SyncException se = new SyncException("Error uploading content item.");
//				se.initCause(e);
//				throw se;
//			}
//		}
//		
		c.close();
	}
	
	public static File getFilePath(String pubUri) throws SyncException{
		final File sdcardPath = Environment.getExternalStorageDirectory();
		// pull off the server's name for this file.
		final String localFile = pubUri.substring(pubUri.lastIndexOf('/') + 1);
		final File locastSaveFile = new File(sdcardPath, DEVICE_EXTERNAL_MEDIA_PATH);
		locastSaveFile.mkdirs();
		if (!locastSaveFile.canWrite()) {
			throw new SyncException("cannot write to external storage '"+locastSaveFile.getAbsolutePath()+"'");
		}
		final File saveFile = new File(locastSaveFile, localFile);
		return saveFile;
	}
	/**
	 * @param castUri the content:// uri of the cast
	 * @param pubUri the http:// uri of the public video
	 * @return true if anything has changed. False if this function has determined it doesn't need to do anything.
	 * @throws SyncException
	 */
	public boolean downloadCastMedia(File saveFile, Uri castUri, String pubUri) throws SyncException {
		try {
			if (saveFile.exists()){
				final HttpResponse headRes = nc.head(pubUri);
				if (saveFile.length() > 2000000){
					Log.w(TAG, "Video "+castUri+" is too large. Not downloading.");
					return false;
				}
				if (saveFile.length() == Long.valueOf(headRes.getFirstHeader("Content-Length").getValue())){
					Log.i(TAG, "Local copy of cast "+saveFile+" seems to be the same as the one on the server. Not re-downloading.");
					return false;
				}else{
					//Log.i(TAG, )
				}
			}
			final HttpResponse res = nc.get(pubUri);
			final HttpEntity ent = res.getEntity();
			final InputStream is = ent.getContent();
			
			final FileOutputStream fos = new FileOutputStream(saveFile);
			StreamUtils.inputStreamToOutputStream(is, fos);
			fos.close();
			is.close();

			final String filePath = saveFile.getAbsolutePath();
			final String contentType = ent.getContentType().getValue();
			
			
			if (msc == null){
				this.msc = new MediaScannerConnection(context, this);
				this.msc.connect();
				
			}else if (msc.isConnected()){
				msc.scanFile(filePath, contentType);
				
			}else{
				scanMap.put(filePath, new ScanQueueItem(castUri, contentType));
				toScan.add(filePath);
			}
							
		} catch (final Exception e) {
			final SyncException se = new SyncException("Error downloading content item.");
			se.initCause(e);
			throw se;
		}
		return true;
	}
	
	public void onScanCompleted(String path, Uri uri) {
		if (uri == null){
			Log.e(TAG, "Scan failed for newly downloaded content: "+path);
			return;
		}
		
		
		final ContentValues cvCast = new ContentValues();
		cvCast.put(_LOCAL_URI, uri.toString());
		final ScanQueueItem item = scanMap.get(path);
		Log.d("Locast", "new local uri " + uri + " for cast "+item.castUri);
		// TODO should be passing in the modified date here to prevent marking the cast as dirty.
		this.context.getContentResolver().update(item.castUri, cvCast, null, null);
	}
	
	public void onMediaScannerConnected() {
		while (!toScan.isEmpty()){
			final String scanme = toScan.remove();
			final ScanQueueItem item = scanMap.get(scanme);
			this.msc.scanFile(scanme, item.contentType);
		}
	}
	private class ScanQueueItem {
		public Uri castUri;
		public String contentType;
		public ScanQueueItem(Uri castUri, String contentType) {
			this.castUri = castUri;
			this.contentType = contentType;
		}
	}
	
	/**
	 * Scans the media database to see if the given item is there.
	 * 
	 * @param context
	 * @param uri 
	 * @param c
	 * @param pubUri
	 * @return local URI if it exists.
	 * @throws SyncException
	 */
	public static String checkForMediaEntry(Context context, Uri uri, String pubUri) throws SyncException{
		String newLocUri = null;
		final File destfile = getFilePath(pubUri);
		final String[] projection = {Media.DATA, Media._ID};
		final String selection = Media.DATA + "=?";
		final String[] selectionArgs = {destfile.getAbsolutePath()};
		final Cursor mediaEntry = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
		if (mediaEntry.getCount() > 0){
			mediaEntry.moveToFirst();
			newLocUri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, mediaEntry.getLong(mediaEntry.getColumnIndex(Media._ID))).toString();
		}else{
			Log.e(TAG, "The media provider doesn't seem to know about "+destfile.getAbsolutePath()+" which is on the filesystem. Strange...");
		}
		
	
		if (newLocUri != null){
			final ContentValues cvCast = new ContentValues();
			cvCast.put(_LOCAL_URI, newLocUri);
			final String[] castProjection = {_ID, _MODIFIED_DATE}; 
			final Cursor castCursor = context.getContentResolver().query(uri, castProjection, null, null, null);
			castCursor.moveToFirst();
			cvCast.put(_MODIFIED_DATE, castCursor.getLong(castCursor.getColumnIndex(_MODIFIED_DATE)));
			castCursor.close();
			
			Log.d("Locast", "new local uri " + newLocUri + " for cast "+uri);
			context.getContentResolver().update(uri, cvCast, null, null);
		}
		return newLocUri;
	}
}
