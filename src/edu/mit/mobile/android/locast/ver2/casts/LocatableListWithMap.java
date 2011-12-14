package edu.mit.mobile.android.locast.ver2.casts;
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
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4_map.app.LoaderManager;
import android.support.v4_map.app.MapFragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Event;
import edu.mit.mobile.android.locast.data.Favoritable;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.maps.CastsOverlay;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.locast.sync.LocastSyncStatusObserver;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.events.EventCursorAdapter;
import edu.mit.mobile.android.locast.ver2.itineraries.BasicLocatableOverlay;
import edu.mit.mobile.android.locast.ver2.itineraries.LocatableItemOverlay;
import edu.mit.mobile.android.widget.RefreshButton;

public class LocatableListWithMap extends MapFragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnClickListener, OnItemClickListener {

	@SuppressWarnings("unused")
	private static final String TAG = LocatableListWithMap.class.getSimpleName();
	private CursorAdapter mAdapter;
	private ListView mListView;
	private Uri mContentNearLocation;
	private Uri mBaseContent;

	private LocatableItemOverlay mLocatableItemsOverlay;
	private MapView mMapView;
	private MapController mMapController;
	private MyMyLocationOverlay mMyLocationOverlay;
	private Location mLastLocation;
	private LoaderManager mLoaderManager;
	private long mLastUpdate;

	private ImageCache mImageCache;

	// constants related to auto-refreshing
	private static long AUTO_UPDATE_FREQUENCY = 15 * 1000 * 1000; // nano-seconds
	private static float MIN_UPDATE_DISTANCE = 50; // meters

	private int searchRadius = 500; // m

	public static final String
		ACTION_SEARCH_NEARBY = "edu.mit.mobile.android.locast.ACTION_SEARCH_NEARBY";

	private boolean actionSearchNearby = false;
	private boolean mExpeditedSync;

	private RefreshButton mRefresh;

	private Object mSyncHandle;

	private TextView mTextViewStatus;

	private final Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
			case LocastSyncStatusObserver.MSG_SET_REFRESHING:
				if (Constants.DEBUG){
					Log.d(TAG, "refreshing...");
				}
				mTextViewStatus.setText(R.string.loading_data);
				mRefresh.setRefreshing(true);
				break;

			case LocastSyncStatusObserver.MSG_SET_NOT_REFRESHING:
				if (Constants.DEBUG){
					Log.d(TAG, "done loading.");
				}
				mTextViewStatus.setText(R.string.list_empty);
				mRefresh.setRefreshing(false);
				break;
			}
		};
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map_list_activity);
		mTextViewStatus= ((TextView)findViewById(android.R.id.empty));
		findViewById(R.id.refresh).setOnClickListener(this);
		findViewById(R.id.home).setOnClickListener(this);

		mMapView = (MapView) findViewById(R.id.map);
		mMapController = mMapView.getController();
		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setOnItemClickListener(this);
		mListView.addFooterView(getLayoutInflater().inflate(R.layout.list_footer, null), null, false);
		mListView.setEmptyView(findViewById(android.R.id.empty));
		mRefresh = (RefreshButton) findViewById(R.id.refresh);
		mRefresh.setOnClickListener(this);
		mLoaderManager = getSupportLoaderManager();

		final Intent intent = getIntent();
		final String action = intent.getAction();

		mImageCache = ImageCache.getInstance(this);

		actionSearchNearby = ACTION_SEARCH_NEARBY.equals(action);
		final boolean actionView = Intent.ACTION_VIEW.equals(action);

		if (!actionView && !actionSearchNearby){
			Log.e(TAG, "unhandled action " + action);
			finish();
			return;
		}

		CharSequence title;

		final Uri data = intent.getData();
		final String type = intent.resolveType(this);

		if (MediaProvider.TYPE_CAST_DIR.equals(type)){
			mAdapter = new CastCursorAdapter(this, null);

			mListView.setAdapter(new ImageLoaderAdapter(this, mAdapter, mImageCache, new int[]{R.id.media_thumbnail}, 48, 48, ImageLoaderAdapter.UNIT_DIP));
			initMapOverlays(new CastsOverlay(this));

			title = getString(R.string.title_casts);

			searchRadius = 1500;

		}else if (MediaProvider.TYPE_EVENT_DIR.equals(type)){

			title = getString(R.string.title_upcoming_events);
			searchRadius = 10000;

			mAdapter = new EventCursorAdapter(this,
					R.layout.browse_content_item,
					null,
					new String[]{Event._TITLE, Event._START_DATE},
					new int[]{android.R.id.text1, android.R.id.text2},
					new int[]{}, 0);

			mListView.setAdapter(mAdapter);
			initMapOverlays(new BasicLocatableOverlay(LocatableItemOverlay.boundCenterBottom(getResources().getDrawable(R.drawable.ic_map_event))));
		}else{
			throw new IllegalArgumentException("Unhandled content type " + type);
		}

		mBaseContent = data;
		setDataUri(data);

		if (actionSearchNearby){
			title = getString(R.string.title_nearby, title);
		}
		// if it's showing only favorited items, adjust the title's language accordingly.
		final Boolean favorited = Favoritable.decodeFavoritedUri(data);
		if (favorited != null){
			title = getString(favorited ? R.string.title_favorited : R.string.title_unfavorited, title);
		}


		setTitle(title);
		if (actionSearchNearby) {
			updateLocation();
		}
		setRefreshing(true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (actionSearchNearby) {
			mMyLocationOverlay.enableMyLocation();
		}
		mExpeditedSync = true;
		mSyncHandle = ContentResolver.addStatusChangeListener(0xff, new LocastSyncStatusObserver(this, mHandler));
		LocastSyncStatusObserver.notifySyncStatusToHandler(this, mHandler);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (actionSearchNearby) {
			mMyLocationOverlay.disableMyLocation();
		}
		if (mSyncHandle != null){
			ContentResolver.removeStatusChangeListener(mSyncHandle);
		}
	}

	/**
	 * Gets the last-known location and updates with that.
	 */
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
			Toast.makeText(this, R.string.notice_finding_your_location, Toast.LENGTH_LONG).show();
			setRefreshing(true);
			mMapView.setVisibility(View.VISIBLE); // show the map, even without location being found
		}
		mLastLocation = loc;
	}

	/**
	 * Called when the location updates.
	 *
	 * @param loc
	 */
	private void updateLocation(Location loc){
		if (loc == null){
			throw new NullPointerException();
		}

		if (actionSearchNearby){
			setDataUri(Locatable.toDistanceSearchUri(mBaseContent, loc, searchRadius));
		}

		mLastLocation = loc;
	}

	private void setDataUri(Uri data){

		final Bundle args = new Bundle();
		args.putParcelable(LOADER_ARG_DATA, data);
		final String type = getContentResolver().getType(data);
		if (MediaProvider.TYPE_EVENT_DIR.equals(type)){
			mLoaderManager.restartLoader(LOADER_ID_EVENT, args, this);

		}else if (MediaProvider.TYPE_CAST_DIR.equals(type)){
			mLoaderManager.restartLoader(LOADER_ID_CAST, args, this);
		}

		setRefreshing(true);

		mContentNearLocation = data;
		if (data != null){
			refresh(false);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus){

		}
	}

	private void setRefreshing(boolean isRefreshing){
		final RefreshButton refresh = (RefreshButton)findViewById(R.id.refresh);
		refresh.setRefreshing(isRefreshing);
	}

	private void initMapOverlays(LocatableItemOverlay overlay){
		mLocatableItemsOverlay = overlay;
		final List<Overlay> overlays = mMapView.getOverlays();
		mMyLocationOverlay = new MyMyLocationOverlay(this, mMapView);

		if (actionSearchNearby) {
			overlays.add(mMyLocationOverlay);
		}
		overlays.add(mLocatableItemsOverlay);
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
		LocastSyncService.startSync(this, mContentNearLocation, explicitSync);
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mBaseContent, id)));
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.refresh:
			refresh(true);
			break;
		case R.id.home:
			startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
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
		LOADER_ID_CAST = 0,
		LOADER_ID_EVENT = 1;

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		switch (id){
		case LOADER_ID_CAST:
			return new CursorLoader(this, (Uri) args.getParcelable(LOADER_ARG_DATA), Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);
		case LOADER_ID_EVENT:
			// only show events that aren't already over
			return new CursorLoader(this, (Uri) args.getParcelable(LOADER_ARG_DATA), Event.PROJECTION,
					Event._END_DATE + " >= ?",
					new String[]{String.valueOf(System.currentTimeMillis())}, Event.SORT_ORDER_DEFAULT);

			default:
				return null;
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mAdapter.swapCursor(c);
		mLocatableItemsOverlay.swapCursor(c);

		if (c.moveToFirst()){
			mMapController.zoomToSpan(mLocatableItemsOverlay.getLatSpanE6(), mLocatableItemsOverlay.getLonSpanE6());
			final GeoPoint center = mLocatableItemsOverlay.getCenter();
			if (mMapView.getVisibility()==View.INVISIBLE){
				mMapController.setCenter(center);
			}else{
				mMapController.animateTo(center);
			}
		}else{
			mMapController.setZoom(15);
			if(mLastLocation != null){
				final GeoPoint myPosition = new GeoPoint((int)(mLastLocation.getLatitude() * 1E6), (int)(mLastLocation.getLongitude() * 1E6));
				if (mMapView.getVisibility()==View.INVISIBLE){
					mMapController.setCenter(myPosition);
				}else{
					mMapController.animateTo(myPosition);
				}
			}
		}

		mMapView.setVisibility(View.VISIBLE);

		setRefreshing(false);

		if (mExpeditedSync){
			mExpeditedSync = false;
			if (mListView.getAdapter().isEmpty()){
				LocastSyncService.startExpeditedAutomaticSync(this, mContentNearLocation);
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
		mLocatableItemsOverlay.swapCursor(null);

	}
}
