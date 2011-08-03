package edu.mit.mobile.android.locast.ver2.events;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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
import android.text.format.DateUtils;
import android.widget.TextView;
import edu.mit.mobile.android.imagecache.SimpleThumbnailCursorAdapter;
import edu.mit.mobile.android.locast.data.Event;

public class EventCursorAdapter extends SimpleThumbnailCursorAdapter {

	private final Context mContext;

	private int mStartCol, mEndCol;
	private int mEventDateViewID = -1;

	public EventCursorAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to, int[] imageIDs, int flags) {
		super(context, layout, c, from, to, imageIDs, flags);

		 mContext = context;
		 for (int i = 0; i < from.length; i++){
			 if (from[i].equals(Event._START_DATE)){
				 mEventDateViewID = to[i];
				 break;
			 }
		 }
		 updateColumns(c);
	}

	@Override
	public void changeCursorAndColumns(Cursor c, String[] from, int[] to) {
		super.changeCursorAndColumns(c, from, to);
		updateColumns(c);
	}

	@Override
	public void changeCursor(Cursor cursor) {
		super.changeCursor(cursor);
		updateColumns(cursor);
	}

	@Override
	public Cursor swapCursor(Cursor c) {
		final Cursor retval = super.swapCursor(c);

		updateColumns(c);
		return retval;
	}


	private void updateColumns(Cursor c){
		if (c != null){
			mStartCol = c.getColumnIndex(Event._START_DATE);
			mEndCol  = c.getColumnIndex(Event._END_DATE);
		}else{
			mStartCol = -1;
			mEndCol = -1;
		}
	}

	private String formatEventRange(Cursor c){
		final long start = c.getLong(mStartCol),
		end = c.getLong(mEndCol);

		return DateUtils.formatDateRange(mContext, start, end, DateUtils.FORMAT_SHOW_TIME|DateUtils.FORMAT_SHOW_DATE);
	}

	@Override
	public void setViewText(TextView v, String text) {
		// date format
		if (mEventDateViewID == v.getId()){
			super.setViewText(v, formatEventRange(mCursor));
		}else{
			super.setViewText(v, text);
		}
	}
}
