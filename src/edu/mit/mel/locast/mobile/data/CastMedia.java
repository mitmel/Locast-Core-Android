package edu.mit.mel.locast.mobile.data;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import android.content.Context;
import android.net.Uri;

public class CastMedia extends JsonSyncableItem implements OrderedList.Columns{
	public final static String 
		_MEDIA_URL    = "url",
		_LOCAL_URI    = "local_uri",
		_SCREENSHOT   = "screenshot",
		_MIME_TYPE    = "mimetype",
		_DURATION     = "duration",
		_PREVIEW_URL  = "preview_url"
		;
	public final static String PATH = "cast_media";
	public final static Uri CONTENT_URI = Uri.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	
	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_ID,
		_MODIFIED_DATE,
		_MEDIA_URL,
		_LOCAL_URI,
		_SCREENSHOT,
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
	
	@Override
	public void onUpdateItem(Context context, Uri uri, JSONObject item)
			throws SyncException, IOException {
		super.onUpdateItem(context, uri, item);
		
		
	}

	public final static Map<String, SyncItem> SYNC_MAP = new HashMap<String, SyncItem>();
	
	static {
		SYNC_MAP.put(_PUBLIC_ID, 	new SyncMap("id", SyncMap.INTEGER,              SyncMap.SYNC_FROM));
		SYNC_MAP.put(_MODIFIED_DATE,new SyncMap("modified", SyncMap.DATE, true,     SyncMap.SYNC_FROM));
		SYNC_MAP.put(_SCREENSHOT, 	new SyncMap("screenshot", SyncMap.STRING, true, SyncMap.SYNC_FROM));
		SYNC_MAP.put(_PREVIEW_URL, 	new SyncMap("preview_url", SyncMap.STRING, true, SyncMap.SYNC_FROM));
		SYNC_MAP.put(_MEDIA_URL, 	new SyncMap("file_url", SyncMap.STRING,         SyncMap.SYNC_FROM));
		SYNC_MAP.put(_MIME_TYPE, 	new SyncMap("mime_type", SyncMap.STRING,        SyncMap.SYNC_FROM));
	}
}
