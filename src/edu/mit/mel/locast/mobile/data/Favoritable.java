package edu.mit.mel.locast.mobile.data;

import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncItem;

public class Favoritable {
	public interface Columns {
		public static final String
			_FAVORITED = "favorited";
	}

	public static final SyncMap SYNC_MAP = new SyncMap();

	static {
		SYNC_MAP.put(Columns._FAVORITED, new JsonSyncableItem.SyncFieldMap("favorited", SyncFieldMap.BOOLEAN, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
	}
}
