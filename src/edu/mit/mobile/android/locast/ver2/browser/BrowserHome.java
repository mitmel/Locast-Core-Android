package edu.mit.mobile.android.locast.ver2.browser;
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

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncInfo;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Gallery;
import edu.mit.mobile.android.appupdater.AppUpdateChecker;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.AuthenticatorActivity;
import edu.mit.mobile.android.locast.accounts.SigninOrSkip;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Event;
import edu.mit.mobile.android.locast.data.Favoritable;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.casts.LocatableListWithMap;
import edu.mit.mobile.android.widget.RefreshButton;

public class BrowserHome extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener{

	private ImageCache mImageCache;

	private CastCursorAdapter mAdapter;
	private AppUpdateChecker mAppUpdateChecker;

	private static final Uri FEATURED_CASTS = Cast.getTagUri(Cast.CONTENT_URI, Cast.addPrefixToTag(Cast.SYSTEM_PREFIX, "_featured"));

	private boolean shouldRefresh;

	private static final int
		MSG_SET_REFRESHING = 100,
		MSG_SET_NOT_REFRESHING = 101;

	private static final String TAG = BrowserHome.class.getSimpleName();

	private final Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
			case MSG_SET_REFRESHING:
				Log.d(TAG, "refreshing...");
				mRefresh.setRefreshing(true);
				break;

			case MSG_SET_NOT_REFRESHING:
				Log.d(TAG, "done loading.");
				mRefresh.setRefreshing(false);
				break;
			}
		};
	};

	private RefreshButton mRefresh;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mImageCache = ImageCache.getInstance(this);

		setContentView(R.layout.browser_main);

		if (Constants.USE_APPUPDATE_CHECKER){
			mAppUpdateChecker = new AppUpdateChecker(this, getString(R.string.app_update_url), new AppUpdateChecker.OnUpdateDialog(this, getString(R.string.app_name)));
			mAppUpdateChecker.checkForUpdates();
		}

		final Gallery casts = (Gallery) findViewById(R.id.casts);

		final String[] from = {Cast._TITLE, Cast._AUTHOR, Cast._THUMBNAIL_URI};
		final int[] to = {R.id.cast_title, R.id.author, R.id.media_thumbnail};

		mAdapter = new CastCursorAdapter(this, null, R.layout.cast_large_thumbnail_item, from, to);
		casts.setAdapter(new ImageLoaderAdapter(this, mAdapter, mImageCache, new int[]{R.id.media_thumbnail}, 320, 200, ImageLoaderAdapter.UNIT_DIP));
		casts.setOnItemClickListener(this);
		casts.setEmptyView(findViewById(android.R.id.empty));
		final LoaderManager lm = getSupportLoaderManager();
		lm.initLoader(LOADER_FEATURED_CASTS, null, this);

		mRefresh = (RefreshButton) findViewById(R.id.refresh);
		mRefresh.setOnClickListener(this);
		findViewById(R.id.itineraries).setOnClickListener(this);
		findViewById(R.id.events).setOnClickListener(this);
		findViewById(R.id.nearby).setOnClickListener(this);
		findViewById(R.id.favorites).setOnClickListener(this);

		shouldRefresh = !checkFirstTime();
	}

	private Object mSyncHandle;

	@Override
	protected void onPause() {
		super.onPause();

		if (mSyncHandle != null){
			ContentResolver.removeStatusChangeListener(mSyncHandle);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSyncHandle = ContentResolver.addStatusChangeListener(0xff, new SyncStatusObserver() {

			@Override
			public void onStatusChanged(int which) {
				final SyncInfo info = ContentResolver.getCurrentSync();
				if (! MediaProvider.AUTHORITY.equals(info.authority)){
					return;
				}
				Log.d(TAG, "onStatusChanged " + which);

				mHandler.sendEmptyMessage(which == ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE ? MSG_SET_REFRESHING : MSG_SET_NOT_REFRESHING);

			}
		});

		if (shouldRefresh) {
			refresh(false);
		}

	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		final Cursor c = (Cursor) adapter.getAdapter().getItem(position);
		startActivity(new Intent(Intent.ACTION_VIEW, Cast.getCanonicalUri(c)));
	}

	private final static int REQUEST_SIGNIN = 0;

	/**
	 * @return true if this seems to be the first time running the app
	 */
	private boolean checkFirstTime(){
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final boolean skip = prefs.getBoolean(Authenticator.PREF_SKIP_AUTH, false);

		final NetworkClient nc = NetworkClient.getInstance(this);

		if (nc.isAuthenticated() || skip){
			return false;
		}
		startActivityForResult(new Intent(this, SigninOrSkip.class), REQUEST_SIGNIN);
		return true;
	}


	private void refresh(boolean explicitSync){
		Bundle b = new Bundle();
		final ContentResolver cr = getContentResolver();

		if (explicitSync){
			b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
		}
		b.putString(LocastSyncService.EXTRA_SYNC_URI, FEATURED_CASTS.toString());
		getContentResolver();
		//getContentResolver().startSync(FEATURED_CASTS, b);
		ContentResolver.requestSync(Authenticator.getFirstAccount(this), MediaProvider.AUTHORITY, b);

		b = new Bundle();
		b.putString(LocastSyncService.EXTRA_SYNC_URI, Itinerary.CONTENT_URI.toString());
		ContentResolver.requestSync(Authenticator.getFirstAccount(this), MediaProvider.AUTHORITY, b);
		//startService(new Intent(Intent.ACTION_SYNC, FEATURED_CASTS).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_SIGNIN:
			if (resultCode == RESULT_CANCELED){
				finish();
			}
			break;

		default:
			break;
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.itineraries:
			startActivity(new Intent(Intent.ACTION_VIEW, Itinerary.CONTENT_URI));
			break;

		case R.id.events:
			startActivity(new Intent(Intent.ACTION_VIEW, Event.CONTENT_URI));
			break;

		case R.id.nearby:

			startActivity(new Intent(LocatableListWithMap.ACTION_SEARCH_NEARBY, Cast.CONTENT_URI));
			break;
		case R.id.favorites:

			startActivity(new Intent(Intent.ACTION_VIEW, Favoritable.getFavoritedUri(Cast.CONTENT_URI, true)));
			break;

		case R.id.refresh:
			refresh(true);
			break;
		}

	}

	private static final int LOADER_FEATURED_CASTS = 0;

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		switch(id){
		case LOADER_FEATURED_CASTS:
			return new CursorLoader(this,
					FEATURED_CASTS,
					Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);

			default:
				return null;
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mAdapter.swapCursor(c);

	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		mAdapter.swapCursor(null);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.homescreen_menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
		case R.id.login:
			startActivity(new Intent(this, AuthenticatorActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
