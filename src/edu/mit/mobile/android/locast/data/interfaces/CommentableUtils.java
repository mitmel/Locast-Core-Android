package edu.mit.mobile.android.locast.data.interfaces;

/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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

import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;
import edu.mit.mobile.android.locast.data.SyncMap;

public class CommentableUtils {
    public static final SyncMap SYNC_MAP = new CommentableSyncMap();

    public static class CommentableSyncMap extends SyncMap{
        /**
         *
         */
        private static final long serialVersionUID = 5427496798582473851L;

        public CommentableSyncMap() {
            put(Commentable.COL_COMMENT_DIR_URI, new JsonSyncableItem.SyncFieldMap("comments",
                    SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
        }
    }
}
