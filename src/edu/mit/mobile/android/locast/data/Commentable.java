package edu.mit.mobile.android.locast.data;

import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;

public class Commentable {
	public interface Columns {
		public static final String
			_COMMENT_DIR_URI = "comment_dir_uri";
	}

	public static final SyncMap SYNC_MAP = new SyncMap();

	static {
		SYNC_MAP.put(Columns._COMMENT_DIR_URI, new JsonSyncableItem.SyncFieldMap("comments_uri", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
	}
}
