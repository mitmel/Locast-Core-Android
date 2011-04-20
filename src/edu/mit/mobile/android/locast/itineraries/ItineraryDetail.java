package edu.mit.mobile.android.locast.itineraries;

import java.util.List;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.Loader.OnLoadCompleteListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;

public class ItineraryDetail extends MapActivity implements OnLoadCompleteListener<Cursor>, OnItemClickListener {
	private static final String TAG = ItineraryDetail.class.getSimpleName();

	private MapView mMapView;
	private MapController mMapController;
	private ListView mCastView;
	private CastCursorAdapter mCastAdapter;

	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	private Uri mUri;
	private Uri mCastsUri;

	private CastsOverlay mItineraryOverlay;
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

			itinLoader = new CursorLoader(this, mUri, Itinerary.PROJECTION, null, null, null);
			castLoader = new CursorLoader(this, mCastsUri, Cast.PROJECTION, null, null, null);

			initCastList();

		}else{
			finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();


		itinLoader.registerListener(0, this);
		itinLoader.startLoading();
		castLoader.registerListener(0, mCastLoadCompleteListener);
		castLoader.startLoading();

	}

	@Override
	protected void onPause() {
		super.onPause();
		itinLoader.stopLoading();
		itinLoader.unregisterListener(this);
		castLoader.stopLoading();
		castLoader.unregisterListener(mCastLoadCompleteListener);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	private void initCastList(){
		mCastAdapter = new CastCursorAdapter(ItineraryDetail.this, null);
		mCastView.setAdapter(new ThumbnailAdapter(ItineraryDetail.this, mCastAdapter, imgCache, new int[]{R.id.media_thumbnail}));
		mItineraryOverlay = new CastsOverlay(ItineraryDetail.this, null);
		final List<Overlay> overlays = mMapView.getOverlays();
		mPathOverlay = new PathOverlay(this);
		overlays.add(mPathOverlay);
		overlays.add(mItineraryOverlay);
	}

	private final OnLoadCompleteListener<Cursor> mCastLoadCompleteListener = new OnLoadCompleteListener<Cursor>() {
		@Override
		public void onLoadComplete(Loader<Cursor> loader, Cursor casts) {
			mCastAdapter.swapCursor(casts);
			mItineraryOverlay.swapCursor(casts);
			if (casts.moveToFirst()){
				mMapController.zoomToSpan(mItineraryOverlay.getLatSpanE6(), mItineraryOverlay.getLonSpanE6());
				mMapController.setCenter(mItineraryOverlay.getCenter());
				mMapView.setVisibility(View.VISIBLE);
			}
		}
	};

	@Override
	public void onLoadComplete(Loader<Cursor> loader, Cursor c) {
		if (c.moveToFirst()){
			((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Itinerary._DESCRIPTION)));
			((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Itinerary._TITLE)));
			((TextView)findViewById(R.id.author)).setText("Itinerary by " + c.getString(c.getColumnIndex(Itinerary._AUTHOR)));
			final List<GeoPoint> path = Itinerary.getPath(c);
			mPathOverlay.setPath(path);
		}else{
			Log.e(TAG, "error loading itinerary");
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mCastsUri, id)));

	}
}