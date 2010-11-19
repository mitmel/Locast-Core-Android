package edu.mit.mel.locast.mobile.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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

import org.json.JSONObject;

import android.content.Context;
import android.net.Uri;

/**
 * DB entry for a project. Also contains a sync mapping for publishing
 * to the network.
 *
 * @author stevep
 *
 */
public class Project extends TaggableItem implements Favoritable.Columns, Locatable.Columns {
	public final static String PATH = "projects";
	public final static Uri CONTENT_URI = Uri
			.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

	public final static String SERVER_PATH = "project/";

	public final static String SORT_ORDER_DEFAULT = _FAVORITED + " DESC," +  _MODIFIED_DATE + " DESC";

	public static final String
		_TITLE = "title",
		_DESCRIPTION = "description";

	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_URI,
		_MODIFIED_DATE,
		_CREATED_DATE,
		_TITLE,
		_AUTHOR,
		_DESCRIPTION,
		_LATITUDE,
		_LONGITUDE,
		_PRIVACY,
		_FAVORITED,
};

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}

	public static Uri getShotListUri(Uri projectUri){
		return Uri.withAppendedPath(projectUri, ShotList.PATH);
	}



	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static class ItemSyncMap extends TaggableItemSyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = -6270787415786212321L;

		public ItemSyncMap() {
			super();

			put(_DESCRIPTION, 		new SyncFieldMap("description", SyncFieldMap.STRING));
			put(_TITLE, 			new SyncFieldMap("title", SyncFieldMap.STRING));
			putAll(Locatable.SYNC_MAP);
			putAll(Favoritable.SYNC_MAP);
			put("_shotlist",   new OrderedList.SyncMapItem("shotlist", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_FROM, new ShotList(), ShotList.PATH));

			remove(_PRIVACY);
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri, JSONObject item, boolean updated) throws SyncException, IOException {
			super.onPostSyncItem(context, uri, item, updated);
			if (updated){
				OrderedList.onUpdate(context, uri, item, "shotlist", SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_FROM, new ShotList(), ShotList.PATH);
			}
		}
	}
}
