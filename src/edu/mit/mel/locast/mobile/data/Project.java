package edu.mit.mel.locast.mobile.data;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import edu.mit.mel.locast.mobile.ListUtils;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

/**
 * DB entry for a project. Also contains a sync mapping for publishing
 * to the network.
 * 
 * @author stevep
 *
 */
public class Project extends TaggableItem {
	public final static String PATH = "projects";
	public final static Uri CONTENT_URI = Uri
			.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	
	public final static String SERVER_PATH = "/projects/";
	

	
	public static final String 	
		TITLE = "title",
		DESCRIPTION = "description",
		CASTS = "casts",
		CASTS_EXTERNAL = "casts_external",
		MEMBERS = "members",
		START_DATE = "startdate",
		END_DATE = "enddate";

	public final static String[] PROJECTION = {
		_ID,
		PUBLIC_ID,
		MODIFIED_DATE,
		TITLE,
		AUTHOR,
		DESCRIPTION,
		CASTS,
		CASTS_EXTERNAL,
		MEMBERS,
		PRIVACY,
		START_DATE,
		END_DATE,
};
	
	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}
	
	public static boolean canEdit(Cursor c){
		final List<String> members = getMemberList(c);
		return TaggableItem.canEdit(c) || members.contains(AndroidNetworkClient.getInstance(null).getUsername());
	}
	
	public static List<String> getMemberList(Cursor c){
		return Project.getList(c.getColumnIndex(Project.MEMBERS), c);
	}
	
	/**
	 * @param context
	 * @param c
	 * @return a comma-separated list of members.
	 */
	public static String getMemberList(Context context, Cursor c){
		String membersText = ListUtils.join(getMemberList(c), ", ");
		if (membersText.length() == 0){
			membersText = context.getString(R.string.project_no_members);
		}
		return membersText;
	}
	
	/* (non-Javadoc)
	 * 
	 * Map internal casts to external casts.
	 * 
	 * @see edu.mit.mel.locast.mobile.data.JsonSyncableItem#onPreSyncItem(android.content.ContentResolver, android.net.Uri, android.database.Cursor)
	 */
	@Override
	public void onPreSyncItem(ContentResolver cr, Uri uri, Cursor c) throws SyncException {
		final List<Long> internalCasts = getListLong(c.getColumnIndex(CASTS), c);
		final List<Long> externalCasts = new Vector<Long>(internalCasts.size());
		final String[] castProjection = {Cast._ID, Cast.PUBLIC_ID};
		for (final Long intCastId: internalCasts){
			final Cursor cast = cr.query(ContentUris.withAppendedId(Cast.CONTENT_URI, intCastId), castProjection, null, null, null);
			cast.moveToFirst();
			externalCasts.add(cast.getLong(c.getColumnIndex(Cast.PUBLIC_ID)));
			cast.close();
		}
		final ContentValues cv = new ContentValues();
		cv.put(MODIFIED_DATE, c.getLong(c.getColumnIndex(MODIFIED_DATE))); // don't update modified date
		putList(CASTS_EXTERNAL, cv, externalCasts);
		cr.update(uri, cv, null, null);
		final int pos = c.getPosition();
		c.requery();
		c.moveToPosition(pos);
	}
	
	@Override
	public void onUpdateItem(Context context, Uri uri) throws SyncException, IOException {
		final ContentResolver cr = context.getContentResolver();
		final String[] projProjection = {_ID, CASTS, CASTS_EXTERNAL, MODIFIED_DATE}; 
		final Cursor project = cr.query(uri, projProjection, null, null, null);
		project.moveToFirst();
		final List<Long> externalCasts = getListLong(project.getColumnIndex(CASTS_EXTERNAL), project);
		
		final List<Long> internalCasts = new Vector<Long>(externalCasts.size());
		final String[] castProjection = {Cast._ID, Cast.PUBLIC_ID};
		for(final Long extCastId: externalCasts){
			final Cursor cast = cr.query(Cast.CONTENT_URI, castProjection, Cast.PUBLIC_ID+"="+extCastId, null, null);
			if (!cast.moveToFirst()){
				Log.d("ProjectSync", "didn't find local copy of external cast #"+extCastId+", so downloading...");
				final Cast mr = new Cast();
				Sync.loadItemFromServer(context, MediaProvider.getPublicPath(cr, Cast.CONTENT_URI) + extCastId, mr);
				cast.requery();
			}
			if (cast.moveToFirst()){
				internalCasts.add(cast.getLong(cast.getColumnIndex(Cast._ID)));
			}else{
				cast.close();
				throw new SyncException("Unable to find local or remote copy of external cast #"+extCastId);
			}
			cast.close();
		}
		final ContentValues cv = new ContentValues();
		cv.put(MODIFIED_DATE, project.getLong(project.getColumnIndex(MODIFIED_DATE))); // don't update modified date
		putList(CASTS, cv, internalCasts);
		project.close();
		cr.update(uri, cv, null, null);
	}
	

	@Override
	public Map<String, SyncItem> getSyncMap() {
		final Map<String, SyncItem> syncMap = super.getSyncMap();
		
		syncMap.put(PUBLIC_ID, 		new SyncMap("id", SyncMap.INTEGER, true));
		syncMap.put(DESCRIPTION, 	new SyncMap("description", SyncMap.STRING));
		syncMap.put(TITLE, 			new SyncMap("title", SyncMap.STRING));
		syncMap.put(AUTHOR, 		new SyncMap("author", SyncMap.STRING, SyncMap.SYNC_FROM));
		syncMap.put(CASTS_EXTERNAL,	new SyncMap("casts", SyncMap.LIST_INTEGER));
		syncMap.put(MEMBERS, 		new SyncMap("members", SyncMap.LIST_STRING));
		syncMap.put(MODIFIED_DATE,	new SyncMap("modified", SyncMap.DATE, SyncItem.SYNC_FROM));
		syncMap.put(START_DATE,		new SyncMap("start", SyncMap.DATE, true));
		syncMap.put(PRIVACY,        new SyncMap("privacy", SyncMap.STRING));
		syncMap.put(END_DATE,		new SyncMap("end", SyncMap.DATE, true));
		
		return syncMap;
	}

}
