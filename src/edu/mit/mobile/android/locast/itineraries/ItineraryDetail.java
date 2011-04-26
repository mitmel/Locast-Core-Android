package edu.mit.mobile.android.locast.itineraries;

import java.util.List;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4_map.app.LoaderManager;
import android.support.v4_map.app.MapFragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailAdapter;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.Sync;

public class ItineraryDetail extends MapFragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener {
	private static final String TAG = ItineraryDetail.class.getSimpleName();

	private MapView mMapView;
	private MapController mMapController;
	private ListView mCastView;
	private CastCursorAdapter mCastAdapter;

	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	private Uri mUri;
	private Uri mCastsUri;

	private CastsOverlay mCastsOverlay;
	private PathOverlay mPathOverlay;

	CursorLoader itinLoader;
	CursorLoader castLoader;

	@Override
	protected void onCreate(Bundle icicle) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(icicle);
		setContentView(R.layout.itinerary_detail);

		imgCache = ((Application)getApplication()).getImageCache();

		mCastView = (ListView)findViewById(R.id.casts);
		findViewById(R.id.refresh).setOnClickListener(this);

		final LayoutInflater layoutInflater = getLayoutInflater();
		mCastView.addHeaderView(layoutInflater.inflate(R.layout.itinerary_detail_list_header, mCastView, false), null, false);
		mCastView.addFooterView(layoutInflater.inflate(R.layout.itinerary_detail_list_footer, mCastView, false), null, false);
		mCastView.setEmptyView(layoutInflater.inflate(R.layout.itinerary_detail_list_empty, mCastView, false));
		mCastView.setOnItemClickListener(this);

		mCastView.setAdapter(null);

		mMapView = (MapView)findViewById(R.id.map);
		mMapController = mMapView.getController();

		final Intent intent = getIntent();
		final String action = intent.getAction();

		if (Intent.ACTION_VIEW.equals(action)){
			mUri = intent.getData();

			mCastsUri = Itinerary.getCastsUri(mUri);

			final LoaderManager lm = getSupportLoaderManager();
			Bundle args = new Bundle();
			args.putParcelable(LOADER_ARG_DATA, mUri);
			lm.initLoader(LOADER_ITINERARY, args, this);

			args = new Bundle();
			args.putParcelable(LOADER_ARG_DATA, mCastsUri);
			lm.initLoader(LOADER_CASTS, args, this);

			initCastList();

		}else{
			finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		refresh(false);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	private void initCastList(){
		mCastAdapter = new CastCursorAdapter(ItineraryDetail.this, null);
		mCastView.setAdapter(new ThumbnailAdapter(ItineraryDetail.this, mCastAdapter, imgCache, new int[]{R.id.media_thumbnail}));
		mCastsOverlay = new CastsOverlay(ItineraryDetail.this);
		final List<Overlay> overlays = mMapView.getOverlays();
		mPathOverlay = new PathOverlay(this);
		overlays.add(mPathOverlay);
		overlays.add(mCastsOverlay);
	}

	private void refresh(boolean explicitSync){
		startService(new Intent(Intent.ACTION_SYNC, mUri).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
	}


	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mCastsUri, id)));

	}

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.refresh:
			refresh(true);
			break;
		}
	}

	private static final int
		LOADER_ITINERARY = 0,
		LOADER_CASTS = 1;
	private static final String
		LOADER_ARG_DATA = "edu.mit.mobile.android.locast.LOADER_ARG_DATA";

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri uri = args.getParcelable(LOADER_ARG_DATA);

		switch (id){
		case LOADER_ITINERARY:
			return new CursorLoader(this, uri, Itinerary.PROJECTION, null, null, null);

		case LOADER_CASTS:
			return new CursorLoader(this, uri, Cast.PROJECTION, null, null, null);

		}

		return null;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		switch (loader.getId()){
		case LOADER_ITINERARY:{
			if (c.moveToFirst()){
				((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Itinerary._DESCRIPTION)));
				((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Itinerary._TITLE)));
				((TextView)findViewById(R.id.author)).setText("Itinerary by " + c.getString(c.getColumnIndex(Itinerary._AUTHOR)));
				final List<GeoPoint> path = Itinerary.getPath(c);
				mPathOverlay.setPath(path);

				mMapController.zoomToSpan(mPathOverlay.getLatSpanE6(), mPathOverlay.getLonSpanE6());
				mMapController.setCenter(mPathOverlay.getCenter());
				mMapView.setVisibility(View.VISIBLE);
			}else{
				Log.e(TAG, "error loading itinerary");
			}

			}break;

		case LOADER_CASTS:{
			mCastAdapter.swapCursor(c);
			mCastsOverlay.swapCursor(c);
		}break;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		switch (loader.getId()){
		case LOADER_CASTS:
			mCastAdapter.swapCursor(null);
			mCastsOverlay.swapCursor(null);
			break;

		case LOADER_ITINERARY:

			break;
		}
	}
}