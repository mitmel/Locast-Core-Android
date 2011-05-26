package edu.mit.mobile.android.locast.ver2.itineraries;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.imagecache.SimpleThumbnailCursorAdapter;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.ver2.R;

public class ItineraryList extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener {

	private static final String TAG = ItineraryList.class.getSimpleName();
	private CursorAdapter mAdapter;
	private ListView mListView;
	private Uri mUri;

	private ImageCache mImageCache;

	private static String LOADER_DATA = "edu.mit.mobile.android.locast.LOADER_DATA";
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_list_activity);

		findViewById(R.id.refresh).setOnClickListener(this);

		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setOnItemClickListener(this);
		mListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.list_footer, null), null, false);
		mListView.setEmptyView(findViewById(android.R.id.empty));

		final Intent intent = getIntent();
		final String action = intent.getAction();

		mImageCache = ImageCache.getInstance(this);

		if (Intent.ACTION_VIEW.equals(action)){
			final Uri data = intent.getData();
			final String type = intent.resolveType(this);

			if (MediaProvider.TYPE_ITINERARY_DIR.equals(type)){
				mAdapter = new SimpleThumbnailCursorAdapter(this,
						R.layout.browse_content_item,
						null,
				new String[] {Itinerary._TITLE, Itinerary._AUTHOR, Itinerary._THUMBNAIL},
				new int[] {android.R.id.text1, android.R.id.text2, R.id.media_thumbnail},
				new int[]{R.id.media_thumbnail},
				0
				);
				mListView.setAdapter(new ImageLoaderAdapter(this, mAdapter, mImageCache, new int[]{R.id.media_thumbnail}, 48, 48, ImageLoaderAdapter.UNIT_DIP));

				final LoaderManager lm = getSupportLoaderManager();
				final Bundle loaderArgs = new Bundle();
				loaderArgs.putParcelable(LOADER_DATA, data);
				lm.initLoader(0, loaderArgs, this);
				setTitle("Itineraries");
				mUri = data;
				startService(new Intent(Intent.ACTION_SYNC, data));
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh(false);
	}

	@Override
	public void setTitle(CharSequence title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	private void refresh(boolean explicitSync){
		startService(new Intent(Intent.ACTION_SYNC, mUri).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri data = args.getParcelable(LOADER_DATA);

		return new CursorLoader(this, data, Itinerary.PROJECTION, null, null, Itinerary.SORT_DEFAULT);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		Log.d(TAG, "onLoadFinished");
		//mAdapter.swapCursor(c);
		mAdapter.changeCursor(c);

	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		Log.d(TAG, "onLoaderReset");
		mAdapter.changeCursor(null);
		//mAdapter.swapCursor(null);

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
		}
	}
}
