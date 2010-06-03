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
import java.util.HashMap;
import java.util.Map;

import android.net.Uri;

/**
 * DB entry for a comment. Also contains a sync mapping for publishing
 * to the network.
 * 
 * @author I040854
 */

public class Comment extends JsonSyncableItem {
	public final static String PATH = "comments";
	public final static Uri CONTENT_URI = Uri
			.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	public final static String DEFAULT_SORT_BY = MODIFIED_DATE + " DESC";
	
	public final static String SERVER_PATH = "comments/";
	
	public static final String 	
		AUTHOR = "author",
		AUTHOR_ICON = "author_icon",
		PARENT_ID    = "parentid",
		PARENT_CLASS = "parentclass",
		DESCRIPTION  = "description",
		COMMENT_NUMBER    = "comment_number";
	
	public final static String[] PROJECTION = {
			_ID,
			PUBLIC_ID,
			AUTHOR,
			AUTHOR_ICON,
			MODIFIED_DATE,
			PARENT_ID,
			PARENT_CLASS,
			DESCRIPTION,
			COMMENT_NUMBER};

	@Override
	public Uri getContentUri() {
		return CONTENT_URI;
	}

	@Override
	public String[] getFullProjection() {
		return PROJECTION;
	}
	
	@Override
	public Map<String, SyncItem> getSyncMap() {
		final Map<String, SyncItem> syncMap = new HashMap<String, SyncItem>();

		final Map<String, SyncItem> author = new HashMap<String, SyncItem>();
		author.put(AUTHOR, new SyncMap("username", SyncMap.STRING));
		author.put(AUTHOR_ICON, new SyncMap("icon", SyncMap.STRING, true));
		syncMap.put("author_object", new SyncMapChain("author", author, SyncItem.SYNC_FROM));
		
		syncMap.put(PUBLIC_ID, 		new SyncMap("id", SyncMap.INTEGER, SyncItem.SYNC_FROM));
		syncMap.put(MODIFIED_DATE,	new SyncMap("created", SyncMap.DATE, SyncItem.SYNC_FROM));
		syncMap.put(DESCRIPTION, 	new SyncMap("description", SyncMap.STRING));
		syncMap.put(COMMENT_NUMBER,	new SyncMap("title", SyncMap.STRING, SyncItem.SYNC_FROM));

		return syncMap;
	}
}
