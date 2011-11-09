package edu.mit.mobile.android.locast.maps;

import java.util.ArrayList;

import org.osmdroid.ResourceProxy;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayItem.HotspotPlace;

import android.content.Context;
import android.graphics.drawable.Drawable;
import edu.mit.mobile.android.locast.ver2.R;

public class CastLocationOverlay extends ItemizedIconOverlay<OverlayItem> {
	private OverlayItem mOverlayItem;
	private final Context mContext;

	public CastLocationOverlay(Context context,
			OnItemGestureListener<OverlayItem> pOnItemGestureListener,
			ResourceProxy pResourceProxy) {
		super(new ArrayList<OverlayItem>(), pResourceProxy.getDrawable(ResourceProxy.bitmap.marker_default),
				pOnItemGestureListener, pResourceProxy);
		mContext = context;

	}

	public Drawable getDefaultMarker(){
		return boundToHotspot(mContext.getResources().getDrawable(R.drawable.ic_map_cast_location), HotspotPlace.CENTER);
	}

	public void setLocation(GeoPoint location) {
		mOverlayItem = new OverlayItem("cast", "", location);
		mOverlayItem.setMarker(getDefaultMarker());
		removeAllItems();
		addItem(mOverlayItem);

	}
}