package edu.mit.mobile.android.maps;
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

import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.Point;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * Shows a single path, with an optional outline.
 *
 * @author steve
 *
 */
public class PathOverlay extends Overlay {
	@SuppressWarnings("unused")
	private static final String TAG = PathOverlay.class.getSimpleName();
	private final Paint mPathPaint;
	private final Paint mPathPaintOutline;
	private PathEffect mPathEffect;
	private List<GeoPoint> mPathGeo = new LinkedList<GeoPoint>();
	private Path mPath;
	private final boolean animate = false;

	private boolean mShowOutline = true;

	private boolean mBoundsDirty = true;


	public PathOverlay(Context context){
		this(context, null);
	}

	public PathOverlay(Context context, Paint pathPaint) {
		if (pathPaint != null){
			mPathPaint = pathPaint;
		}else{
			// TODO bring this to XML somehow
			mPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			mPathPaint.setColor(Color.rgb(0x2c, 0xb2, 0xb2));
			mPathPaint.setStyle(Paint.Style.STROKE);

			mPathEffect = new DashPathEffect (new float[]{15, 8}, 0);
			mPathPaint.setPathEffect(mPathEffect);
			mPathPaint.setStrokeWidth(5);
			mPathPaint.setStrokeJoin(Paint.Join.ROUND);
			mPathPaint.setStrokeCap(Paint.Cap.ROUND);
		}

		mPathPaintOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPathPaintOutline.setColor(Color.argb(192, 255, 255, 255));
		mPathPaintOutline.setStyle(Paint.Style.STROKE);

		mPathPaintOutline.setStrokeWidth(mPathPaint.getStrokeWidth() * 2);
		mPathPaintOutline.setStrokeJoin(Paint.Join.ROUND);
		mPathPaintOutline.setStrokeCap(Paint.Cap.ROUND);
	}

	/**
	 * Add a point to the path.
	 *
	 * @param latitudeE6
	 * @param longitudeE6
	 */
	public void addPoint(int latitudeE6, int longitudeE6){
		mPathGeo.add(new GeoPoint(latitudeE6, longitudeE6));
		mBoundsDirty = true;
	}

	/**
	 * Add a point to the path.
	 *
	 * @param point
	 */
	public void addPoint(GeoPoint point){
		mPathGeo.add(point);
		mBoundsDirty = true;
	}

	/**
	 * Clear the path.
	 */
	public void clearPath(){
		mPathGeo.clear();
		mBoundsDirty = true;
	}

	public void setPath(List<GeoPoint> path){
		mPathGeo = path;
		mBoundsDirty = true;
	}

	/**
	 * Adds a white/translucent outline around your path to improve visibility.
	 *
	 * @param show show the outline
	 */
	public void setShowOutline(boolean show){
		mShowOutline = show;
	}

	private int maxLat, minLat;
	private int maxLon, minLon;

	private void updatePath(Projection projection){
		mPath = new Path();
		final Point p = new Point();

		boolean first = true;
		for (final GeoPoint gp : mPathGeo){
			projection.toPixels(gp, p);
			if (first){
				mPath.moveTo(p.x, p.y);
				first = false;
			}else{

				mPath.lineTo(p.x, p.y);
			}
		}
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (!shadow){
			if (animate){
				mPathEffect = new DashPathEffect (new float[]{20, 20}, ((System.currentTimeMillis() / 10) % 400)/10.0f);
				mPathPaint.setPathEffect(mPathEffect);
			}
			updatePath(mapView.getProjection());
			if (mShowOutline){
				canvas.drawPath(mPath, mPathPaintOutline);
			}
			canvas.drawPath(mPath, mPathPaint);
			if (animate) {
				mapView.postInvalidateDelayed(50);
			}
		}
	};

	private void computeBounds(){
		if (!mBoundsDirty){
			return;
		}

		maxLat = minLat = mPathGeo.get(0).getLatitudeE6();
		maxLon = minLon = mPathGeo.get(0).getLongitudeE6();

		int lat, lon;
		for (final GeoPoint gp : mPathGeo){
			lat = gp.getLatitudeE6();
			lon = gp.getLongitudeE6();
			maxLat = Math.max(maxLat, lat);
			minLat = Math.min(minLat, lat);

			maxLon = Math.max(maxLon, lon);
			minLon = Math.min(minLon, lon);
		}
		mBoundsDirty = false;
	}

	public int getLatSpanE6(){
		if (mPathGeo.size() == 0){
			return 0;
		}

		computeBounds();

		return Math.abs(maxLat - minLat);
	}

	public int getLonSpanE6(){
		if (mPathGeo.size() == 0){
			return 0;
		}

		computeBounds();

		return Math.abs(maxLon - minLon);
	}

	/**
	 * this does not work properly when crossing -180/180 boundaries.
	 *
	 * @return the center point of the path according to its bounds or null if there are no points on the path.
	 */
	public GeoPoint getCenter() {
		if (mPathGeo.size() == 0){
			return null;
		}

		computeBounds();

		return new GeoPoint((maxLat - minLat)/2 + minLat, (maxLon - minLon)/2 + minLon);
	}
}
