package edu.mit.mel.locast.mobile.data;

import java.io.IOException;
import java.util.HashMap;

import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mel.locast.mobile.data.JsonSyncableItem.SyncItem;

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
	 * Hook called after an item has been updated on the server.
	 * @param uri Local URI pointing to the newly-updated item.
	 * @throws SyncException
	 * @throws IOException
	 */
	public void onUpdateItem(Context context, Uri uri, JSONObject item) throws SyncException, IOException {}

	/**
	 * Called just before an item is sync'd.
	 * @param c Cursor pointing to the given item.
	 *
	 * @throws SyncException
	 */
	public void onPreSyncItem(ContentResolver cr, Uri uri, Cursor c) throws SyncException {}

	/**
	 * Hook called after an item has been synchronized on the server. Called each time the sync request is made.
	 * @param uri Local URI pointing to the item.
	 * @throws SyncException
	 * @throws IOException
	 */
	public void onPostSyncItem(Context context, Uri uri, JSONObject item) throws SyncException, IOException {}
}
