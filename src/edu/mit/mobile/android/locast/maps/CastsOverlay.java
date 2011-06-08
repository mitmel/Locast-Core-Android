package edu.mit.mobile.android.locast.maps;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.google.android.maps.OverlayItem;
import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.itineraries.LocatableItemOverlay;

public class CastsOverlay extends LocatableItemOverlay {
	private int mOfficialCol, mTitleCol, mDescriptionCol;
	private final Drawable mOfficialCastDrawable;
	private final Drawable mCommunityCastDrawable;

	public static final String[] CASTS_OVERLAY_PROJECTION = ArrayUtils.concat(LOCATABLE_ITEM_PROJECTION,
			new String[]{Cast._TITLE, Cast._DESCRIPTION, Cast._OFFICIAL});

	public CastsOverlay(Context context) {
		super(boundCenterBottom(context.getResources().getDrawable(R.drawable.ic_map_community)));
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

		final OverlayItem item = new OverlayItem(getItemLocation(mLocatableItems),
				mLocatableItems.getString(mTitleCol), mLocatableItems.getString(mDescriptionCol));

		if (mLocatableItems.getInt(mOfficialCol) != 0){
			item.setMarker(mOfficialCastDrawable);
		}else{
			item.setMarker(mCommunityCastDrawable);
		}
		return item;
	}
}
