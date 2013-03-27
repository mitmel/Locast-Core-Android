package edu.mit.mobile.android.locast.data.tags;
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

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.ContentProviderClient;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import edu.mit.mobile.android.content.m2m.M2MManager;
import edu.mit.mobile.android.locast.data.SyncMap;

/**
 * DB entry for an item that can be tagged.
 *
 * @author stevep
 *
 */
public class Taggable {

    public static final String PATH = "tags";
    public static final String QUERY_PARAMETER_TAGS = PATH;

    private static final String[] TAG_PROJECTION = new String[] { Tag.COL_NAME };

    public interface Columns {
        public static final M2MManager TAGS = new M2MManager(Tag.class);
    }

    public static Uri getTagPath(Uri item) {
        return item.buildUpon().appendPath(PATH).build();
    }

    public static Uri getItemMatchingTags(Uri contentDir, String... tag) {

        return contentDir.buildUpon()
                .appendQueryParameter(QUERY_PARAMETER_TAGS, TextUtils.join(",", tag))
                .build();
    }

    public static Set<String> getTags(ContentProviderClient cpc, Uri tagDir) throws RemoteException {
        final Set<String> tags = new HashSet<String>();

        final Cursor c = cpc.query(tagDir, TAG_PROJECTION, null, null, null);

        try {
            final int tagNameCol = c.getColumnIndexOrThrow(Tag.COL_NAME);

            while (c.moveToNext()) {
                tags.add(c.getString(tagNameCol));
            }
        } finally {
            c.close();
        }

        return tags;
    }

    public static class TaggableSyncMap extends SyncMap {
        /**
         *
         */
        private static final long serialVersionUID = 8893141554402784876L;

        public TaggableSyncMap(M2MManager target) {
            put(Tag.TAGS_SPECIAL_CV_KEY, new TagSyncField("tags", target));
        }
    }

    public static String toFlattenedString(JSONArray ja) throws JSONException {
        final int len = ja.length();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(Tag.TAG_DELIM);
            }
            sb.append(ja.getString(i));
        }
        return sb.toString();
    }
}
