package edu.mit.mobile.android.locast.maps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

/**
 * A map overlay that draws a pointer with a shadow on top of the map, pointing
 * down to a specific geo coordinate.
 *
 * @author steve
 *
 */
public class PointerShadowOverlay extends Overlay {
	private GeoPoint mGeoPoint;

	private final PointerShadow mPointerShadow;

	public PointerShadowOverlay(Context context, PointerShadow pointerShadow) {
		mPointerShadow = pointerShadow;
	}

	public void setPointer(GeoPoint gp) {
		mGeoPoint = gp;
	}

	private final Point p = new Point();

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (shadow) {
			return;
		}
		if (mGeoPoint == null){
			return;
		}
		mapView.getProjection().toPixels(mGeoPoint, p);
		mPointerShadow.setOffset(p.x, p.y);
	}
}