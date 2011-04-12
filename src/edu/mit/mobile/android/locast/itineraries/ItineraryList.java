package edu.mit.mobile.android.locast.itineraries;

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
import android.widget.ListView;
import android.widget.TextView;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.MediaProvider;

public class ItineraryList extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {

	private CursorAdapter mAdapter;
	private ListView mListView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_list_activity);

		mListView = (ListView) findViewById(android.R.id.list);

		final Intent intent = getIntent();
		final String action = intent.getAction();


		if (Intent.ACTION_VIEW.equals(action)){
			final Uri data = intent.getData();
			final String type = intent.resolveType(this);

			if (MediaProvider.TYPE_ITINERARY_DIR.equals(type)){
				mAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, null,
				new String[] {Itinerary._TITLE, Itinerary._AUTHOR},
				new int[] {android.R.id.text1, android.R.id.text2}, 0
				);
				mListView.setAdapter(mAdapter);
				mListView.setEmptyView(findViewById(android.R.id.empty));
				final LoaderManager lm = getSupportLoaderManager();
				lm.initLoader(0, null, this);
				setTitle("Itineraries");
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
		final Intent intent = getIntent();
		final String action = intent.getAction();

		if (Intent.ACTION_VIEW.equals(action)){
			final Uri data = intent.getData();
			final String type = intent.resolveType(this);
			if (MediaProvider.TYPE_ITINERARY_DIR.equals(type)){
				return new CursorLoader(this, data, Itinerary.PROJECTION, null, null, Itinerary.SORT_DEFAULT);

			}
		}

		return null;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mAdapter.changeCursor(c);

	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		// TODO Auto-generated method stub

	}


}
