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
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
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
	
	public final static String SERVER_PATH = "/project/";
	

	
	public static final String 	
		_TITLE = "title",
		_DESCRIPTION = "description";

	public final static String[] PROJECTION = {
		_ID,
		_PUBLIC_ID,
		_MODIFIED_DATE,
		_TITLE,
		_AUTHOR,
		_DESCRIPTION,
		_PRIVACY,
};
	
	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}
	
	/* (non-Javadoc)
	 * 
	 * Map internal casts to external casts.
	 * 
	 * @see edu.mit.mel.locast.mobile.data.JsonSyncableItem#onPreSyncItem(android.content.ContentResolver, android.net.Uri, android.database.Cursor)
	 */
	@Override
	public void onPreSyncItem(ContentResolver cr, Uri uri, Cursor c) throws SyncException {

	}
	
	@Override
	public void onUpdateItem(Context context, Uri uri, JSONObject item) throws SyncException, IOException {
		OrderedList.onUpdate(context, uri, item, "shotlist", new ShotList(), ShotList.PATH);
	}

	@Override
	public Map<String, SyncItem> getSyncMap() {
		return SYNC_MAP;
	}
	
	public static final HashMap<String, SyncItem> SYNC_MAP = new HashMap<String, SyncItem>(TaggableItem.SYNC_MAP);
	
	static {
		SYNC_MAP.put(_DESCRIPTION, 		new SyncMap("description", SyncMap.STRING));
		SYNC_MAP.put(_TITLE, 			new SyncMap("title", SyncMap.STRING));
		SYNC_MAP.putAll(Locatable.SYNC_MAP);
		SYNC_MAP.putAll(Favoritable.SYNC_MAP);
		SYNC_MAP.put("_shotlist",   new OrderedList.SyncMap("shotlist", true, new ShotList(), ShotList.PATH));
		
		SYNC_MAP.remove(_PRIVACY);
	}

}
