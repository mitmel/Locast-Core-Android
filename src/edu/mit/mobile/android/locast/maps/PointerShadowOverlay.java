package edu.mit.mobile.android.locast.maps;
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

import org.osmdroid.ResourceProxy;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import android.graphics.Canvas;
import android.graphics.Point;

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

	public PointerShadowOverlay(ResourceProxy resourceProxy, PointerShadow pointerShadow) {
		super(resourceProxy);
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
		
		mapView.getProjection().toPixels(mapView.getMapCenter(), p);
		mPointerShadow.setOffset(-p.x + (int)(mapView.getWidth() * 0.5), -p.y);
	}
}
