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
	public final static String DEFAULT_SORT_BY = _MODIFIED_DATE + " DESC";
	
	public final static String SERVER_PATH = "comments/";
	
	public static final String 	
		_AUTHOR = "author",
		_AUTHOR_ICON = "author_icon",
		_PARENT_ID    = "parentid",
		_PARENT_CLASS = "parentclass",
		_DESCRIPTION  = "description",
		_COMMENT_NUMBER    = "comment_number";
	
	public final static String[] PROJECTION = {
			_ID,
			_PUBLIC_ID,
			_AUTHOR,
			_AUTHOR_ICON,
			_MODIFIED_DATE,
			_PARENT_ID,
			_PARENT_CLASS,
			_DESCRIPTION,
			_COMMENT_NUMBER};

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
		author.put(_AUTHOR, new SyncMap("username", SyncMap.STRING));
		author.put(_AUTHOR_ICON, new SyncMap("icon", SyncMap.STRING, true));
		syncMap.put("author_object", new SyncMapChain("author", author, SyncItem.SYNC_FROM));
		
		syncMap.put(_PUBLIC_ID, 		new SyncMap("id", SyncMap.INTEGER, SyncItem.SYNC_FROM));
		syncMap.put(_MODIFIED_DATE,	new SyncMap("created", SyncMap.DATE, SyncItem.SYNC_FROM));
		syncMap.put(_DESCRIPTION, 	new SyncMap("content", SyncMap.STRING));

		return syncMap;
	}
}
