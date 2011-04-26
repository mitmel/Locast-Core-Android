package edu.mit.mobile.android.locast.casts;

import java.util.List;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4_map.app.LoaderManager;
import android.support.v4_map.app.MapFragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.itineraries.CastsOverlay;

public class LocatableListWithMap extends MapFragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnClickListener, OnItemClickListener {

	@SuppressWarnings("unused")
	private static final String TAG = LocatableListWithMap.class.getSimpleName();
	private CursorAdapter mAdapter;
	private ListView mListView;
	private Uri mContentNearLocation;
	private Uri mContent;

	private CastsOverlay mCastsOverlay;
	private MapView mMapView;
	private MapController mMapController;
	private MyMyLocationOverlay mMyLocationOverlay;
	private Location mLastLocation;
	private LoaderManager mLoaderManager;
	private long mLastUpdate;

	// constants related to auto-refreshing
	private static long AUTO_UPDATE_FREQUENCY = 15 * 1000 * 1000; // nano-seconds
	private static float MIN_UPDATE_DISTANCE = 50; // meters

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

		mLoaderManager = getSupportLoaderManager();

		final Intent intent = getIntent();
		final String action = intent.getAction();


		if (Intent.ACTION_VIEW.equals(action) || ACTION_SEARCH_NEARBY.equals(action)){
			final Uri data = intent.getData();
			final String type = intent.resolveType(this);

			if (MediaProvider.TYPE_CAST_DIR.equals(type)){
				mAdapter = new CastCursorAdapter(this, null);

				mListView.setAdapter(mAdapter);
				initMapOverlays();

				setTitle("Nearby Casts");
				mContent = data;

				updateLocation();
				setRefreshing(true);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mMyLocationOverlay.enableMyLocation();
		refresh(false);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mMyLocationOverlay.disableMyLocation();
	}

	private void updateLocation(){
		final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		final String provider = lm.getBestProvider(new Criteria(), true);
		if (provider == null){
			Toast.makeText(this, getString(R.string.error_no_providers), Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		final Location loc = lm.getLastKnownLocation(provider);
		if (loc != null){
			updateLocation(loc);
		}else{
			Toast.makeText(this, "Finding your location...", Toast.LENGTH_LONG).show();
			setRefreshing(true);
			mMapView.setVisibility(View.VISIBLE); // show the map, even without location being found
		}
		mLastLocation = loc;
	}

	private void updateLocation(Location loc){
		if (loc == null){
			throw new NullPointerException();
		}
		setDataUri(Locatable.toDistanceSearchUri(mContent, loc, 500));

		mLastLocation = loc;
	}

	private void setDataUri(Uri data){

		final Bundle args = new Bundle();
		args.putParcelable(LOADER_ARG_DATA, data);
		mLoaderManager.restartLoader(LOADER_ID_CAST, args, this);
		setRefreshing(true);

		mContentNearLocation = data;
		if (data != null){
			refresh(false);
		}
	}

	private void setRefreshing(boolean isRefreshing){
		if(isRefreshing){
			((ImageButton)findViewById(R.id.refresh)).setImageResource(android.R.drawable.ic_dialog_alert);
		}else{
			((ImageButton)findViewById(R.id.refresh)).setImageResource(R.drawable.ic_refresh);
		}
	}

	private void initMapOverlays(){
		mCastsOverlay = new CastsOverlay(this);
		final List<Overlay> overlays = mMapView.getOverlays();
		mMyLocationOverlay = new MyMyLocationOverlay(this, mMapView);

		overlays.add(mMyLocationOverlay);
		overlays.add(mCastsOverlay);
	}

	@Override
	public void setTitle(CharSequence title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	private void refresh(boolean explicitSync){
		if ((System.nanoTime() - mLastUpdate) < AUTO_UPDATE_FREQUENCY && !explicitSync){
			// not enough time has elapsed for a non-explicit sync to be allowed
			return;
		}
		mLastUpdate = System.nanoTime();
		startService(new Intent(Intent.ACTION_SYNC, mContentNearLocation).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mContent, id)));
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

	private class MyMyLocationOverlay extends MyLocationOverlay {

		private Location mPrevLocation = null;

		public MyMyLocationOverlay(Context context, MapView mapView) {
			super(context, mapView);
		}

		@Override
		public synchronized void onLocationChanged(Location location) {
			super.onLocationChanged(location);
			if (mPrevLocation == null || location.distanceTo(mPrevLocation) > MIN_UPDATE_DISTANCE){
				updateLocation(location);
				mPrevLocation = location;
			}
		}
	}

	private static String LOADER_ARG_DATA = "edu.mit.mobile.android.locast.LOADER_ARG_DATA";
	private final static int
		LOADER_ID_CAST = 0;

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		switch (id){
		case LOADER_ID_CAST:
			return new CursorLoader(this, (Uri) args.getParcelable(LOADER_ARG_DATA), Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);

			default:
				return null;
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mAdapter.swapCursor(c);
		mCastsOverlay.swapCursor(c);

		if(mLastLocation != null){
			final GeoPoint myPosition = new GeoPoint((int)(mLastLocation.getLatitude() * 1E6), (int)(mLastLocation.getLongitude() * 1E6));
			if (mMapView.getVisibility()==View.INVISIBLE){
				mMapController.setCenter(myPosition);
			}else{
				mMapController.animateTo(myPosition);
			}
		}

		if (c.moveToFirst()){
			mMapController.zoomToSpan(mCastsOverlay.getLatSpanE6(), mCastsOverlay.getLonSpanE6());
		}else{
			mMapController.setZoom(15);
		}

		mMapView.setVisibility(View.VISIBLE);

		setRefreshing(false);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
		mCastsOverlay.swapCursor(null);

	}
}
