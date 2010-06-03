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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.provider.BaseColumns;
import edu.mit.mel.locast.mobile.ListUtils;

public class Tag implements BaseColumns {
	public final static String _REF_ID   = "ref_id",
							   _REF_CLASS= "ref_class",
							   _NAME     = "name";
							   
	public final static String[] DEFAULT_PROJECTION = {_REF_ID, _REF_CLASS, _NAME};
	public final static String TAG_DELIM = ",";
	public final static String PATH = "tags";
	public final static Uri CONTENT_URI = Uri
			.parse("content://"+MediaProvider.AUTHORITY+"/"+PATH);
	
	private String name;
	private int count;
	
	public Tag(String name, int count){
		this.name = name;
		this.count = count;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	
	public static Set<String> toSet(String tagString){
		return new HashSet<String>(Arrays.asList(tagString.split(TAG_DELIM)));
	}
	
	public static String toTagString(Collection<String> tags){
		return ListUtils.join(tags, TAG_DELIM);
	}

	public void fromJSON(JSONObject item) throws JSONException,
			IOException {
		name = item.getString("name");
		if (item.has("count")){
			count = item.getInt("count");
		}else{
			count = item.getInt("rough_count");
		}
	}

	public JSONObject toJSON() throws JSONException {
		// TODO Auto-generated method stub
		return null;
	}
}
