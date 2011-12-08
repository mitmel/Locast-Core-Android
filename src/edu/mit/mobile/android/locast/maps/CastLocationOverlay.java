package edu.mit.mobile.android.locast.maps;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

import edu.mit.mobile.android.locast.ver2.R;

public class CastLocationOverlay extends ItemizedOverlay<OverlayItem> {
	private OverlayItem mOverlayItem;
	private final Context mContext;

	public CastLocationOverlay(Context context) {
		super(context.getResources().getDrawable(R.drawable.map_marker_user_cast));

		mContext = context;
		this.populate();

	}

	public Drawable getDefaultMarker(){
		return boundCenterBottom(mContext.getResources().getDrawable(R.drawable.ic_map_cast_location));
	}

	public void setLocation(GeoPoint location) {
		mOverlayItem = new OverlayItem(location, "", "cast");
		mOverlayItem.setMarker(getDefaultMarker());
		this.populate();
	}

	@Override
	protected OverlayItem createItem(int i) {
		return mOverlayItem;
	}

	@Override
	public int size() {
		return mOverlayItem == null ? 0 : 1;
	}
}