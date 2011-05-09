package edu.mit.mobile.android.locast.casts;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4_map.app.LoaderManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.browser.BrowserHome;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.itineraries.CastsOverlay;
import edu.mit.mobile.android.locast.itineraries.LocatableItemOverlay;

public class CastDetail extends LocatableDetail implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener{
	private LoaderManager mLoaderManager;
	private CastsOverlay mCastsOverlay;
	private MapController mMapController;
	private SimpleCursorAdapter mCastMedia;

	private static final int
		LOADER_CAST = 0,
		LOADER_CAST_MEDIA = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_detail);

		initOverlays();

		mMapController = ((MapView)findViewById(R.id.map)).getController();
		mLoaderManager = getSupportLoaderManager();
		mLoaderManager.initLoader(LOADER_CAST, null, this);
		mLoaderManager.initLoader(LOADER_CAST_MEDIA, null, this);
		findViewById(R.id.home).setOnClickListener(this);
		final AbsListView castMediaView = (AbsListView) findViewById(R.id.cast_media);

		mCastMedia = new MediaThumbnailCursorAdapter(this,
				R.layout.cast_media_item,
				null,
//				new String[]{CastMedia._TITLE, CastMedia._AUTHOR, CastMedia._THUMBNAIL},
//				new int[]{R.id.cast_title, R.id.author, R.id.media_thumbnail},
				new String[]{CastMedia._TITLE, CastMedia._AUTHOR},
				new int[]{R.id.title, R.id.author},
				0);

//		mCastMedia = new SimpleCursorAdapter(this,
//				android.R.layout.simple_list_item_2,
//				null,
//				new String[]{CastMedia._TITLE, CastMedia._AUTHOR},
//				new int[]{android.R.id.text1, android.R.id.text2}, 0);

		castMediaView.setAdapter(mCastMedia);
		castMediaView.setOnItemClickListener(this);

		castMediaView.setEnabled(true);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.home:
			startActivity(new Intent(this, BrowserHome.class));
			break;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {

		final Cursor c = (Cursor) adapter.getItemAtPosition(position);
		final String mediaString = c.getString(c.getColumnIndex(CastMedia._MEDIA_URL));
		final String mimeType = c.getString(c.getColumnIndex(CastMedia._MIME_TYPE));
		if (mediaString != null){
			final Uri media = Uri.parse(mediaString);

			final Intent i = new Intent(Intent.ACTION_VIEW, media);
			// setting the MIME type for URLs doesn't work.
			startActivity(i);
		}

	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri data = getIntent().getData();
		switch (id){
		case LOADER_CAST:
			return new CursorLoader(this, data, Cast.PROJECTION, null, null, null);
		case LOADER_CAST_MEDIA:
			return new CursorLoader(this, Cast.getCastMediaUri(data), CastMedia.PROJECTION, null, null, null);
		}
		return null;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		switch (loader.getId()){
		case LOADER_CAST:
			mCastsOverlay.swapCursor(c);
			if (c.moveToFirst()){
				MediaProvider.dumpCursorToLog(c, Cast.PROJECTION);
				((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Cast._TITLE)));
				((TextView)findViewById(R.id.author)).setText(c.getString(c.getColumnIndex(Cast._AUTHOR)));
				((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Cast._DESCRIPTION)));
				final double[] result = new double[2];
				Locatable.toLocationArray(c, c.getColumnIndex(Cast._LATITUDE), c.getColumnIndex(Cast._LONGITUDE), result);
				final GeoPoint gp = new GeoPoint((int)(result[0]  * 1E6), (int)(result[1] * 1E6));
				setPointer(gp);
				mMapController.setCenter(gp);
				mMapController.setZoom(12);

			}

		break;

		case LOADER_CAST_MEDIA:
			mCastMedia.swapCursor(c);
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
				MediaProvider.dumpCursorToLog(c, CastMedia.PROJECTION);
			}
			break;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		switch(loader.getId()){
		case LOADER_CAST:
			mCastsOverlay.swapCursor(null);
			break;

		case LOADER_CAST_MEDIA:
			mCastMedia.swapCursor(null);
			break;
		}
	}

	@Override
	protected LocatableItemOverlay createItemOverlay() {
		mCastsOverlay = new CastsOverlay(this);
		return mCastsOverlay;
	}

}
