package edu.mit.mobile.android.locast.casts;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4_map.app.LoaderManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.itineraries.CastsOverlay;
import edu.mit.mobile.android.locast.itineraries.LocatableItemOverlay;

public class CastDetail extends LocatableDetail implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener{
	private LoaderManager mLoaderManager;
	private CastsOverlay mCastsOverlay;
	private MapController mMapController;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_detail);

		mMapController = ((MapView)findViewById(R.id.map)).getController();
		mLoaderManager = getSupportLoaderManager();
		mLoaderManager.initLoader(0, null, this);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub

	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(this, getIntent().getData(), Cast.PROJECTION, null, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mCastsOverlay.swapCursor(c);
		if (c.moveToFirst()){
			((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Cast._TITLE)));
			((TextView)findViewById(R.id.author)).setText(c.getString(c.getColumnIndex(Cast._AUTHOR)));
			((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Cast._DESCRIPTION)));
			final double[] result = new double[2];
			Locatable.toLocationArray(c, c.getColumnIndex(Cast._LATITUDE), c.getColumnIndex(Cast._LONGITUDE), result);
			final GeoPoint gp = new GeoPoint((int)(result[0]  * 1E6), (int)(result[1] * 1E6));
			setPointer(gp);
			mMapController.setCenter(gp);
		}

	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mCastsOverlay.swapCursor(null);

	}

	@Override
	protected LocatableItemOverlay createItemOverlay() {
		mCastsOverlay = new CastsOverlay(this);
		return mCastsOverlay;
	}

}
