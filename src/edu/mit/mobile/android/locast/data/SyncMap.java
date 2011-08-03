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
