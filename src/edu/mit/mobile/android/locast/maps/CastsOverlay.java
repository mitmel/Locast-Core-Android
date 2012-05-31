package edu.mit.mobile.android.locast.maps;
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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.locast.collections.LocatableItemOverlay;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.ver2.R;

public class CastsOverlay extends LocatableItemOverlay {
	private int mTitleCol, mDescriptionCol;
	private final Drawable mCastDrawable;
	private final Context mContext;
	private int mIdCol;

	public static final String[] CASTS_OVERLAY_PROJECTION = ArrayUtils.concat(LOCATABLE_ITEM_PROJECTION,
 new String[] { Cast._ID, Cast._TITLE, Cast._DESCRIPTION });

	public CastsOverlay(Context context, MapView mapview) {
		super(boundCenter(context.getResources().getDrawable(R.drawable.ic_map_community)),
				mapview);
		final Resources res = context.getResources();
		mCastDrawable = boundCenterBottom(res.getDrawable(R.drawable.ic_map_community));
		mContext = context;
	}

	@Override
	protected void updateCursorCols() {
		super.updateCursorCols();
		if (mLocatableItems != null){
			mIdCol = mLocatableItems.getColumnIndex(Cast._ID);
			mTitleCol = mLocatableItems.getColumnIndex(Cast._TITLE);
			mDescriptionCol = mLocatableItems.getColumnIndex(Cast._DESCRIPTION);
		}
	}

	@Override
	protected boolean onBalloonTap(int index, OverlayItem item) {
		mLocatableItems.moveToPosition(index);
		final Cast cast = new Cast(mLocatableItems);
		mContext.startActivity(new Intent(Intent.ACTION_VIEW, cast.getCanonicalUri()));

		return true;
	}

	@Override
	protected OverlayItem createItem(int i){
		mLocatableItems.moveToPosition(i);

		final ComparableOverlayItem item = new ComparableOverlayItem(
				getItemLocation(mLocatableItems),
				mLocatableItems.getString(mTitleCol), mLocatableItems.getString(mDescriptionCol),
				mLocatableItems.getLong(mIdCol));

		item.setMarker(mCastDrawable);
		onCreateItem(item);
		return item;
	}
}
