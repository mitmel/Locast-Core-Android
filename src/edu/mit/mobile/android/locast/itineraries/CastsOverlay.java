package edu.mit.mobile.android.locast.itineraries;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Locatable;

class CastsOverlay extends ItemizedOverlay<OverlayItem> {
	private Cursor mCasts;
	private int mTitleCol, mDescriptionCol;

	public CastsOverlay(Context context, Cursor casts) {
		super(boundCenterBottom(context.getResources().getDrawable(R.drawable.ic_map_community)));

		mCasts = casts;

	}

	public void swapCursor(Cursor casts){
		mCasts = casts;
		refresh();
	}

	private void refresh(){
		if (mCasts != null){
			mTitleCol = mCasts.getColumnIndex(Cast._TITLE);
			mDescriptionCol = mCasts.getColumnIndex(Cast._DESCRIPTION);

			populate();
		}
	}

	@Override
	protected OverlayItem createItem(int i) {
		mCasts.moveToPosition(i);

		final Location castLoc =  Locatable.toLocation(mCasts);
		final GeoPoint castLocGP = new GeoPoint((int)(castLoc.getLatitude()*1E6), (int)(castLoc.getLongitude()*1E6));

		final OverlayItem item = new OverlayItem(castLocGP, mCasts.getString(mTitleCol), mCasts.getString(mDescriptionCol));

		//TODO set marker here item.setMarker(marker);
		return item;
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

		mCasts.moveToFirst();
		final double[] latLon = new double[2];
		Locatable.toLocationArray(mCasts, latLon);
		maxLat = minLat = (int)(latLon[0] * 1E6);
		maxLon = minLon = (int)(latLon[1] * 1E6);
		mCasts.moveToNext();
		for (; !mCasts.isAfterLast(); mCasts.moveToNext()){
			Locatable.toLocationArray(mCasts, latLon);
			maxLat = Math.max(maxLat, (int)(latLon[0] * 1E6));
			minLat = Math.min(minLat, (int)(latLon[0] * 1E6));

			maxLon = Math.max(maxLon, (int)(latLon[1] * 1E6));
			minLon = Math.min(minLon, (int)(latLon[1] * 1E6));
		}

		return new GeoPoint((maxLat - minLat)/2 + minLat, (maxLon - minLon)/2 + minLon);
	}


	@Override
	public int size() {
		if (mCasts == null){
			return 0;
		}
		return mCasts.getCount();
	}

	private class CastOverlayItem extends OverlayItem {

		public CastOverlayItem(GeoPoint point, String title, String snippet) {
			super(point, title, snippet);
			// TODO Auto-generated constructor stub
		}

	}
}