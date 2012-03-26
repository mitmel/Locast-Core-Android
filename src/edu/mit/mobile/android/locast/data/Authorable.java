package edu.mit.mobile.android.locast.data;

import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncMapChain;

public abstract class Authorable {

	public interface Columns {
		@DBColumn(type = TextColumn.class)
		public static final String _AUTHOR = "author";

		@DBColumn(type = TextColumn.class)
		public static final String _AUTHOR_URI = "author_uri";

	}

	public static final SyncMap SYNC_MAP = new SyncMap();

	static {

		final SyncMap authorSync = new SyncMap();
		authorSync.put(Columns._AUTHOR, new SyncFieldMap("display_name", SyncFieldMap.STRING,
				SyncItem.FLAG_OPTIONAL));
		authorSync.put(Columns._AUTHOR_URI, new SyncFieldMap("uri", SyncFieldMap.STRING));
		SYNC_MAP.put("_author", new SyncMapChain("author", authorSync, SyncItem.SYNC_FROM));
	}
}
