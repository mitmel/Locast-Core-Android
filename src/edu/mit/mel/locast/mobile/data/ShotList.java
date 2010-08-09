package edu.mit.mel.locast.mobile.data;

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

	public static final SyncMap SYNC_MAP = new SyncMap();

	static {
		SYNC_MAP.put(_DURATION,  new SyncFieldMap("duration", SyncFieldMap.DURATION));
		SYNC_MAP.put(_DIRECTION, new SyncFieldMap("direction", SyncFieldMap.STRING));
		SYNC_MAP.put(_PUBLIC_ID, new SyncFieldMap("id", SyncFieldMap.INTEGER, SyncFieldMap.SYNC_FROM));
	}

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

}
