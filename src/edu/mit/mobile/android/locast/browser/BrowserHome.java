package edu.mit.mobile.android.locast.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailAdapter;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;

import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.SigninOrSkip;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.net.NetworkClient;

public class BrowserHome extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener{

	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	private CastCursorAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		imgCache = ((Application)getApplication()).getImageCache();
		setContentView(R.layout.browser_main);

		final Gallery casts = (Gallery) findViewById(R.id.casts);

		final String[] from = {Cast._TITLE, Cast._AUTHOR, Cast._THUMBNAIL_URI};
		final int[] to = {R.id.cast_title, R.id.author, R.id.media_thumbnail};

		mAdapter = new CastCursorAdapter(this, null, R.layout.cast_large_thumbnail_item, from, to);
		casts.setAdapter(new ThumbnailAdapter(this, mAdapter,imgCache, new int[]{R.id.media_thumbnail}));
		casts.setOnItemClickListener(this);
		casts.setEmptyView(findViewById(android.R.id.empty));
		final LoaderManager lm = getSupportLoaderManager();
		lm.initLoader(LOADER_FEATURED_CASTS, null, this);

		findViewById(R.id.itineraries).setOnClickListener(this);
		findViewById(R.id.events).setOnClickListener(this);
		findViewById(R.id.nearby).setOnClickListener(this);

		checkFirstTime();
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
		}

	}

	private static final int LOADER_FEATURED_CASTS = 0;

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		switch(id){
		case LOADER_FEATURED_CASTS:
			return new CursorLoader(this,
					Cast.getTagUri(Cast.CONTENT_URI, Cast.addPrefixToTag(Cast.SYSTEM_PREFIX, "_featured")),
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
}
