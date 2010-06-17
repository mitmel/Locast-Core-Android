package edu.mit.mel.locast.mobile.data;

import java.util.HashMap;

import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncItem;
import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncMap;

public class Favoritable {
	public interface Columns {
		public static final String 
			_FAVORITED = "favorited";
	}
	
	public static final HashMap<String, SyncItem> SYNC_MAP = new HashMap<String, SyncItem>();
	
	static {
		SYNC_MAP.put(Columns._FAVORITED, new JsonSyncableItem.SyncMap("favorited", SyncMap.BOOLEAN, SyncMap.SYNC_FROM));
	}
}
