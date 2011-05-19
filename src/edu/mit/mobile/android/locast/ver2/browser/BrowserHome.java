package edu.mit.mobile.android.locast.ver2.browser;

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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Gallery;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.casts.LocatableListWithMap;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.SigninOrSkip;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.net.NetworkClient;

public class BrowserHome extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener{

	private ImageCache mImageCache;

	private CastCursorAdapter mAdapter;

	private static final Uri FEATURED_CASTS = Cast.getTagUri(Cast.CONTENT_URI, Cast.addPrefixToTag(Cast.SYSTEM_PREFIX, "_featured"));

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mImageCache = ImageCache.getInstance(this);

		setContentView(R.layout.browser_main);

		final Gallery casts = (Gallery) findViewById(R.id.casts);

		final String[] from = {Cast._TITLE, Cast._AUTHOR, Cast._THUMBNAIL_URI};
		final int[] to = {R.id.cast_title, R.id.author, R.id.media_thumbnail};

		mAdapter = new CastCursorAdapter(this, null, R.layout.cast_large_thumbnail_item, from, to);
		casts.setAdapter(new ImageLoaderAdapter(this, mAdapter, mImageCache, new int[]{R.id.media_thumbnail}, 320, 200, ImageLoaderAdapter.UNIT_DIP));
		casts.setOnItemClickListener(this);
		casts.setEmptyView(findViewById(android.R.id.empty));
		final LoaderManager lm = getSupportLoaderManager();
		lm.initLoader(LOADER_FEATURED_CASTS, null, this);

		findViewById(R.id.refresh).setOnClickListener(this);
		findViewById(R.id.itineraries).setOnClickListener(this);
		findViewById(R.id.events).setOnClickListener(this);
		findViewById(R.id.nearby).setOnClickListener(this);

		checkFirstTime();
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh(false);
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
		startService(new Intent(Intent.ACTION_SYNC, FEATURED_CASTS).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
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

		case R.id.nearby:

			startActivity(new Intent(LocatableListWithMap.ACTION_SEARCH_NEARBY, Cast.CONTENT_URI));
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
		mAdapter.changeCursor(c);

	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		mAdapter.changeCursor(null);

	}
}
