package edu.mit.mobile.android.locast.data;
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



import android.net.Uri;
import edu.mit.mobile.android.content.column.BooleanColumn;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;

public class Favoritable {
    public interface Columns {
        @DBColumn(type = BooleanColumn.class)
        public static final String _FAVORITED = "favorited";
    }

    public static final String QUERY_PARAM = "favorited";

    private static final String
        TRUE = "true",
        FALSE = "false";

    public static final SyncMap SYNC_MAP = new SyncMap();

    public static Uri getFavoritedUri(Uri uri, boolean favorited){
        return uri.buildUpon().appendQueryParameter(QUERY_PARAM, favorited ? TRUE : FALSE).build();
    }

    public static Boolean decodeFavoritedUri(Uri uri){
        final String favorited = uri.getQueryParameter(QUERY_PARAM);
        if (favorited == null){
            return null;

        }else if (TRUE.equals(favorited)){
            return true;

        }else if (FALSE.equals(favorited)){
            return false;
        }else{
            throw new IllegalArgumentException("unhandled value for favorited paramter");
        }
    }

    static {
        SYNC_MAP.put(Columns._FAVORITED, new JsonSyncableItem.SyncFieldMap("is_favorite", SyncFieldMap.BOOLEAN, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
    }
}
