package edu.mit.mobile.android.locast.ver2.casts;

import java.util.HashSet;
import java.util.Set;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;
import org.osmdroid.views.overlay.OverlayItem;

import android.content.ContentValues;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.maps.CastLocationOverlay;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.location.IncrementalLocator;
import edu.mit.mobile.android.utils.ResourceUtils;
import edu.mit.mobile.android.widget.CheckableTabWidget;

public class CastEdit extends FragmentActivity implements OnClickListener,
		OnTabChangeListener, LocationListener {

	// stateful
	private final boolean isDraft = true;
	private IGeoPoint mLocation;
	private Set<String> mTags;
	private boolean mRecenterMapOnCurrentLocation = true;

	// stateless
	private Uri mCast;
	private EditText mTitleView;
	private EditText mDescriptionView;

	private TabHost mTabHost;
	private CheckableTabWidget mTabWidget;

	private MapView mMapView;
	private MapController mMapController;

	private IncrementalLocator mIncrementalLocator;

	private CastLocationOverlay mCastLocationOverlay;

	private static final String
		TAB_LOCATION = "location",
		TAB_MEDIA = "media",
		TAB_DETAILS = "details";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_edit);

		// configure tabs
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabWidget = (CheckableTabWidget) findViewById(android.R.id.tabs);
		mTabHost.setup();

		mTabHost.addTab(mTabHost.newTabSpec(TAB_LOCATION).setIndicator(getTabIndicator(R.layout.tab_indicator_left, "Location", R.drawable.ic_tab_location)).setContent(R.id.cast_edit_location));
		mTabHost.addTab(mTabHost.newTabSpec(TAB_MEDIA).setIndicator(getTabIndicator(R.layout.tab_indicator_middle, "Photos", R.drawable.ic_tab_media)).setContent(R.id.cast_edit_media));
		mTabHost.addTab(mTabHost.newTabSpec(TAB_DETAILS).setIndicator(getTabIndicator(R.layout.tab_indicator_right, "Details", R.drawable.ic_tab_details)).setContent(R.id.cast_edit_details));
		mTabHost.setOnTabChangedListener(this);

		// find the other widgets
		mTitleView = (EditText) findViewById(R.id.cast_title);
		mDescriptionView = (EditText) findViewById(R.id.description);

		mMapView = (MapView) findViewById(R.id.map);
		mMapController = mMapView.getController();
		mMapController.setZoom(7);
		mMapView.setBuiltInZoomControls(true);
		mMapView.setMultiTouchControls(true);

		mCastLocationOverlay = new CastLocationOverlay(this, new OnItemGestureListener<OverlayItem>() {

			@Override
			public boolean onItemLongPress(int index, OverlayItem item) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem item) {
				// TODO Auto-generated method stub
				return false;
			}

		}, new DefaultResourceProxyImpl(this));

		mMapView.getOverlayManager().add(mCastLocationOverlay);

		// hook in buttons
		findViewById(R.id.save).setOnClickListener(this);
		findViewById(R.id.center_on_current_location).setOnClickListener(this);
		findViewById(R.id.set_current_location).setOnClickListener(this);

		mIncrementalLocator = new IncrementalLocator(this);

		// process intents

		final Intent intent = getIntent();
		final String action = intent.getAction();

		if (Intent.ACTION_EDIT.equals(action)){

		}else if (Intent.ACTION_INSERT.equals(action)){
			setTitleFromIntent(intent);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopUpdatingLocation();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (TAB_LOCATION.equals(mTabHost.getCurrentTabTag())) {
			startUpdatingLocation();
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.save:
			save();
			break;

		case R.id.center_on_current_location:
			centerOnCurrentLocation();
			break;

		case R.id.set_current_location:
			setCurrentLocation();
			break;
		}

	}

	@Override
	public void onTabChanged(String tabId) {
		if (TAB_LOCATION.equals(tabId)) {
			startUpdatingLocation();
		} else {
			stopUpdatingLocation();
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		if (mRecenterMapOnCurrentLocation) {
			mMapController.animateTo(new GeoPoint(location));
		}
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}

	// //////////////////////////////////////////////////////////////////////////
	// non-handlers

	private void startUpdatingLocation() {
		mIncrementalLocator.requestLocationUpdates(this);
	}

	private void stopUpdatingLocation() {
		mIncrementalLocator.removeLocationUpdates(this);
	}

	private View getTabIndicator(int layout, CharSequence title, int drawable) {
		final LayoutInflater inflater = getLayoutInflater();

		final TextView ind = (TextView) inflater.inflate(layout, mTabHost,
				false);
		ind.setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0);
		ind.setText(title);
		return ind;
	}

	private void setTitleFromIntent(Intent intent) {
		final Uri data = intent.getData();
		final String parentType = getContentResolver().getType(
				ProviderUtils.removeLastPathSegment(data));

		if (MediaProvider.TYPE_ITINERARY_ITEM.equals(parentType)) {
			setTitle(ResourceUtils.getText(this, R.string.add_cast_to_x,
					parentType));

		} else if (MediaProvider.TYPE_CAST_DIR.equals(parentType)) {
			setTitle(getString(R.string.edit_cast));
		}
	}

	@Override
	public void setTitle(int titleId) {
		((TextView) findViewById(android.R.id.title)).setText(titleId);
		super.setTitle(titleId);
	}

	@Override
	public void setTitle(CharSequence title) {
		((TextView) findViewById(android.R.id.title)).setText(title);
		super.setTitle(title);
	}

	private void initNewCast() {
		mTags = new HashSet<String>();
	}

	/**
	 * Reads from the UI and stateful variables, saving to a ContentValues.
	 *
	 * @return
	 */
	public ContentValues toContentValues() {
		final ContentValues cv = new ContentValues();

		cv.put(Cast._TITLE, mTitleView.getText().toString());
		cv.put(Cast._DRAFT, isDraft);
		cv.put(Cast._DESCRIPTION, mDescriptionView.getText().toString());

		return cv;
	}

	private boolean validateEntries() {
		if (mTitleView.getText().toString().trim().length() == 0) {
			mTitleView.setError(getText(R.string.error_please_enter_a_title));
			return false;
		}

		if (mLocation == null) {
			// focus tab on location
			return false;
		}

		return true;
	}

	/**
	 * request that the map be centered on the user's current location
	 */
	private void centerOnCurrentLocation() {
		final Location curLoc = mIncrementalLocator.getLastKnownLocation();
		if (curLoc != null) {
			if (mMapView.getZoomLevel() < 10) {
				mMapController.setZoom(15);
			}
			mMapController.animateTo(new GeoPoint(curLoc));
		}

		mRecenterMapOnCurrentLocation = true;
	}

	private void setLocation(IGeoPoint location) {
		mLocation = location;
		mTabWidget.setTabChecked(0, true);
		mCastLocationOverlay.setLocation(new GeoPoint(location.getLatitudeE6(), location.getLongitudeE6()));
		mMapView.postInvalidate();
	}

	private void setCurrentLocation() {
		setLocation(mMapView.getMapCenter());
	}

	public boolean save() {
		if (validateEntries()) {

		}
		return true;
	}


}
