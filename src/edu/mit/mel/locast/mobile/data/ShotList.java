package edu.mit.mel.locast.mobile.data;

import java.util.HashMap;
import java.util.Map;

import android.net.Uri;

public class ShotList extends JsonSyncableItem {
	public static final String
		_PUBLIC_ID   = "id",
		_DIRECTION   = "direction",
		_DURATION    = "duration",
		_PARENT_ID   = "parent_id",
		_LIST_IDX    = "list_idx"
		;
	
	public final static String PATH = "shotlist";
	public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH); 
	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_ID,
		_DIRECTION,
		_DURATION,
		_PARENT_ID,
		_LIST_IDX
	};

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	public static final HashMap<String, SyncItem> SYNC_MAP = new HashMap<String, SyncItem>();
	
	static {
		SYNC_MAP.put(_DURATION,  new SyncMap("duration", SyncMap.DURATION));
		SYNC_MAP.put(_DIRECTION, new SyncMap("direction", SyncMap.STRING));
		SYNC_MAP.put(_PUBLIC_ID, new SyncMap("id", SyncMap.INTEGER, SyncMap.SYNC_FROM));
	}
	
	@Override
	public Map<String, SyncItem> getSyncMap() {
		return SYNC_MAP;
	}

}
