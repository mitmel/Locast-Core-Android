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
import edu.mit.mobile.android.utils.ListUtils;

public class Tag implements BaseColumns {
	public final static String _REF_ID   = "ref_id",
							   _REF_CLASS= "ref_class",
							   _NAME     = "name";

	public final static String[] DEFAULT_PROJECTION = {_REF_ID, _REF_CLASS, _NAME};
	public final static String TAG_DELIM = ",";
	public final static String PATH = "tags";
	public final static Uri CONTENT_URI = Uri
			.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);

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
		return ListUtils.join(tags, TAG_DELIM);
	}
}
