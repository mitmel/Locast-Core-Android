package edu.mit.mobile.android.locast.ver2.casts;

import java.io.File;
import java.util.HashMap;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;
import org.osmdroid.views.overlay.MyLocationOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;

import android.R.attr;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.MediaColumns;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.accounts.AuthenticationService;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.TaggableItem;
import edu.mit.mobile.android.locast.maps.CastLocationOverlay;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.widget.TagList;
import edu.mit.mobile.android.utils.ResourceUtils;
import edu.mit.mobile.android.widget.CheckableTabWidget;

public class CastEdit extends FragmentActivity implements OnClickListener,
		OnTabChangeListener, LocationListener, LoaderCallbacks<Cursor> {

	private static final String TAG = CastEdit.class.getSimpleName();

	/////////////////////
	// stateful
	private Uri mCast;

	private boolean mIsDraft = true;
	private IGeoPoint mLocation;
	private boolean mRecenterMapOnCurrentLocation = true;
	private boolean mFirstLoad = true;

	// when creating media (on some devices), you need to first create the filename, save it, and then use it when the camera returns.
	private Uri mCreateMediaUri;

	/////////////////////
	// stateless

	// general
	private Uri mCastBase; // the base URI that the cast will be inserted into
	private TabHost mTabHost;
	private EditText mTitleView;
	private CheckableTabWidget mTabWidget;


	// media
	private ListView mCastMediaView;
	private EditableCastMediaAdapter mCastMediaAdapter;

	// location
	private ImageButton mSetLocation;
	private MapView mMapView;
	private MapController mMapController;
	private MyLocationOverlay mMyLocationOverlay;

	private CastLocationOverlay mCastLocationOverlay;

	// details
	private EditText mDescriptionView;
	private TagList mTags;

	//////////////////////
	// constants
	private static final String
		TAB_LOCATION = "location",
		TAB_MEDIA = "media",
		TAB_DETAILS = "details";

	private static final int
		REQUEST_NEW_PHOTO = 100,
		REQUEST_NEW_VIDEO = 101,
		REQUEST_PICK_MEDIA = 102;

	private static final int
		DIALOG_PICK_MEDIA = 200;

	private static final int
		LOADER_CAST = 300,
		LOADER_CASTMEDIA = 301;

	private static final String
		LOADER_ARGS_URI = "uri";

	private static final String
		RS_NS = "edu.mit.mobile.android.locast.",
		RUNTIME_STATE_CAST_URI = RS_NS + "CAST_URI",
		RUNTIME_STATE_FIRST_LOAD = RS_NS + "FIRST_LOAD",
		RUNTIME_STATE_CURRENT_TAB = RS_NS + "CURRENT_TAB",
		RUNTIME_STATE_IS_DRAFT = RS_NS + "IS_DRAFT",
		RUNTIME_STATE_LOCATION = RS_NS + "LOCATION",
		RUNTIME_STATE_CREATE_MEDIA_URI = RS_NS + "CREATE_MEDIA_URI";

	private static final String[]
	           CAST_MEDIA_FROM = new String[]{CastMedia._TITLE, CastMedia._THUMB_LOCAL, CastMedia._THUMBNAIL},
	           CAST_MEDIA_PROJECTION = ArrayUtils.concat(new String[]{CastMedia._ID}, CAST_MEDIA_FROM);

	private static final int[]
	           CAST_MEDIA_TO = new int[]{R.id.cast_media_title, R.id.media_thumbnail, R.id.media_thumbnail};

	private static final int[]
	           STATE_ACTIVE_LOCATION_SET = new int[]{attr.state_active, attr.state_checked},
	           STATE_INACTIVE_LOCATION_SET = new int[]{attr.state_checked},
	           STATE_LOCATION_NOT_SET = new int[]{};

	///////////////////////////////////////////////////////////

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.cast_edit);

		// configure tabs
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabWidget = (CheckableTabWidget) findViewById(android.R.id.tabs);
		mTabHost.setup();

		mTabHost.addTab(mTabHost
				.newTabSpec(TAB_LOCATION)
				.setIndicator(
						getTabIndicator(R.layout.tab_indicator_left,
								getString(R.string.tab_location),
								R.drawable.ic_tab_location))
				.setContent(R.id.cast_edit_location));

		mTabHost.addTab(mTabHost
				.newTabSpec(TAB_MEDIA)
				.setIndicator(
						getTabIndicator(R.layout.tab_indicator_middle,
								getString(R.string.tab_media),
								R.drawable.ic_tab_media))
				.setContent(R.id.cast_edit_media));

		mTabHost.addTab(mTabHost
				.newTabSpec(TAB_DETAILS)
				.setIndicator(
						getTabIndicator(R.layout.tab_indicator_right,
								getString(R.string.tab_details),
								R.drawable.ic_tab_details))
				.setContent(R.id.cast_edit_details));
		mTabHost.setOnTabChangedListener(this);

		// find the other widgets
		mTitleView = (EditText) findViewById(R.id.cast_title);

		// cast media
		mCastMediaView = (ListView) findViewById(android.R.id.list);
		mCastMediaView.setEmptyView(findViewById(android.R.id.empty));
		mCastMediaView.setItemsCanFocus(true);

		mCastMediaAdapter = new EditableCastMediaAdapter(this, R.layout.cast_media_editable, null, CAST_MEDIA_FROM, CAST_MEDIA_TO, new int[]{R.id.media_thumbnail}, SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);

		mCastMediaAdapter.addOnClickListener(R.id.remove, new OnClickListener() {

			@Override
			public void onClick(View v) {
				final CastMediaAdapter adapter = (CastMediaAdapter) v.getTag(R.id.viewtag_item_adapter);
				final int position = (Integer) v.getTag(R.id.viewtag_item_position);
				final Uri castMediaUri = ContentUris.withAppendedId(Cast.getCastMediaUri(mCast), adapter.getItemId(position));

				getContentResolver().delete(castMediaUri, null, null);
			}
		});

		/*
		 * XXX doesn't work.
		 *
		 * mCastMediaAdapter.addOnFocusChangeListener(R.id.cast_media_title, new OnFocusChangeListener() {

			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus){
					final CastMediaAdapter adapter = (CastMediaAdapter) v.getTag(R.id.viewtag_item_adapter);
					final int position = (Integer) v.getTag(R.id.viewtag_item_position);
					final Uri castMediaUri = ContentUris.withAppendedId(Cast.getCastMediaUri(mCast), adapter.getItemId(position));

					final ContentValues cv = new ContentValues();
					cv.put(CastMedia._TITLE, ((EditText)v).getText().toString());
					getContentResolver().update(castMediaUri, cv, null, null);
				}
			}
		});*/



		mCastMediaView.setOnItemClickListener(mCastMediaOnItemClickListener);
		mCastMediaView.setAdapter(new ImageLoaderAdapter(this, mCastMediaAdapter,
				ImageCache.getInstance(this),
				new int[] { R.id.media_thumbnail }, 100, 100,
				ImageLoaderAdapter.UNIT_DIP));

		mDescriptionView = (EditText) findViewById(R.id.description);
		mTags = (TagList) findViewById(R.id.tags);

		////////////////
		// initialize map
		mSetLocation = (ImageButton) findViewById(R.id.set_current_location);
		mMapView = (MapView) findViewById(R.id.map);
		mMapView.setMapListener(new MapListener() {

			@Override
			public boolean onZoom(ZoomEvent event) {
				mapHasMoved();
				return false;
			}

			@Override
			public boolean onScroll(ScrollEvent event) {
				mapHasMoved();
				return false;
			}
		});

		mMapController = mMapView.getController();
		mMapController.setZoom(7);
		mMapView.setBuiltInZoomControls(true);
		mMapView.setMultiTouchControls(true);

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

		mMyLocationOverlay = new MyLocationOverlay(this, mMapView);
		mMyLocationOverlay.setDrawAccuracyEnabled(true);

		final OverlayManager overlayManager = mMapView.getOverlayManager();
		overlayManager.add(mMyLocationOverlay);
		overlayManager.add(mCastLocationOverlay);

		// hook in buttons
		findViewById(R.id.save).setOnClickListener(this);
		mSetLocation.setOnClickListener(this);
		findViewById(R.id.center_on_current_location).setOnClickListener(this);
		findViewById(R.id.new_photo).setOnClickListener(this);
		findViewById(R.id.new_video).setOnClickListener(this);
		findViewById(R.id.pick_media).setOnClickListener(this);

		//mIncrementalLocator = new IncrementalLocator(this);


		/////////////
		// process intents

		final Intent intent = getIntent();
		final String action = intent.getAction();

		/////////////
		// restore any existing state

		final LoaderManager lm = getSupportLoaderManager();

		if (savedInstanceState != null){
			mCast = savedInstanceState.getParcelable(RUNTIME_STATE_CAST_URI);
			mFirstLoad = savedInstanceState.getBoolean(RUNTIME_STATE_FIRST_LOAD, true);
			mTabHost.setCurrentTab(savedInstanceState.getInt(RUNTIME_STATE_CURRENT_TAB, 0));
			setLocation((GeoPoint)savedInstanceState.getParcelable(RUNTIME_STATE_LOCATION));
			mIsDraft = savedInstanceState.getBoolean(RUNTIME_STATE_IS_DRAFT, true);
			mCreateMediaUri = savedInstanceState.getParcelable(RUNTIME_STATE_CREATE_MEDIA_URI);
		}

		if (Intent.ACTION_EDIT.equals(action)){
			if (mCast == null){
				mCast = intent.getData();
			}
			mCastBase = ProviderUtils.removeLastPathSegment(mCast);

		}else if (Intent.ACTION_INSERT.equals(action)){
			setTitleFromIntent(intent);

			mCastBase = intent.getData();
		}

		if (mCast != null){
			lm.initLoader(LOADER_CAST, null, this);

		}else{
			// XXX put on thread
			mCast = save(); // create a new, blank cast

		}

		lm.initLoader(LOADER_CASTMEDIA, null, this);

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mCast != null){
			save();
		}

		stopUpdatingLocation();
		mMyLocationOverlay.disableMyLocation();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (TAB_LOCATION.equals(mTabHost.getCurrentTabTag())) {
			startUpdatingLocation();
		}

		mMyLocationOverlay.enableMyLocation();
		//getSupportLoaderManager().restartLoader(arg0, arg1, arg2)
	}

	@Override
	public void onBackPressed() {

		if (mLocation == null && mTitleView.getText().length() == 0 && mCastMediaAdapter.getCount() == 0 && mDescriptionView.getText().length() == 0){
			Log.d(TAG, "cast "+mCast+" seems to be empty, so deleting it");
			final ContentResolver cr = getContentResolver();
			cr.delete(Cast.getCastMediaUri(mCast), null, null);
			cr.delete(mCast, null, null);
			mCast = null;
		}


		super.onBackPressed();

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.save:
			if (saveButton()){
				// this isn't necessary, but could be helpful for integration
				setResult(RESULT_OK, new Intent().setData(mCast));
				finish();
			}
			break;

		case R.id.center_on_current_location:
			centerOnCurrentLocation();
			break;

		case R.id.set_current_location:
			setCurrentLocation();
			break;

		case R.id.new_photo:{
			final Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			mCreateMediaUri = createNewMedia("jpg");
			i.putExtra(MediaStore.EXTRA_OUTPUT, mCreateMediaUri);
			startActivityForResult(i, REQUEST_NEW_PHOTO);
		}break;

		case R.id.new_video:{
			final Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			mCreateMediaUri = createNewMedia("mp4");
			i.putExtra(MediaStore.EXTRA_OUTPUT, mCreateMediaUri);
			startActivityForResult(i, REQUEST_NEW_VIDEO);
		}break;

		case R.id.pick_media:
			showDialog(DIALOG_PICK_MEDIA);
			break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_CANCELED){
			Log.d(TAG, "media adding cancelled");
			mCreateMediaUri = null;
			return;
		}
//		Log.d(TAG, "onActivityResult: " + data);
//		if (data == null || data.getData() == null){
//			Toast.makeText(this, R.string.cast_edit_error_camera_did_not_return_image, Toast.LENGTH_LONG).show();
//			return;
//		}

		switch(requestCode){
			case REQUEST_NEW_PHOTO:{

				addMedia(mCreateMediaUri);
				mCreateMediaUri = null;

				}break;
			case REQUEST_NEW_VIDEO:{

				addMedia(mCreateMediaUri);
				mCreateMediaUri = null;
			}break;

			case REQUEST_PICK_MEDIA:{
				final Uri media = data.getData();
				if (media != null){
					addMedia(media);
				}
			}

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
			if (mMapView.getZoomLevel() < 10) {
				mMapController.setZoom(15);
			}
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
		case DIALOG_PICK_MEDIA:
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

		default:
			return super.onCreateDialog(id);
		}
	}


	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		switch (id){
		case LOADER_CAST:
			return new CursorLoader(this, mCast, Cast.PROJECTION, null, null, null);

		case LOADER_CASTMEDIA:
			return new CursorLoader(this, Cast.getCastMediaUri(mCast), CAST_MEDIA_PROJECTION, null, null, CastMedia._CREATED_DATE + " DESC");
		}

		return null;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		switch (loader.getId()){
		case LOADER_CAST:
			// XXX hack. This is done as the cast is notified that it should reload when cast media is added. Obviously, it shouldn't.
			if (mFirstLoad && c.moveToFirst()){
				loadFromCursor(c);
			}
			break;

		case LOADER_CASTMEDIA:
			mCastMediaAdapter.swapCursor(c);
			mTabWidget.setTabChecked(1, c.getCount() != 0);
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
				ProviderUtils.dumpCursorToLog(c, CAST_MEDIA_PROJECTION);
			}
			break;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		switch (loader.getId()){
		case LOADER_CAST:

			break;

		case LOADER_CASTMEDIA:
			mCastMediaAdapter.swapCursor(null);
			break;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putParcelable(RUNTIME_STATE_CAST_URI, mCast);
		outState.putBoolean(RUNTIME_STATE_FIRST_LOAD, mFirstLoad);
		outState.putInt(RUNTIME_STATE_CURRENT_TAB, mTabHost.getCurrentTab());
		if (mLocation instanceof GeoPoint){
			outState.putParcelable(RUNTIME_STATE_LOCATION, (GeoPoint)mLocation);
		}
		outState.putBoolean(RUNTIME_STATE_IS_DRAFT, mIsDraft);
		outState.putParcelable(RUNTIME_STATE_CREATE_MEDIA_URI, mCreateMediaUri);
	}

	// //////////////////////////////////////////////////////////////////////////
	// non-handlers

	private Uri createNewMedia(String extension){
		final File outfile = new File("/sdcard/locast/", System.currentTimeMillis() + "." + extension);
		outfile.getParentFile().mkdirs();

		return Uri.fromFile(outfile);
	}

	private void loadFromCursor(Cursor c){
		mTitleView.setText(c.getString(c.getColumnIndexOrThrow(Cast._TITLE)));
		mDescriptionView.setText(c.getString(c.getColumnIndexOrThrow(Cast._DESCRIPTION)));

		final Location l = Locatable.toLocation(c);
		if (l != null){
			mRecenterMapOnCurrentLocation = false;
			setLocation(new GeoPoint(l)); // XXX optimize
		}
		mTags.clearAllTags();
		mTags.addTags(TaggableItem.getTags(getContentResolver(), mCast));

		if (mFirstLoad){
			mTabHost.setCurrentTab(mTabWidget.getNextUncheckedTab());
			mFirstLoad = false;
		}

		final int draftCol = c.getColumnIndexOrThrow(Cast._DRAFT);

		mIsDraft = !c.isNull(draftCol) && c.getInt(draftCol) != 0;
	}

	private void startUpdatingLocation() {
		mMyLocationOverlay.enableMyLocation();
	}

	private void stopUpdatingLocation() {
		mMyLocationOverlay.disableMyLocation();
	}

	private boolean mMapCenterIsLocation = false;
	private void mapHasMoved(){
		if (mMapCenterIsLocation){
			if (mLocation != null){
				if (mLocation instanceof GeoPoint){
					mMapCenterIsLocation = ((GeoPoint) mLocation).distanceTo(mMapView.getMapCenter()) <= 0.1;
					if (!mMapCenterIsLocation){
						mSetLocation.setImageState(STATE_INACTIVE_LOCATION_SET, false);
					}else{
						mSetLocation.setImageState(STATE_ACTIVE_LOCATION_SET, false);
					}
				}
			}else{
				mSetLocation.setImageState(STATE_LOCATION_NOT_SET, false);
				mMapCenterIsLocation = false;
			}

			mSetLocation.postInvalidate();
		}

		// XXX this doesn't work as this function gets called even if the map is moved programatically
		mRecenterMapOnCurrentLocation = false;
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

	private void setDescription(CharSequence description){
		mDescriptionView.setText(description);
		mTabWidget.setTabChecked(2, true);
	}

	/**
	 * Reads from the UI and stateful variables, saving to a ContentValues. CastMedia and tags need to be saved separately.
	 *
	 * @return
	 */
	public ContentValues toContentValues() {
		final ContentValues cv = new ContentValues();

		final Account me = Authenticator.getFirstAccount(this);
		final AccountManager am = AccountManager.get(this);

		cv.put(Cast._AUTHOR, am.getUserData(me, AuthenticationService.USERDATA_DISPLAY_NAME));
		cv.put(Cast._AUTHOR_URI, am.getUserData(me, AuthenticationService.USERDATA_USER_URI));

		cv.put(Cast._TITLE, mTitleView.getText().toString());
		cv.put(Cast._DRAFT, mIsDraft);
		cv.put(Cast._DESCRIPTION, mDescriptionView.getText().toString());
		cv.put(Cast._MODIFIED_DATE, System.currentTimeMillis());

		if(mLocation != null){
			Locatable.toContentValues(cv, mLocation);
		}

		return cv;
	}

	public void addMedia(Uri content){
		final Uri castMedia = Cast.getCastMediaUri(mCast);
		final ContentValues cv = new ContentValues();

		final long now = System.currentTimeMillis();
		cv.put(CastMedia._MODIFIED_DATE, now);
		cv.put(CastMedia._CREATED_DATE, now);
		cv.put(CastMedia._TITLE, content.getLastPathSegment());

		String mimeType = getContentResolver().getType(content);
		if (mimeType == null){
			mimeType = CastMedia.guessMimeTypeFromUrl(content.toString());
		}
		cv.put(CastMedia._MIME_TYPE, mimeType);

		String mediaPath = null;

		if ("content".equals(content.getScheme())){
			final Cursor c = getContentResolver().query(content, new String[]{MediaColumns._ID, MediaColumns.DATA, MediaColumns.TITLE, Media.LATITUDE, Media.LONGITUDE}, null, null, null);
			try {
				if (c.moveToFirst()){
					cv.put(CastMedia._TITLE, c.getString(c.getColumnIndexOrThrow(MediaColumns.TITLE)));
					mediaPath = "file://" + c.getString(c.getColumnIndexOrThrow(MediaColumns.DATA));

					// if the current location is null, infer it from the first media that's added.
					if (mLocation == null){
						final int latCol = c.getColumnIndex(Media.LATITUDE);
						final int lonCol = c.getColumnIndex(Media.LONGITUDE);
						final double lat = c.getDouble(latCol);
						final double lon = c.getDouble(lonCol);

						final boolean isInArmpit = lat == 0 && lon == 0; // Sorry, people in boats off the coast of Ghana, but you're an unfortunate edge case...
						if (!c.isNull(latCol) && !c.isNull(lonCol) && ! isInArmpit){
							setLocation(new GeoPoint(c.getDouble(latCol), c.getDouble(lonCol)));
						}
					}

				}
			}finally{
				c.close();
			}
		}else{
			mediaPath = content.toString();
		}

		if (mediaPath == null){
			Log.e(TAG, "couldn't add media from uri "+content);
			return;
		}

		cv.put(CastMedia._THUMB_LOCAL, mediaPath);
		cv.put(CastMedia._LOCAL_URI, mediaPath);

		Log.d(TAG, "addMedia("+castMedia+", "+cv+")");
		getContentResolver().insert(castMedia, cv);

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
		final Location curLoc = mMyLocationOverlay.getLastFix();
		//final Location curLoc = mIncrementalLocator.getLastKnownLocation();
		if (curLoc != null) {
			if (mMapView.getZoomLevel() < 10) {
				mMapController.setZoom(15);
			}
			mMapController.animateTo(new GeoPoint(curLoc));
		}else{
			Toast.makeText(this, R.string.notice_finding_your_location, Toast.LENGTH_SHORT).show();
		}

		mRecenterMapOnCurrentLocation = true;
	}

	private void setLocation(IGeoPoint location) {
		mLocation = location;

		final boolean hasLocation = mLocation != null;

		mRecenterMapOnCurrentLocation = false;

		mTabWidget.setTabChecked(0, hasLocation);
		//mSetLocation.setImageState(STATE_ACTIVE_LOCATION_SET, false);
		//mSetLocation.postInvalidate();

		mMapCenterIsLocation = hasLocation;

		if (hasLocation){
			mCastLocationOverlay.setLocation(new GeoPoint(location.getLatitudeE6(), location.getLongitudeE6()));
			mMapController.setCenter(location);
		}

		mMapView.postInvalidate();

	}

	private void setCurrentLocation() {
		setLocation(mMapView.getMapCenter());
	}

	/**
	 * Validates the entries and marks the cast as non-draft if it passes validation.
	 *
	 * @return
	 */
	public boolean saveButton() {
		boolean savedSuccessfully = false;
		if (validateEntries()) {
			mIsDraft = false;
			mCast = save();
			savedSuccessfully = mCast != null;
		}
		return savedSuccessfully;
	}

	/**
	 * Saves the current state of the cast entry (ignoring validation).
	 *
	 * @return the new URI of the cast
	 */
	private Uri save(){
		Uri newCast;
		final ContentResolver cr = getContentResolver();
		final ContentValues cv = toContentValues();

		if (mCast == null){
			Log.d(TAG, "inserting "+cv+" into "+ mCastBase);
			newCast = cr.insert(mCastBase, cv);
		}else{
			Log.d(TAG, "updating "+mCast+" with "+ cv);
			if (cr.update(mCast, cv, null, null) != 1){
				throw new RuntimeException("error updating cast " + mCast);
			}
			newCast = mCast;

		}
		Log.d(TAG, "cast URI is" + newCast);
		if (newCast != null){
			TaggableItem.putTags(cr, newCast, mTags.getTags());
		}
		if (mCast != null){
			// XXX hack to fix possibly empty cast media. This is only called when there's existing data.
			final ContentValues cvCastMediaDefaultTitle = new ContentValues();
			cvCastMediaDefaultTitle.put(CastMedia._TITLE, "untitled");
			cr.update(Cast.getCastMediaUri(mCast), cvCastMediaDefaultTitle, CastMedia._TITLE + " IS NULL", null);
		}

		return newCast;
	}

	private final OnItemClickListener mCastMediaOnItemClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> adapter, View v, int position,
				long id) {
			final Uri castMedia = ContentUris.withAppendedId(Cast.getCastMediaUri(mCast), id);
			final Cursor c = (Cursor) adapter.getItemAtPosition(position);
			CastMedia.showMedia(CastEdit.this, c, castMedia);

		}
	};

	private class EditableCastMediaAdapter extends CastMediaAdapter{
		private final HashMap<Integer, OnClickListener> mClickListeners = new HashMap<Integer, AdapterView.OnClickListener>();
		private final HashMap<Integer, OnFocusChangeListener> mFocusChangeListeners = new HashMap<Integer, OnFocusChangeListener>();

		public EditableCastMediaAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int[] imageIDs, int flags) {
			super(context, layout, c, from, to, imageIDs, flags);

		}

		public void addOnClickListener(int viewID, OnClickListener onClickListener){
			mClickListeners.put(viewID, onClickListener);
		}

		public void addOnFocusChangeListener(int viewID, OnFocusChangeListener onFocusChangeListener){
			mFocusChangeListeners.put(viewID, onFocusChangeListener);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final View v = super.getView(position, convertView, parent);

			for (final int viewID : mClickListeners.keySet()){
				final View targetView = v.findViewById(viewID);
				if (targetView == null){
					continue;
				}
				targetView.setOnClickListener(mClickListeners.get(viewID));

				targetView.setTag(R.id.viewtag_item_position, position);
				targetView.setTag(R.id.viewtag_item_adapter, this);
			}
			// TODO inefficient, but works
			for (final int viewID : mFocusChangeListeners.keySet()){
				final View targetView = v.findViewById(viewID);
				if (targetView == null){
					continue;
				}
				targetView.setOnFocusChangeListener(mFocusChangeListeners.get(viewID));

				targetView.setTag(R.id.viewtag_item_position, position);
				targetView.setTag(R.id.viewtag_item_adapter, this);
			}

			return v;
		}
	}

}