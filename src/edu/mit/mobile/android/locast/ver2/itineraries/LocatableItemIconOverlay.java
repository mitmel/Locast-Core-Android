package edu.mit.mobile.android.locast.ver2.itineraries;

import java.util.ArrayList;

import org.osmdroid.ResourceProxy;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.maps.MapsUtils;

public class LocatableItemIconOverlay extends ItemizedIconOverlay<OverlayItem> {
	
	protected Cursor mLocatableItems;
	private int mLatCol, mLonCol;

	public static final String[] LOCATABLE_ITEM_PROJECTION = {Locatable.Columns._LATITUDE, Locatable.Columns._LONGITUDE};

	private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			populate();
		}
	};
	
	public LocatableItemIconOverlay(Drawable marker,
			ItemizedIconOverlay.OnItemGestureListener<OverlayItem> onItemGestureListener,
			ResourceProxy resourceProxy) {	
		
		this(new ArrayList<OverlayItem>() , null, marker, onItemGestureListener, resourceProxy);
	}
	
	public LocatableItemIconOverlay(ArrayList<OverlayItem> list, Cursor items, Drawable marker,
			ItemizedIconOverlay.OnItemGestureListener<OverlayItem> onItemGestureListener,
			ResourceProxy resourceProxy) {
		super(list, marker, onItemGestureListener, resourceProxy);
		mLocatableItems = items;
		populate();
	}

	public static Drawable boundCenterBottom(Drawable drawable){
		// why isn't this visible?
		return drawable;
		//return ItemizedOverlay.boundCenterBottom(drawable);
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
			mItemList.clear();
			mLatCol = mLocatableItems.getColumnIndex(Locatable.Columns._LATITUDE);
			mLonCol = mLocatableItems.getColumnIndex(Locatable.Columns._LONGITUDE);
		}
	}

	protected GeoPoint getItemLocation(Cursor item){
		return MapsUtils.getGeoPoint(item, mLatCol, mLonCol);
	}

	public BoundingBoxE6 getBounds(){
		//return BoundingBoxE6.fromGeoPoints();
		return null;
	}

	/**
	 * this does not work properly when crossing -180/180 boundaries.
	 *
	 * @see com.google.android.maps.ItemizedOverlay#getCenter()
	 */
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
