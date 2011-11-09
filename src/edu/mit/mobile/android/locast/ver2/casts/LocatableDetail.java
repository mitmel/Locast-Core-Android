package edu.mit.mobile.android.locast.ver2.casts;
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

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.OverlayManager;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import edu.mit.mobile.android.locast.maps.MapsUtils;
import edu.mit.mobile.android.locast.maps.PointerShadow;
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
abstract public class LocatableDetail extends FragmentActivity {

	private MapView mMapView;
	private PointerShadow mPointerShadow;
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

		mPointerShadow = (PointerShadow) findViewById(R.id.pointer_shadow);
		mShadowOverlay = new PointerShadowOverlay(new DefaultResourceProxyImpl(this), mPointerShadow);
		mLocatableItemOverlay = createItemOverlay();
		final OverlayManager om = mMapView.getOverlayManager();

		om.add(mLocatableItemOverlay);
		om.add(mShadowOverlay);

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
