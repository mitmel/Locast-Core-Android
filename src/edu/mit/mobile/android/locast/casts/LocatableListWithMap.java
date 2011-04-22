package edu.mit.mobile.android.locast.casts;

import java.util.List;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.Loader.OnLoadCompleteListener;
import android.support.v4.widget.CursorAdapter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.itineraries.CastsOverlay;

public class LocatableListWithMap extends MapActivity implements OnItemClickListener, OnClickListener, OnLoadCompleteListener<Cursor> {

	@SuppressWarnings("unused")
	private static final String TAG = LocatableListWithMap.class.getSimpleName();
	private CursorAdapter mAdapter;
	private ListView mListView;
	private Uri mUri;
	private CursorLoader mLoader;

	private CastsOverlay mCastsOverlay;
	private MapView mMapView;
	private MapController mMapController;
	private MyLocationOverlay mMyLocationOverlay;
	private Location mLastLocation;

	public static final String
		ACTION_SEARCH_NEARBY = "edu.mit.mobile.android.locast.ACTION_SEARCH_NEARBY";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map_list_activity);

		findViewById(R.id.refresh).setOnClickListener(this);

		mMapView = (MapView) findViewById(R.id.map);
		mMapController = mMapView.getController();
		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setOnItemClickListener(this);
		mListView.setEmptyView(findViewById(android.R.id.empty));

		final Intent intent = getIntent();
		final String action = intent.getAction();


		if (Intent.ACTION_VIEW.equals(action) || ACTION_SEARCH_NEARBY.equals(action)){
			final Uri data = intent.getData();
			final String type = intent.resolveType(this);

			if (MediaProvider.TYPE_CAST_DIR.equals(type)){
				mAdapter = new CastCursorAdapter(this, null);
//				mAdapter = new SimpleCursorAdapter(this,
//						R.layout.browse_content_item,
//						null,
//				new String[] {Cast._TITLE, Cast._AUTHOR},
//				new int[] {android.R.id.text1, android.R.id.text2}, 0
//				);
				mListView.setAdapter(mAdapter);
				initCastList();

				setTitle("Casts");

				//setDataUri(data);
				updateLocation(data);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mLoader != null){
			mLoader.registerListener(0, this);
			mLoader.startLoading();
		}

		refresh(false);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mLoader != null){
			mLoader.stopLoading();
			mLoader.unregisterListener(this);
		}
	}

	private void updateLocation(Uri data){
		final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		final Location loc = lm.getLastKnownLocation(lm.getBestProvider(new Criteria(), true));
		setDataUri(Locatable.toDistanceSearchUri(data, loc, 500));
		mLastLocation = loc;
	}

	private void setDataUri(Uri data){
		if (mLoader != null){
			mLoader.stopLoading();
			mLoader.setUri(data);
			mLoader.startLoading();
		}else{
			mLoader = new CursorLoader(this, data, Cast.PROJECTION, null, null, Itinerary.SORT_DEFAULT);
		}

		mUri = data;
		startService(new Intent(Intent.ACTION_SYNC, data));
	}

	private void initCastList(){
		mCastsOverlay = new CastsOverlay(this, null);
		final List<Overlay> overlays = mMapView.getOverlays();
		mMyLocationOverlay = new MyLocationOverlay(this, mMapView);
		overlays.add(mMyLocationOverlay);
		overlays.add(mCastsOverlay);
	}

	@Override
	public void setTitle(CharSequence title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	private void refresh(boolean explicitSync){
		startService(new Intent(Intent.ACTION_SYNC, mUri).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mUri, id)));
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.refresh:
			refresh(true);
			break;
		}
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	@Override
	public void onLoadComplete(Loader<Cursor> loader, Cursor c) {
		mAdapter.changeCursor(c);
		mCastsOverlay.changeCursor(c);

		if(mLastLocation != null){
			mMapController.setCenter(new GeoPoint((int)(mLastLocation.getLatitude() * 1E6), (int)(mLastLocation.getLongitude() * 1E6)));
		}
		mMyLocationOverlay.enableMyLocation();

		if (c.moveToFirst()){
			mMapController.zoomToSpan(mCastsOverlay.getLatSpanE6(), mCastsOverlay.getLonSpanE6());
		}else{
			mMapController.setZoom(15);
		}

		mMapView.setVisibility(View.VISIBLE);
	}
}
