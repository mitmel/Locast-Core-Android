package edu.mit.mobile.android.locast.ver2.casts;

import java.util.List;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4_map.app.MapFragmentActivity;
import android.view.View;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.maps.MapsUtils;
import edu.mit.mobile.android.locast.maps.PointerShadowOverlay;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.itineraries.LocatableItemOverlay;

/**
 * Handles the creation of the map pointer shadow overlay. provides
 * {@link #setPointer(GeoPoint)} to set the geo coordinate that the pointer
 * points to.
 *
 * Make sure to inflate a view with a map whose ID is
 *
 * <code>R.id.map</code>
 *
 * before calling {@link #initOverlays()}.
 *
 * @author steve
 *
 */
abstract public class LocatableDetail extends MapFragmentActivity {

	private MapView mMapView;
	private PointerShadowOverlay mShadowOverlay;
	private LocatableItemOverlay mLocatableItemOverlay;

	public static final int DEFAULT_ZOOM_LEVEL = 15;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

	}

	protected abstract LocatableItemOverlay createItemOverlay();

	protected void initOverlays() {
		mMapView = (MapView) findViewById(R.id.map);
		mShadowOverlay = new PointerShadowOverlay(this);
		mLocatableItemOverlay = createItemOverlay();
		final List<Overlay> overlays = mMapView.getOverlays();
		overlays.add(mLocatableItemOverlay);
		overlays.add(mShadowOverlay);

		mMapView.setVisibility(View.VISIBLE);
	}

	public void setPointer(GeoPoint geoPoint) {
		mShadowOverlay.setPointer(geoPoint);
	}

	protected void setPointerFromCursor(Cursor c, MapController mc){
		setPointerFromCursor(c, mc, DEFAULT_ZOOM_LEVEL);
	}

	private GeoPoint mCurrentPointer;

	protected void setPointerFromCursor(Cursor c, MapController mc, int zoomLevel){
		final GeoPoint gp = MapsUtils.getGeoPoint(c);
		if (mCurrentPointer == null || !mCurrentPointer.equals(gp)){
			setPointer(gp);
			mc.setCenter(gp);
			mc.setZoom(zoomLevel);
			mCurrentPointer = gp;
		}
	}
}
