package edu.mit.mobile.android.locast.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
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
import android.database.Cursor;

/**
 * DB entry for a comment. Also contains a sync mapping for publishing
 * to the network.
 *
 * @author I040854
 */

public abstract class AbsComment extends JsonSyncableItem {
    public final static String DEFAULT_SORT_BY = COL_MODIFIED_DATE + " DESC";

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
            COL_PUBLIC_URL,
            _AUTHOR,
            _AUTHOR_ICON,
            COL_MODIFIED_DATE,
            _PARENT_ID,
            _PARENT_CLASS,
            _DESCRIPTION,
            _COMMENT_NUMBER};

    public AbsComment(Cursor c) {
        super(c);
    }

    public static final SyncMap SYNC_MAP = new ItemSyncMap();

    public static class ItemSyncMap extends JsonSyncableItem.ItemSyncMap {

        /**
         *
         */
        private static final long serialVersionUID = -8410267481565950832L;

        public ItemSyncMap() {
            super();

            final SyncMap author = new SyncMap();
            author.put(_AUTHOR, new SyncFieldMap("username", SyncFieldMap.STRING));
            author.put(_AUTHOR_ICON, new SyncFieldMap("icon", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL | SyncItem.SYNC_BOTH));
            put("_author_object", new SyncMapChain("author", author, SyncItem.SYNC_FROM));

            remove(COL_CREATED_DATE);
            put(COL_MODIFIED_DATE, new SyncFieldMap("created", SyncFieldMap.DATE, SyncItem.SYNC_FROM)); // comments only have a creation date.
            put(_DESCRIPTION,   new SyncFieldMap("content", SyncFieldMap.STRING));
        }
    }
}
