package edu.mit.mel.locast.mobile.data;

import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncItem;

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
