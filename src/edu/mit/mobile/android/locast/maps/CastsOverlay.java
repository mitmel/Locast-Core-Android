package edu.mit.mobile.android.locast.maps;
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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.google.android.maps.OverlayItem;
import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.itineraries.LocatableItemOverlay;

public class CastsOverlay extends LocatableItemOverlay {
	private int mTitleCol, mDescriptionCol;
	private final Drawable mCastDrawable;

	public static final String[] CASTS_OVERLAY_PROJECTION = ArrayUtils.concat(LOCATABLE_ITEM_PROJECTION,
 new String[] { Cast._TITLE, Cast._DESCRIPTION });

	public CastsOverlay(Context context) {
		super(boundCenterBottom(context.getResources().getDrawable(R.drawable.ic_map_community)));
		final Resources res = context.getResources();
		mCastDrawable = boundCenterBottom(res.getDrawable(R.drawable.ic_map_community));
	}

	@Override
	protected void updateCursorCols() {
		super.updateCursorCols();
		if (mLocatableItems != null){
			mTitleCol = mLocatableItems.getColumnIndex(Cast._TITLE);
			mDescriptionCol = mLocatableItems.getColumnIndex(Cast._DESCRIPTION);
		}
	}

	@Override
	protected OverlayItem createItem(int i){
		mLocatableItems.moveToPosition(i);

		final OverlayItem item = new OverlayItem(getItemLocation(mLocatableItems),
				mLocatableItems.getString(mTitleCol), mLocatableItems.getString(mDescriptionCol));

		item.setMarker(mCastDrawable);
		return item;
	}
}
