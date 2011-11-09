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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

import com.stackoverflow.MediaUtils;

import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.TaggableItem;
import edu.mit.mobile.android.locast.maps.CastLocationOverlay;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.location.IncrementalLocator;
import edu.mit.mobile.android.utils.ResourceUtils;
import edu.mit.mobile.android.widget.CheckableTabWidget;

public class CastEdit extends FragmentActivity implements OnClickListener,
		OnTabChangeListener, LocationListener {

	// stateful
	private boolean mIsDraft = true;
	private IGeoPoint mLocation;
	private Set<String> mTags;
	private boolean mRecenterMapOnCurrentLocation = true;
	private Uri mCast;

	// stateless

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

	private static final int
		REQUEST_NEW_PHOTO = 0,
		REQUEST_NEW_VIDEO = 1,
		REQUEST_PICK_MEDIA = 2;

	private static final int
		DIALOG_PICK_MEDIA = 0;


	private static final String
		RS_NS = "edu.mit.mobile.android.locast.",
		RUNTIME_STATE_CAST_URI = RS_NS + "CAST_URI";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_edit);

		// configure tabs
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabWidget = (CheckableTabWidget) findViewById(android.R.id.tabs);
		mTabHost.setup();

		mTabHost.addTab(mTabHost.newTabSpec(TAB_LOCATION).setIndicator(getTabIndicator(R.layout.tab_indicator_left, getString(R.string.tab_location), R.drawable.ic_tab_location)).setContent(R.id.cast_edit_location));
		mTabHost.addTab(mTabHost.newTabSpec(TAB_MEDIA).setIndicator(getTabIndicator(R.layout.tab_indicator_middle, getString(R.string.tab_media), R.drawable.ic_tab_media)).setContent(R.id.cast_edit_media));
		mTabHost.addTab(mTabHost.newTabSpec(TAB_DETAILS).setIndicator(getTabIndicator(R.layout.tab_indicator_right, getString(R.string.tab_details), R.drawable.ic_tab_details)).setContent(R.id.cast_edit_details));
		mTabHost.setOnTabChangedListener(this);

		// find the other widgets
		mTitleView = (EditText) findViewById(R.id.cast_title);
		mDescriptionView = (EditText) findViewById(R.id.description);

		mMapView = (MapView) findViewById(R.id.map);
		mMapController = mMapView.getController();
		mMapController.setZoom(7);
		mMapView.setBuiltInZoomControls(true);
		mMapView.setMultiTouchControls(true);

		if (savedInstanceState != null){
			mCast = savedInstanceState.getParcelable(RUNTIME_STATE_CAST_URI);
		}

		mCastLocationOverlay = new CastLocationOverlay(this, new OnItemGestureListener<OverlayItem>() {

			@Override
			public boolean onItemLongPress(int index, OverlayItem item) {
				return false;
			}

			@Override
			public boolean onItemSingleTapUp(int index, OverlayItem item) {
				return false;
			}

		}, new DefaultResourceProxyImpl(this));

		mMapView.getOverlayManager().add(mCastLocationOverlay);

		// hook in buttons
		findViewById(R.id.save).setOnClickListener(this);
		findViewById(R.id.center_on_current_location).setOnClickListener(this);
		findViewById(R.id.set_current_location).setOnClickListener(this);
		findViewById(R.id.new_photo).setOnClickListener(this);
		findViewById(R.id.new_video).setOnClickListener(this);
		findViewById(R.id.pick_media).setOnClickListener(this);

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
			saveButton();
			break;

		case R.id.center_on_current_location:
			centerOnCurrentLocation();
			break;

		case R.id.set_current_location:
			setCurrentLocation();
			break;

		case R.id.new_photo:
			startActivityForResult(MediaUtils.getImageCaptureIntent(), REQUEST_NEW_PHOTO);
			break;

		case R.id.new_video:
			startActivityForResult(new Intent(MediaStore.ACTION_VIDEO_CAPTURE), REQUEST_NEW_VIDEO);
			break;

		case R.id.pick_media:
			showDialog(DIALOG_PICK_MEDIA);
			break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_CANCELED){
			return;
		}

		switch(requestCode){
			case REQUEST_NEW_PHOTO:{
				final Uri photo = MediaUtils.handleImageCaptureResult(this, intent);
				if (photo != null){
					addMedia(photo);
				}

				}break;
			case REQUEST_PICK_MEDIA:
			case REQUEST_NEW_VIDEO:{
				final Uri media = intent.getData();
				if (media != null){
					addMedia(media);
				}
			}break;

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
	public void onProviderDisabled(String provider) {}

	@Override
	public void onProviderEnabled(String provider) {}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id){
		case DIALOG_PICK_MEDIA:{
			return new AlertDialog.Builder(this)
				.setTitle(R.string.cast_edit_select_media_type)
				.setItems(R.array.cast_edit_pick, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						final Intent i = new Intent();
						i.setAction(Intent.ACTION_GET_CONTENT);
						switch (which){
						case 0:
							i.setType("video/*");
							break;
						case 1:
							i.setType("image/*");
							break;
						}
						startActivityForResult(i, REQUEST_PICK_MEDIA);

					}
				}).create();

		}

		default:
			return super.onCreateDialog(id);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putParcelable(RUNTIME_STATE_CAST_URI, mCast);
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
		cv.put(Cast._DRAFT, mIsDraft);
		cv.put(Cast._DESCRIPTION, mDescriptionView.getText().toString());

		return cv;
	}

	public void addMedia(Uri content){
		final Uri castMedia = Cast.getCastMediaUri(mCast);
		final ContentValues cv = new ContentValues();
		cv.put(CastMedia._LOCAL_URI, content.toString());
		cv.put(CastMedia._MIME_TYPE, getContentResolver().getType(content));

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

	public boolean saveButton() {
		if (validateEntries()) {
			mIsDraft = false;
			mCast = save();
		}
		return true;
	}

	/**
	 * Saves the current state of the cast entry (ignoring validation)
	 *
	 * @return the new URI of the cast
	 */
	private Uri save(){
		Uri newCast;
		final ContentResolver cr = getContentResolver();
		final ContentValues cv = toContentValues();

		if (mCast == null){
			newCast = cr.insert(Cast.CONTENT_URI, cv);
		}else{
			if (cr.update(mCast, cv, null, null) != 1){
				throw new RuntimeException("error updating cast " + mCast);
			}
			newCast = mCast;
		}

		TaggableItem.putTags(cr, newCast, mTags);

		return newCast;
	}
}