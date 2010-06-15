package edu.mit.mel.locast.mobile.data;

import java.util.HashMap;
import java.util.Map;

import android.net.Uri;

public class ShotList extends JsonSyncableItem {
	public static final String
		_PUBLIC_ID   = "id",
		_DIRECTION   = "direction",
		_DURATION    = "duration",
		_PARENT_ID   = "parent_id"
		;
	
	public final static String PATH = "shotlists";
	public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH); 
	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_ID,
		_MODIFIED_DATE,
		_DIRECTION,
		_DURATION,
		_PARENT_ID
	};

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	@Override
	public Map<String, SyncItem> getSyncMap() {
		final Map<String, SyncItem> syncMap = new HashMap<String, SyncItem>();
		syncMap.put(_DURATION, new SyncMap("duration", SyncMap.DURATION));
		syncMap.put(_DIRECTION, new SyncMap("direction", SyncMap.STRING));
		syncMap.put(_PUBLIC_ID, new SyncMap("id", SyncMap.INTEGER, SyncMap.SYNC_FROM));
		//syncMap.put(, value)
		return syncMap;
	}

}
