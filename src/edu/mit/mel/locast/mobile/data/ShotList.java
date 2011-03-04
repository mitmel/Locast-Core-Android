package edu.mit.mel.locast.mobile.data;

import android.net.Uri;

public class ShotList extends JsonSyncableItem {
	public static final String
		_DIRECTION   = "direction",
		_DURATION    = "duration",
		_PARENT_ID   = "parent_id",
		_LIST_IDX    = "list_idx",
		_HARD_DURATION  = "hard_duration"
		;

	public final static String PATH = "shotlist";
	public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_URI,
		_DIRECTION,
		_DURATION,
		_HARD_DURATION,
		_PARENT_ID,
		_LIST_IDX
	};

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}
	public static Uri getProjectUri(Uri shotlistUri){
		return MediaProvider.removeLastPathSegment(shotlistUri);
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	public static final SyncMap SYNC_MAP = new SyncMap();

	static {
		SYNC_MAP.put(_DURATION,  new SyncFieldMap("duration", SyncFieldMap.DURATION));
		SYNC_MAP.put(_HARD_DURATION,  new SyncFieldMap("hard_duration", SyncFieldMap.BOOLEAN, SyncFieldMap.FLAG_OPTIONAL));
		SYNC_MAP.put(_DIRECTION, new SyncFieldMap("direction", SyncFieldMap.STRING));
		SYNC_MAP.put(_PUBLIC_URI, new SyncFieldMap("id", SyncFieldMap.STRING, SyncFieldMap.SYNC_FROM));
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

}
