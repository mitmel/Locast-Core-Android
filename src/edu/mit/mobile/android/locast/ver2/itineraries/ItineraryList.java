package edu.mit.mobile.android.locast.ver2.itineraries;
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

import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.appupdater.AppUpdateChecker;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.imagecache.SimpleThumbnailCursorAdapter;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.AuthenticatorActivity;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.browser.BrowserHome;

public class ItineraryList extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener {

	@SuppressWarnings("unused")
	private static final String TAG = ItineraryList.class.getSimpleName();
	private CursorAdapter mAdapter;
	private ListView mListView;
	private Uri mUri;

	private ImageCache mImageCache;

	private boolean firstTime;

	private static final boolean REQUIRE_LOGIN = false;

	private final String[] ITINERARY_DISPLAY = new String[]{Itinerary._TITLE, Itinerary._THUMBNAIL, Itinerary._DESCRIPTION};
	private final String[] ITINERARY_PROJECTION = ArrayUtils.concat(new String[]{Itinerary._ID}, ITINERARY_DISPLAY);

	private AppUpdateChecker mAppUpdateChecker;

	private static String LOADER_DATA = "edu.mit.mobile.android.locast.LOADER_DATA";

	private boolean mSyncWhenLoaded = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_list_activity);

		findViewById(R.id.refresh).setOnClickListener(this);
		findViewById(R.id.home).setOnClickListener(this);

		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setOnItemClickListener(this);
		mListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.list_footer, null), null, false);
		mListView.setEmptyView(findViewById(android.R.id.empty));

		if (Constants.USE_APPUPDATE_CHECKER){
			mAppUpdateChecker = new AppUpdateChecker(this, getString(R.string.app_update_url), new AppUpdateChecker.OnUpdateDialog(this, getString(R.string.app_name)));
			mAppUpdateChecker.checkForUpdates();
		}

		final Intent intent = getIntent();
		final String action = intent.getAction();

		mImageCache = ImageCache.getInstance(this);

		firstTime = checkFirstTime();

		if (REQUIRE_LOGIN){
			final NetworkClient nc = NetworkClient.getInstance(this);

			if (!nc.isAuthenticated()){
				startActivityForResult(new Intent(this, AuthenticatorActivity.class), REQUEST_SIGNIN);
				return;
			}
		}

		if (Intent.ACTION_VIEW.equals(action)){
			loadData(intent.getData());

		}else if (Intent.ACTION_MAIN.equals(action)){
			loadData(Itinerary.CONTENT_URI);
		}


	}

	@Override
	protected void onResume() {
		super.onResume();

		mSyncWhenLoaded = true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {

		super.onCreateContextMenu(menu, v, menuInfo);
	}

	private void loadData(Uri data){
		final String type = getContentResolver().getType(data);

		if (! MediaProvider.TYPE_ITINERARY_DIR.equals(type)){
			throw new IllegalArgumentException("cannot load type: " + type);
		}
		mAdapter = new SimpleThumbnailCursorAdapter(this,
				R.layout.itinerary_item,
				null,
		ITINERARY_DISPLAY,
		new int[] {android.R.id.text1, R.id.media_thumbnail, android.R.id.text2},
		new int[]{R.id.media_thumbnail},
		0
		);
		mListView.setAdapter(new ImageLoaderAdapter(this, mAdapter, mImageCache, new int[]{R.id.media_thumbnail}, 48, 48, ImageLoaderAdapter.UNIT_DIP));

		final LoaderManager lm = getSupportLoaderManager();
		final Bundle loaderArgs = new Bundle();
		loaderArgs.putParcelable(LOADER_DATA, data);
		lm.initLoader(0, loaderArgs, this);
		setTitle(R.string.itineraries);
		mUri = data;

	}
	@Override
	public void setTitle(CharSequence title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	@Override
	public void setTitle(int title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	private void refresh(boolean explicitSync){
		LocastSyncService.startSync(this, mUri, explicitSync);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri data = args.getParcelable(LOADER_DATA);

		final CursorLoader cl = new CursorLoader(this, data, ITINERARY_PROJECTION, null, null, Itinerary.SORT_DEFAULT);
		cl.setUpdateThrottle(Constants.UPDATE_THROTTLE);
		return cl;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mAdapter.swapCursor(c);
		if (mSyncWhenLoaded){
			mSyncWhenLoaded = false;
			if (mListView.getAdapter().isEmpty()){
				LocastSyncService.startExpeditedAutomaticSync(this, mUri);
			}else{
				refresh(false);
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		mAdapter.swapCursor(null);

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

		case R.id.home:
			startActivity(new Intent(this, BrowserHome.class));
			break;
		}
	}

	private final static int REQUEST_SIGNIN = 0;


	/**
	 * @return true if this seems to be the first time running the app
	 */
	private boolean checkFirstTime(){
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final boolean skip = prefs.getBoolean(Authenticator.PREF_SKIP_AUTH, false);

		return skip;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_SIGNIN:
			if (resultCode == RESULT_CANCELED){
				finish();
			}else if (resultCode == RESULT_OK){
				refresh(false);
			}
			break;

		default:
			break;
		}
	}
}
