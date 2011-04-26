package edu.mit.mobile.android.locast.itineraries;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.location.Location;
import android.os.Handler;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Locatable;

abstract public class LocatableItemOverlay extends ItemizedOverlay<OverlayItem> {
	protected Cursor mLocatableItems;
	private int mLatCol, mLonCol;

	private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			refresh();
		}
	};

	public LocatableItemOverlay(Context context) {
		this(context, null);
	}

	public LocatableItemOverlay(Context context, Cursor casts) {
		super(boundCenterBottom(context.getResources().getDrawable(R.drawable.ic_map_community)));

		mLocatableItems = casts;
	}

	public void swapCursor(Cursor locatableItems){
		mLocatableItems = locatableItems;
		updateCursorCols();

		refresh();
	}

	public void onPause(){
		if (mLocatableItems != null){
			mLocatableItems.unregisterContentObserver(mContentObserver);
		}
	}

	public void onResume(){
		if (mLocatableItems != null){
			mLocatableItems.registerContentObserver(mContentObserver);
		}
	}

	public void changeCursor(Cursor locatableItems){
		final Cursor oldCursor = mLocatableItems;
		mLocatableItems = locatableItems;
		updateCursorCols();
		refresh();

		if (oldCursor != null && !oldCursor.isClosed()){
			oldCursor.close();
		}
	}

	protected void updateCursorCols(){
		if (mLocatableItems != null){
			mLatCol = mLocatableItems.getColumnIndex(Locatable.Columns._LATITUDE);
			mLonCol = mLocatableItems.getColumnIndex(Locatable.Columns._LONGITUDE);
		}
	}

	protected void refresh(){
		if (mLocatableItems != null){
			populate();
		}
	}

	protected GeoPoint getGeoPoint(Cursor item){
		final Location castLoc =  Locatable.toLocation(mLocatableItems, mLatCol, mLonCol);
		final GeoPoint castLocGP = new GeoPoint((int)(castLoc.getLatitude()*1E6), (int)(castLoc.getLongitude()*1E6));
		return castLocGP;
	}

	/**
	 * this does not work properly when crossing -180/180 boundaries.
	 *
	 * @see com.google.android.maps.ItemizedOverlay#getCenter()
	 */
	@Override
	public GeoPoint getCenter() {
		int maxLat, minLat;
		int maxLon, minLon;

		mLocatableItems.moveToFirst();
		final double[] latLon = new double[2];
		Locatable.toLocationArray(mLocatableItems, mLatCol, mLonCol, latLon);
		maxLat = minLat = (int)(latLon[0] * 1E6);
		maxLon = minLon = (int)(latLon[1] * 1E6);
		mLocatableItems.moveToNext();
		for (; !mLocatableItems.isAfterLast(); mLocatableItems.moveToNext()){
			Locatable.toLocationArray(mLocatableItems, mLatCol, mLonCol, latLon);
			maxLat = Math.max(maxLat, (int)(latLon[0] * 1E6));
			minLat = Math.min(minLat, (int)(latLon[0] * 1E6));

			maxLon = Math.max(maxLon, (int)(latLon[1] * 1E6));
			minLon = Math.min(minLon, (int)(latLon[1] * 1E6));
		}

		return new GeoPoint((maxLat - minLat)/2 + minLat, (maxLon - minLon)/2 + minLon);
	}

	@Override
	public int size() {
		if (mLocatableItems == null){
			return 0;
		}
		return mLocatableItems.getCount();
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (shadow){
			return;
		}
		super.draw(canvas, mapView, shadow);
	}
}