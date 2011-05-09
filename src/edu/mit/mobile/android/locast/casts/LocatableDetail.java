package edu.mit.mobile.android.locast.casts;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4_map.app.MapFragmentActivity;
import android.view.View;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.itineraries.LocatableItemOverlay;

abstract public class LocatableDetail extends MapFragmentActivity {

	private MapView mMapView;
	private ShadowOverlay mShadowOverlay;
	private LocatableItemOverlay mLocatableItemOverlay;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

	}

	protected abstract LocatableItemOverlay createItemOverlay();

	protected void initOverlays(){
		mMapView = (MapView) findViewById(R.id.map);
		mShadowOverlay = new ShadowOverlay(this);
		mLocatableItemOverlay = createItemOverlay();
		final List<Overlay> overlays = mMapView.getOverlays();
		overlays.add(mLocatableItemOverlay);
		overlays.add(mShadowOverlay);

		mMapView.setVisibility(View.VISIBLE);
	}

	public void setPointer(GeoPoint geoPoint){
		mShadowOverlay.setPointer(geoPoint);
	}

	private class ShadowOverlay extends Overlay {
		private final Drawable mHorizontal, mPointer;
		private GeoPoint mGeoPoint;

		public ShadowOverlay(Context context) {
			mHorizontal = context.getResources().getDrawable(R.drawable.map_overshadow_horizontal);
			mPointer = context.getResources().getDrawable(R.drawable.map_overshadow_pointer);
		}

		public void setPointer(GeoPoint gp){
			mGeoPoint = gp;
		}

		private final Point p = new Point();

		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			if (shadow){
				return;
			}

			// short-circuit and don't draw the pointer if the point is missing.
			if (mGeoPoint == null){
				mHorizontal.setBounds(0, 0, canvas.getWidth(), mHorizontal.getIntrinsicHeight());
				mHorizontal.draw(canvas);
				return;
			}

			mapView.getProjection().toPixels(mGeoPoint, p);

			final int pos = p.x;
			final int halfPointer = mPointer.getIntrinsicWidth() / 2;

			mHorizontal.setBounds(0, 0, pos - halfPointer, mHorizontal.getIntrinsicHeight());
			mHorizontal.draw(canvas);

			mPointer.setBounds(pos - halfPointer,0,pos + halfPointer, mPointer.getIntrinsicHeight());
			mPointer.draw(canvas);

			mHorizontal.setBounds(pos+halfPointer, 0, canvas.getWidth(), mHorizontal.getIntrinsicHeight());
			mHorizontal.draw(canvas);

		}
	}
}
