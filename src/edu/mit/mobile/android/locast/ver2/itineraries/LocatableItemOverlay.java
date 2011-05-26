package edu.mit.mobile.android.locast.ver2.itineraries;

import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.maps.MapsUtils;

abstract public class LocatableItemOverlay extends ItemizedOverlay<OverlayItem> {
	protected Cursor mLocatableItems;
	private int mLatCol, mLonCol;

	private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			populate();
		}
	};

	public LocatableItemOverlay(Drawable marker) {
		this(marker, null);
	}

	public LocatableItemOverlay(Drawable marker, Cursor items) {
		super(marker);

		mLocatableItems = items;

		populate();
	}

	public static Drawable boundCenterBottom(Drawable drawable){
		// why isn't this visible?
		return ItemizedOverlay.boundCenterBottom(drawable);
	}

	public void swapCursor(Cursor locatableItems){
		mLocatableItems = locatableItems;
		updateCursorCols();

		populate();
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
		populate();

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

	protected GeoPoint getItemLocation(Cursor item){
		return MapsUtils.getGeoPoint(item, mLatCol, mLonCol);
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
}