package edu.mit.mobile.android.locast.ver2.casts;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.MediaColumns;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4_map.app.LoaderManager;
import android.support.v4_map.app.LoaderManager.LoaderCallbacks;
import android.support.v4_map.app.MapFragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.media.ExifInterface;

import com.google.android.maps.GeoPoint;
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
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.widget.TagList;
import edu.mit.mobile.android.locast.widget.TagList.OnTagListChangeListener;
import edu.mit.mobile.android.utils.ResourceUtils;
import edu.mit.mobile.android.widget.CheckableTabWidget;

public class CastEdit extends MapFragmentActivity implements OnClickListener,
		OnTabChangeListener, LocationListener, LoaderCallbacks<Cursor> {

	private static final String TAG = CastEdit.class.getSimpleName();

	/////////////////////
	// stateful
	private Uri mCast;
	private boolean mIsNewCast;

	private boolean mIsDraft = true;
	private boolean mIsEditable = false;

	private AlertDialog alertDialog = null;
	private ProgressDialog waitForLocationDialog = null;
	private GeoPoint mLocation;
	private Location currentLocation = null;

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
	private ImageView mMediaThumbnail;

	private final ImageCache mImageCache = ImageCache.getInstance(this);

	// media
	//private ListView mCastMediaView;
	private ListView mCastMediaView;
	private EditableCastMediaAdapter mCastMediaAdapter;

	// location
	private LocationManager locationManager;

	// details
	private EditText mDescriptionView;
	private TagList mTags;

	//////////////////////
	// constants
	private static final String
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


	private static final int MINIMUM_REQUIRED_ACCURACY = 100;
	private static final int MAXIMUM_WAIT_TIME_IN_SECONDS = 60;

	///////////////////////////////////////////////////////////

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.cast_edit_simple);

		//configureTabs();

		// find the other widgets
		mTitleView = (EditText) findViewById(R.id.cast_title);

		//configureCastMediaControls();

		//configureDescriptionView();

		configureTagsView();


		findViewById(R.id.save).setOnClickListener(this);
		findViewById(R.id.new_photo).setOnClickListener(this);
		findViewById(R.id.media_thumbnail).setOnClickListener(this);
		//findViewById(R.id.new_video).setOnClickListener(this);
		//findViewById(R.id.pick_media).setOnClickListener(this);

		processIntents(savedInstanceState);

		setupLocationRetrieval();
	}

	private void processIntents(Bundle savedInstanceState) {
		final Intent intent = getIntent();
		final String action = intent.getAction();

		/////////////
		// restore any existing state
		final LoaderManager lm = getSupportLoaderManager();

		if (savedInstanceState != null){
			mCast = savedInstanceState.getParcelable(RUNTIME_STATE_CAST_URI);
			mFirstLoad = savedInstanceState.getBoolean(RUNTIME_STATE_FIRST_LOAD, true);
			if (mTabHost != null) {
				mTabHost.setCurrentTab(savedInstanceState.getInt(RUNTIME_STATE_CURRENT_TAB, 0));
			}
			setLocation((GeoPoint)savedInstanceState.getParcelable(RUNTIME_STATE_LOCATION));
			mIsDraft = savedInstanceState.getBoolean(RUNTIME_STATE_IS_DRAFT, true);
			mCreateMediaUri = savedInstanceState.getParcelable(RUNTIME_STATE_CREATE_MEDIA_URI);
		}

		if (Intent.ACTION_EDIT.equals(action)) {
			if (mCast == null){
				mCast = intent.getData();
			}

			mCastBase = ProviderUtils.removeLastPathSegment(mCast);
		} else if (Intent.ACTION_INSERT.equals(action)){
			setTitleFromIntent(intent);

			mCastBase = intent.getData();
		}

		if (mCast != null) {
			lm.initLoader(LOADER_CAST, null, this);
		} else {
			// XXX put on thread
			mIsNewCast = true;
			setEditable(true);
			mCast = save(); // create a new, blank cast
		}

		lm.initLoader(LOADER_CASTMEDIA, null, this);
	}

	private void configureTagsView() {
		mTags = (TagList) findViewById(R.id.tags);
		mTags.setOnTagListChangeListener(new OnTagListChangeListener() {

			@Override
			public void onTagListChange(TagList v) {
				updateDetailsTab();
			}
		});
	}

	private void configureDescriptionView() {
		mDescriptionView = (EditText) findViewById(R.id.description);
		mDescriptionView.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}

			@Override
			public void afterTextChanged(Editable s) {
				//updateDetailsTab();

			}
		});
	}

	private void configureCastMediaControls() {
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

		mCastMediaView.setOnItemClickListener(mCastMediaOnItemClickListener);

		mCastMediaView.setAdapter(new ImageLoaderAdapter(this, mCastMediaAdapter,
				ImageCache.getInstance(this),
				new int[] { R.id.media_thumbnail }, 100, 100,
				ImageLoaderAdapter.UNIT_DIP));
	}

	private void configureTabs() {
		// configure tabs

		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabWidget = (CheckableTabWidget) findViewById(android.R.id.tabs);
		mTabHost.setup();

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
	}

	private void setupLocationRetrieval() {
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	}

	protected void updateDetailsTab() {
		//final boolean descriptionComplete = mDescriptionView.length() > 0;
		final boolean tagsComplete = mTags.getTags().size() > 0;
		//mTabWidget.setTabChecked(1, descriptionComplete || tagsComplete);
	}

	@Override
	protected void onPause() {
		super.onPause();

		mImageCache.unregisterOnImageLoadListener(mImageCacheLoadListener);

		if (mCast != null){
			save();
		}

		stopUpdatingLocation();
	}

	@Override
	protected void onResume() {
		super.onResume();

		mImageCache.registerOnImageLoadListener(mImageCacheLoadListener);
		if (mIsNewCast) {
			startUpdatingLocation();
		}
	}

	@Override
	public void onBackPressed() {
		if (mTitleView.getText().length() == 0) {
			Log.d(TAG, "cast "+ mCast + " seems to be empty, so deleting it");
			final ContentResolver cr = getContentResolver();
			cr.delete(Cast.getCastMediaUri(mCast), null, null);
			cr.delete(mCast, null, null);
			mCast = null;
		}

		super.onBackPressed();
	}

	private void showAlertDialog(){
		alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle(getString(R.string.location_service_not_working));
		alertDialog.setMessage(getString(R.string.need_more_time_to_retrieve_location));

		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.continue_res), new android.content.DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which) {
				alertDialog.dismiss();
				alertDialog = null;

				showLocationDialog();
			}
		});

		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.stop), new android.content.DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				alertDialog.dismiss();
				alertDialog = null;
			}
		});

		alertDialog.show();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.save:
			if (saveButton()){
				//If we still don't have a location, or if the location we have is not accurate enough, we open a modal dialog and ask the user to wait
				if (showLocationDialog()) {
					return;
				}

				finishSave();
			}
			break;

		case R.id.new_photo:
		case R.id.media_thumbnail:
			final Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			mCreateMediaUri = createNewMedia("jpg");
			i.putExtra(MediaStore.EXTRA_OUTPUT, mCreateMediaUri);
			startActivityForResult(i, REQUEST_NEW_PHOTO);
			break;

		case R.id.new_video:
			final Intent i2 = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			mCreateMediaUri = createNewMedia("mp4");
			i2.putExtra(MediaStore.EXTRA_OUTPUT, mCreateMediaUri);
			startActivityForResult(i2, REQUEST_NEW_VIDEO);
			break;

		case R.id.pick_media:
			showDialog(DIALOG_PICK_MEDIA);
			break;
		}
	}

	private boolean showLocationDialog() {
		if (mLocation == null || (currentLocation != null && currentLocation.hasAccuracy() && currentLocation.getAccuracy() > MINIMUM_REQUIRED_ACCURACY))
		{
			waitForLocationDialog = ProgressDialog.show(this, "", getString(R.string.wait_location), true);

			scheduleAlertDialog();

			return true;
		}

		return false;
	}

	private final Handler handler = new Handler();
	private final Runnable showAlertDialog = new Runnable() {
		@Override
		public void run() {
			if (waitForLocationDialog != null) {
				dismissDialog();
				showAlertDialog();
			}
		}
	};

	private void scheduleAlertDialog() {
		handler.removeCallbacks(showAlertDialog);
		handler.postDelayed(showAlertDialog, MAXIMUM_WAIT_TIME_IN_SECONDS * 1000);
	}

	private void finishSave() {
		if (alertDialog != null){
			alertDialog.dismiss();
			alertDialog = null;
		}

		// this isn't necessary, but could be helpful for integration
		setResult(RESULT_OK, new Intent().setData(mCast));
		finish();
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_CANCELED){
			Log.d(TAG, "media adding cancelled");
			mCreateMediaUri = null;
			return;
		}

		switch(requestCode){
			case REQUEST_NEW_PHOTO:
				addMedia(mCreateMediaUri);
				mCreateMediaUri = null;
				break;
			case REQUEST_NEW_VIDEO:
				addMedia(mCreateMediaUri);
				mCreateMediaUri = null;
				break;
			case REQUEST_PICK_MEDIA:
				final Uri media = data.getData();
				if (media != null){
					addMedia(media);
				}
				break;
		}
	}

	@Override
	public void onTabChanged(String tabId) {}

	@Override
	public void onLocationChanged(Location location) {
		if (isBetterLocation(location, currentLocation)) {
			setLocation(location);
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
			} else if (mLocation == null && c.moveToFirst()) {
				loadLocation(c);
			}
			break;

		case LOADER_CASTMEDIA:
			/*
			mCastMediaAdapter.swapCursor(c);
			mTabWidget.setTabChecked(1, c.getCount() != 0);
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
				ProviderUtils.dumpCursorToLog(c, CAST_MEDIA_PROJECTION);
			}
			*/
				if (c.moveToFirst()) {
					Uri thumb = CastMedia.getThumbnail(c,
							c.getColumnIndex(CastMedia._THUMBNAIL),
							c.getColumnIndex(CastMedia._THUMB_LOCAL));

					if (thumb == null) {
						thumb = CastMedia.getMedia(c,

						c.getColumnIndex(CastMedia._MEDIA_URL),
								c.getColumnIndex(CastMedia._LOCAL_URI));
					}

					if (thumb != null) {
						mImageCache.scheduleLoadImage(0, thumb, 320, 320);
					}

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
			//mCastMediaAdapter.swapCursor(null);
			break;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putParcelable(RUNTIME_STATE_CAST_URI, mCast);
		outState.putBoolean(RUNTIME_STATE_FIRST_LOAD, mFirstLoad);
		if (mTabHost != null) {
			outState.putInt(RUNTIME_STATE_CURRENT_TAB, mTabHost.getCurrentTab());
		}
		if (mLocation instanceof GeoPoint){
			// XXX outState.putParcelable(RUNTIME_STATE_LOCATION, (GeoPoint)mLocation);
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
		//mDescriptionView.setText(c.getString(c.getColumnIndexOrThrow(Cast._DESCRIPTION)));

		loadLocation(c);

		mTags.clearAllTags();
		mTags.addTags(TaggableItem.getTags(getContentResolver(), mCast));

		if (mFirstLoad){
			//mTabHost.setCurrentTab(mTabWidget.getNextUncheckedTab());
			mFirstLoad = false;
		}

		final int draftCol = c.getColumnIndexOrThrow(Cast._DRAFT);

		mIsDraft = !c.isNull(draftCol) && c.getInt(draftCol) != 0;

		setEditable(Cast.canEdit(this, c));
		updateDetailsTab();
	}

	private void loadLocation(Cursor c) {
		final Location l = Locatable.toLocation(c);
		if (l != null) {
			setLocation(new GeoPoint((int)(l.getLatitude() * 1E6), (int)(l.getLongitude() * 1E6))); // XXX optimize
		} else {
			startUpdatingLocation();
		}
	}

	private void setEditable(boolean isEditable){
		mIsEditable = isEditable;

		findViewById(R.id.cast_title).setEnabled(isEditable);
		findViewById(R.id.save).setEnabled(isEditable);

		// location
		//mSetLocation.setEnabled(isEditable);

		// media
		findViewById(R.id.new_photo).setEnabled(isEditable);
		//findViewById(R.id.new_video).setEnabled(isEditable);
		//findViewById(R.id.pick_media).setEnabled(isEditable);

		// details
		mTags.setEnabled(isEditable);
		//mDescriptionView.setEnabled(isEditable);
	}

	private void startUpdatingLocation() {
		for(final String provider : locationManager.getProviders(true)) {
			locationManager.requestLocationUpdates(provider, 0, 0, this);
		}
	}

	private void stopUpdatingLocation() {
		locationManager.removeUpdates(this);
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

	/**
	 * Reads from the UI and stateful variables, saving to a ContentValues. CastMedia and tags need to be saved separately.
	 *
	 * @return
	 */
	public ContentValues toContentValues() {
		final ContentValues cv = new ContentValues();

		cv.put(Cast._TITLE, mTitleView.getText().toString());
		cv.put(Cast._DRAFT, mIsDraft);
		//cv.put(Cast._DESCRIPTION, mDescriptionView.getText().toString());
		cv.put(Cast._MODIFIED_DATE, System.currentTimeMillis());

		if (mLocation != null) {
			Locatable.toContentValues(cv, mLocation);
		}

		return cv;
	}

	public void addMedia(Uri content){
		final Uri castMedia = Cast.getCastMediaUri(mCast);
		final ContentValues cv = new ContentValues();

		final long now = System.currentTimeMillis();

		String exifDateTime = "";
		try
		{
			ExifInterface exif = new ExifInterface(mCreateMediaUri.getPath());
			exifDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
		}
		catch (IOException ioex)
		{
			Log.e(TAG, "EXIF: Couldn't find media: " + mCreateMediaUri.getPath());
		}
		
		
		cv.put(CastMedia._MODIFIED_DATE, now);
		cv.put(CastMedia._CREATED_DATE, now);
		cv.put(CastMedia._TITLE, content.getLastPathSegment());
		cv.put(CastMedia._EXIF_DATETIME, exifDateTime);

		String mimeType = getContentResolver().getType(content);
		if (mimeType == null){
			mimeType = CastMedia.guessMimeTypeFromUrl(content.toString());
		}
		cv.put(CastMedia._MIME_TYPE, mimeType);

		String mediaPath = null;

		// only add in credentials on inserts
		final Account me = Authenticator.getFirstAccount(this);
		final AccountManager am = AccountManager.get(this);

		final String displayName = am.getUserData(me, AuthenticationService.USERDATA_DISPLAY_NAME);
		final String authorUri = am.getUserData(me, AuthenticationService.USERDATA_USER_URI);

		if ("content".equals(content.getScheme())){
			final Cursor c = getContentResolver().query(content, new String[]{MediaColumns._ID, MediaColumns.DATA, MediaColumns.TITLE, Media.LATITUDE, Media.LONGITUDE}, null, null, null);
			try {
				if (c.moveToFirst()){
					cv.put(Cast._AUTHOR, displayName);
					cv.put(Cast._AUTHOR_URI, authorUri);

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
							setLocation(new GeoPoint((int)(c.getDouble(latCol) * 1E6), (int)(c.getDouble(lonCol) * 1E6)));
						}
					}
				}
			} finally{
				c.close();
			}
		} else {
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

//		if (mLocation == null) {
//			// focus tab on location
//			return false;
//		}

		return true;
	}

	private void setLocation(GeoPoint location){
		mLocation = location;

		updateLocationStatusText();

		if (waitForLocationDialog != null && currentLocation != null && currentLocation.hasAccuracy() && currentLocation.getAccuracy() <= MINIMUM_REQUIRED_ACCURACY) {
			dismissDialog();
			finishSave();
		}
	}

	private void updateLocationStatusText() {
		final TextView view = (TextView) findViewById(R.id.location_status);
		if (mLocation == null) {
			view.setText(R.string.location_unknown);
		} else {
			view.setText(R.string.location_known);
			//view.setText(view.getText() + " (" + (mLocation.getLatitudeE6() / 1.0E6) + ", " + (mLocation.getLongitudeE6() / 1.0E6) + ")");
		}
	}

	private void dismissDialog() {
		waitForLocationDialog.dismiss();
		waitForLocationDialog = null;
	}

	private void setLocation(Location location) {
		final int e6lat = (int)(location.getLatitude() * 1E6);
    	final int e6lon = (int)(location.getLongitude() * 1E6);

		currentLocation = location;

		setLocation(new GeoPoint(e6lat, e6lon));
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
		if (!mIsEditable){
			return mCast;
		}

		Uri newCast;
		final ContentResolver cr = getContentResolver();
		final ContentValues cv = toContentValues();

		if (mCast == null){
			// only add in credentials on inserts
			final Account me = Authenticator.getFirstAccount(this);
			final AccountManager am = AccountManager.get(this);

			cv.put(Cast._AUTHOR, am.getUserData(me, AuthenticationService.USERDATA_DISPLAY_NAME));
			cv.put(Cast._AUTHOR_URI, am.getUserData(me, AuthenticationService.USERDATA_USER_URI));

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

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}

	private static final int TWO_MINUTES = 1000 * 60 * 2;
	/** Determines whether one Location reading is better than the current Location fix
	 * @param location  The new Location that you want to evaluate
	 * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	 */
	protected boolean isBetterLocation(Location location, Location currentBestLocation) {
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    final long timeDelta = location.getTime() - currentBestLocation.getTime();
	    final boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
	    final boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
	    final boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    final int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    final boolean isLessAccurate = accuracyDelta > 0;
	    final boolean isMoreAccurate = accuracyDelta < 0;
	    final boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    final boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}

	private final ImageCache.OnImageLoadListener mImageCacheLoadListener = new ImageCache.OnImageLoadListener() {

		@Override
		public void onImageLoaded(long id, Uri imageUri, Drawable image) {
			final ImageView imv = (ImageView) findViewById(R.id.media_thumbnail);
			imv.setImageDrawable(image);
		}
	};
}
