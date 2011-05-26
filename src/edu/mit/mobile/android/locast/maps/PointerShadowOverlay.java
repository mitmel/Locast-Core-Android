package edu.mit.mobile.android.locast.maps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.ver2.R;

/**
 * A map overlay that draws a pointer with a shadow on top of the map, pointing
 * down to a specific geo coordinate.
 *
 * @author steve
 *
 */
public class PointerShadowOverlay extends Overlay {
	private final Drawable mHorizontal, mPointer;
	private GeoPoint mGeoPoint;

	public PointerShadowOverlay(Context context) {
		mHorizontal = context.getResources().getDrawable(
				R.drawable.map_overshadow_horizontal);
		mPointer = context.getResources().getDrawable(
				R.drawable.map_overshadow_pointer);
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

		// short-circuit and don't draw the pointer if the point is missing.
		if (mGeoPoint == null) {
			mHorizontal.setBounds(0, 0, canvas.getWidth(),
					mHorizontal.getIntrinsicHeight());
			mHorizontal.draw(canvas);
			return;
		}

		mapView.getProjection().toPixels(mGeoPoint, p);

		final int pos = p.x;
		final int halfPointer = mPointer.getIntrinsicWidth() / 2;

		mHorizontal.setBounds(0, 0, pos - halfPointer,
				mHorizontal.getIntrinsicHeight());
		mHorizontal.draw(canvas);

		mPointer.setBounds(pos - halfPointer, 0, pos + halfPointer,
				mPointer.getIntrinsicHeight());
		mPointer.draw(canvas);

		mHorizontal.setBounds(pos + halfPointer, 0, canvas.getWidth(),
				mHorizontal.getIntrinsicHeight());
		mHorizontal.draw(canvas);

	}
}