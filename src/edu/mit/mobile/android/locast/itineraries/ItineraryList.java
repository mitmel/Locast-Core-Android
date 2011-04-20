package edu.mit.mobile.android.locast.itineraries;

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
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.MediaProvider;

public class ItineraryList extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener {

	private CursorAdapter mAdapter;
	private ListView mListView;
	private Uri mUri;

	private static String LOADER_DATA = "edu.mit.mobile.android.locast.LOADER_DATA";
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_list_activity);

		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setOnItemClickListener(this);
		mListView.setEmptyView(findViewById(android.R.id.empty));

		final Intent intent = getIntent();
		final String action = intent.getAction();


		if (Intent.ACTION_VIEW.equals(action)){
			final Uri data = intent.getData();
			final String type = intent.resolveType(this);

			if (MediaProvider.TYPE_ITINERARY_DIR.equals(type)){
				mAdapter = new SimpleCursorAdapter(this,
						R.layout.browse_content_item,
						null,
				new String[] {Itinerary._TITLE, Itinerary._AUTHOR},
				new int[] {android.R.id.text1, android.R.id.text2}, 0
				);
				mListView.setAdapter(mAdapter);

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
	public void setTitle(CharSequence title){
		super.setTitle(title);
		((TextView)findViewById(android.R.id.title)).setText(title);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri data = args.getParcelable(LOADER_DATA);

		return new CursorLoader(this, data, Itinerary.PROJECTION, null, null, Itinerary.SORT_DEFAULT);
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
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mUri, id)));
	}
}
