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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;

public abstract class AbsTag implements BaseColumns {

    @DBColumn(type = TextColumn.class, unique = true, notnull = true)
    public static final String COL_NAME = "name";

    public static final String[] DEFAULT_PROJECTION = new String[] { _ID, COL_NAME };

    public final static String TAG_DELIM = ",";

    public final static String TAGS_SPECIAL_CV_KEY = "tags";

    /**
     * Given a tag query string, return the set of tags it represents.
     *
     * @param tagQuery
     * @return
     * @see #toTagQuery(Collection)
     */
    public static Set<String> toSet(String tagQuery){
        final String[] tmpList = tagQuery.split(TAG_DELIM);
        for (int i = 0; i < tmpList.length; i++){
            tmpList[i] = Uri.decode(tmpList[i]);
        }
        return new HashSet<String>(Arrays.asList(tmpList));
    }

    /**
     *
     * @param tags a collection of tags
     * @return a query string representing those tags
     * @see #toSet(String)
     */
    public static String toTagQuery(Collection<String> tags){
        final List<String> tempList = new Vector<String>(tags.size());
        for (final String s : tags){
            // escape all of the delimiters in the individual strings
            tempList.add(Uri.encode(s));
        }
        return TextUtils.join(TAG_DELIM, tags);
    }
}
