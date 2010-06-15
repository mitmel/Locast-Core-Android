package edu.mit.mel.locast.mobile.data;

import java.util.HashMap;
import java.util.Map;

import android.net.Uri;

public class CastMedia extends JsonSyncableItem {
	public final static String 
		_MEDIA_URL    = "url",
		_SCREENSHOT   = "screenshot",
		_MIME_TYPE    = "mimetype",
		_DURATION     = "duration",
		_PREVIEW_URL  = "preview_url",
		_PARENT_ID    = "parent_id",
		_LIST_IDX     = "list_idx"
		;
	public final static String PATH = "cast_media";
	public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	public final static String DEFAULT_SORT = _LIST_IDX + " ASC";
	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_ID,
		_MODIFIED_DATE,
		_SCREENSHOT,
		_MEDIA_URL,
		_MIME_TYPE,
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

	@Override
	public Map<String, SyncItem> getSyncMap() {
		
		return SYNC_MAP;
	}

	public final static Map<String, SyncItem> SYNC_MAP = new HashMap<String, SyncItem>();
	
	static {
		
		SYNC_MAP.put(_PUBLIC_ID, 	new SyncMap("id", SyncMap.INTEGER, SyncMap.SYNC_FROM));
		SYNC_MAP.put(_MODIFIED_DATE, new SyncMap("modified", SyncMap.DATE, true, SyncMap.SYNC_FROM));
		SYNC_MAP.put(_SCREENSHOT, 	new SyncMap("screenshot", SyncMap.STRING));
		SYNC_MAP.put(_MEDIA_URL, 	new SyncMap("url", SyncMap.STRING));
		SYNC_MAP.put(_MIME_TYPE, 	new SyncMap("mime_type", SyncMap.STRING));
		// TODO SYNC_MAP.put(_PARENT_ID,     new SyncMap("", type));
		
	}
}
