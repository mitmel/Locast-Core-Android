package edu.mit.mobile.android.locast.data;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

public class OrderedList {
	public static final String TAG = OrderedList.class.getSimpleName();

	public static interface Columns {
		public final static String
			_LIST_IDX     = "list_idx",
			_PARENT_ID    = "parent_id";

		public final static String
			DEFAULT_SORT = _LIST_IDX + " ASC";
	}

	public static void onUpdate(Context context, Uri parentUri, JSONObject item, String remoteKey, int syncFlags, JsonSyncableItem listItem, String itemPath) throws SyncException {
		final SyncMap syncMap = new SyncMap();
		syncMap.put("_contents", new OrderedList.SyncMapOnUpdate(remoteKey, syncFlags, listItem, itemPath));

		try {
			Log.d(TAG, "trying to load ordered list for "+ parentUri+"; remoteKey: " + remoteKey+ "; item path: " + itemPath + "; item type: "+ listItem.getClass().getSimpleName());
			JsonSyncableItem.fromJSON(context, parentUri, item, syncMap);
		} catch (final Exception e1) {
			final SyncException e = new SyncException("Error loading list");
			e.initCause(e1);
			throw e;
		}
	}

	public static class SyncMapItem extends JsonSyncableItem.SyncCustom {
		private final JsonSyncableItem mListItem;
		private final String mItemPath;

		public SyncMapItem(String remoteKey, int flags, JsonSyncableItem listItem, String itemPath) {
			super(remoteKey, flags & ~SYNC_FROM);
			mListItem = listItem;
			mItemPath = itemPath;
		}
		public SyncMapItem(String remoteKey, JsonSyncableItem listItem, String itemPath) {
			this(remoteKey, SYNC_TO, listItem, itemPath);
		}

		@Override
		public ContentValues fromJSON(Context context, Uri parentItem,
				JSONObject item, String lProp) throws JSONException, NetworkProtocolException,
				IOException {
			return null;
		}

		@Override
		public JSONArray toJSON(Context context, Uri parentItem, Cursor c, String lProp)
				throws JSONException, NetworkProtocolException, IOException {
			Log.d(TAG, "main toJSON");

			final Cursor item_c = context.getContentResolver().query(Uri.withAppendedPath(parentItem, mItemPath), mListItem.getFullProjection(), null, null, null);
			final JSONArray ja = new JSONArray();
			for (item_c.moveToFirst(); !item_c.isAfterLast(); item_c.moveToNext()){
				ja.put(JsonSyncableItem.toJSON(context, parentItem, item_c, mListItem.getSyncMap()));
			}
			item_c.close();
			return ja;
		}
	}

	public static class SyncMapOnUpdate extends JsonSyncableItem.SyncCustom {
		private final JsonSyncableItem mListItem;
		private final String mItemPath;

		public SyncMapOnUpdate(String remoteKey, int flags, JsonSyncableItem listItem, String itemPath) {
			super(remoteKey, flags & ~SyncItem.SYNC_TO);
			mListItem = listItem;
			mItemPath = itemPath;
		}

		public SyncMapOnUpdate(String remoteKey, JsonSyncableItem listItem, String itemPath) {
			this(remoteKey, SYNC_FROM, listItem, itemPath);

		}

		@Override
		public JSONArray toJSON(Context context, Uri parentItem, Cursor c, String lProp)
		throws JSONException, NetworkProtocolException, IOException {
			return null;
		}

		@Override
		public ContentValues fromJSON(Context context, Uri parentItem, JSONObject item, String lProp)
		throws JSONException, NetworkProtocolException, IOException {
			Log.d(TAG, "onUpdateItem fromJSON");
			final JSONArray ja = item.getJSONArray(remoteKey);

			final ContentResolver cr = context.getContentResolver();

			for (int i = 0; i < ja.length(); i++){
				final ContentValues cv = JsonSyncableItem.fromJSON(context, null, ja.getJSONObject(i), mListItem.getSyncMap());

				cv.put(Columns._LIST_IDX, i);
				cv.put(Columns._PARENT_ID, ContentUris.parseId(parentItem));
				final Uri itemDirUri = Uri.withAppendedPath(parentItem, mItemPath);
				final int updated = cr.update(ContentUris.withAppendedId(itemDirUri, i), cv, null, null);
				// if the update failed, we should probably add it.
				if (updated == 0){
					cr.insert(itemDirUri, cv);
				}

			}
			return null;
		}
	}
}
