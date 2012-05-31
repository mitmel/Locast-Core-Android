package edu.mit.mobile.android.locast.casts;
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

import java.util.List;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4_map.app.MapFragmentActivity;
import android.view.View;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.collections.LocatableItemOverlay;
import edu.mit.mobile.android.locast.maps.MapsUtils;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.maps.PointerShadow;
import edu.mit.mobile.android.maps.PointerShadowOverlay;

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
	private MapController mMapController;
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

		mMapController = mMapView.getController();
		mPointerShadow = (PointerShadow) findViewById(R.id.pointer_shadow);
		mShadowOverlay = new PointerShadowOverlay(this, mPointerShadow);
		mLocatableItemOverlay = createItemOverlay();
		mMapView.setBuiltInZoomControls(true);
		final List<Overlay> overlays = mMapView.getOverlays();
		overlays.add(mLocatableItemOverlay);
		overlays.add(mShadowOverlay);

		mMapView.setVisibility(View.VISIBLE);
	}

	public void setPointer(GeoPoint geoPoint) {
		mShadowOverlay.setPointer(geoPoint);
	}

	protected void setPointerFromCursor(Cursor c){
		setPointerFromCursor(c, DEFAULT_ZOOM_LEVEL);
	}

	private GeoPoint mCurrentPointer;

	protected void setPointerFromCursor(Cursor c, int zoomLevel){
		final GeoPoint gp = MapsUtils.getGeoPoint(c);
		if (mCurrentPointer == null || !mCurrentPointer.equals(gp)){
			setPointer(gp);
			mMapController.setCenter(gp);
			mMapController.setZoom(zoomLevel);
			mCurrentPointer = gp;
		}
	}
}
