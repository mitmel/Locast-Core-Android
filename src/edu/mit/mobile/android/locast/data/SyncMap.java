package edu.mit.mobile.android.locast.data;

import java.io.IOException;
import java.util.HashMap;

import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;

public class SyncMap extends HashMap<String, SyncItem> {
	/**
	 *
	 */
	private static final long serialVersionUID = 4817034517893809747L;

	public SyncMap() {

	}

	public SyncMap(SyncMap syncMap) {
		super(syncMap);
	}

	/**
	 * Called just before an item is sync'd.
	 * @param c Cursor pointing to the given item.
	 *
	 * @throws SyncException
	 */
	public void onPreSyncItem(ContentResolver cr, Uri uri, Cursor c) throws SyncException {}

	/**
	 * Hook called after an item has been synchronized on the server. Called each time the sync request is made.
	 * Make sure to call through when subclassing.
	 * @param uri Local URI pointing to the item.
	 * @param updated true if the item was updated during the sync.
	 * @throws SyncException
	 * @throws IOException
	 */
	public void onPostSyncItem(Context context, Uri uri, JSONObject item, boolean updated) throws SyncException, IOException {
		for (final SyncItem childItem: this.values()){
			childItem.onPostSyncItem(context, uri, item, updated);
		}
	}
}
