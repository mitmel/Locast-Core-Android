package edu.mit.mobile.android.locast.itineraries;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.google.android.maps.OverlayItem;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Cast;

public class CastsOverlay extends LocatableItemOverlay {
	private int mOfficialCol, mTitleCol, mDescriptionCol;
	private final Drawable mOfficialCastDrawable;
	private final Drawable mCommunityCastDrawable;

	public CastsOverlay(Context context) {
		super(context);
		final Resources res = context.getResources();
		mOfficialCastDrawable = boundCenterBottom(res.getDrawable(R.drawable.ic_map_official));
		mCommunityCastDrawable = boundCenterBottom(res.getDrawable(R.drawable.ic_map_community));
	}

	@Override
	protected void updateCursorCols() {
		super.updateCursorCols();
		if (mLocatableItems != null){
			mTitleCol = mLocatableItems.getColumnIndex(Cast._TITLE);
			mDescriptionCol = mLocatableItems.getColumnIndex(Cast._DESCRIPTION);
			mOfficialCol =  mLocatableItems.getColumnIndex(Cast._OFFICIAL);
		}
	}

	@Override
	protected OverlayItem createItem(int i){
		mLocatableItems.moveToPosition(i);

		final OverlayItem item = new OverlayItem(getGeoPoint(mLocatableItems),
				mLocatableItems.getString(mTitleCol), mLocatableItems.getString(mDescriptionCol));

		if (mLocatableItems.getInt(mOfficialCol) != 0){
			item.setMarker(mOfficialCastDrawable);
		}else{
			item.setMarker(mCommunityCastDrawable);
		}
		return item;
	}
}
