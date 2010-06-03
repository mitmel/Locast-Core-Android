package edu.mit.mel.locast.mobile.casts;
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
import android.content.Context;
import android.database.Cursor;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;

public class CastCursorAdapter extends SimpleCursorAdapter {
	private final static String[] from = new String[] {Cast.THUMBNAIL_URI, Cast.AUTHOR, Cast.TITLE, Cast.DESCRIPTION};
	private final static int[] to = new int[] {R.id.media_thumbnail, R.id.author, android.R.id.text1, android.R.id.text2};
	public final static int[] IMAGE_IDS = {R.id.media_thumbnail};
	
	public final static String[] projection = {
			Cast._ID,
			Cast.AUTHOR,
			Cast.TITLE,
			Cast.DESCRIPTION,
			Cast.THUMBNAIL_URI
		};
	
	public CastCursorAdapter(Context context, Cursor c) {
		super(context, R.layout.browse_content_item, c, from, to);
	}
	
	@Override
	public void setViewImage(ImageView v, String value) {
		if (value != null && value.length() > 0){
			v.setTag(value);
		}else{
			v.setTag(null);
			v.setImageResource(R.drawable.cast_placeholder);
		}
	}
}
