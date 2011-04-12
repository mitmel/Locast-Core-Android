package edu.mit.mobile.android.locast.itineraries;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.Loader.OnLoadCompleteListener;
import android.view.LayoutInflater;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailAdapter;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.Locatable;

public class ItineraryDetail extends MapActivity implements OnLoadCompleteListener<Cursor> {

	private MapView mMapView;
	private MapController mMapController;
	private ListView mCastView;

	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	//private final BasicCursorContentObserver mContentObserver = new BasicCursorContentObserver(this);

	private Cursor mItinerary;
	private Uri mUri;

	private ItineraryOverlay mItineraryOverlay;

	@Override
	protected void onCreate(Bundle icicle) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(icicle);
		setContentView(R.layout.itinerary_detail);

		imgCache = ((Application)getApplication()).getImageCache();

		mCastView = (ListView)findViewById(R.id.casts);

		final LayoutInflater layoutInflater = getLayoutInflater();
		mCastView.addHeaderView(layoutInflater.inflate(R.layout.itinerary_detail_list_header, mCastView, false));
		mCastView.setEmptyView(layoutInflater.inflate(R.layout.itinerary_detail_list_empty, mCastView, false));

		mCastView.setAdapter(null);

		mMapView = (MapView)findViewById(R.id.map);
		mMapController = mMapView.getController();

		final Intent intent = getIntent();
		final String action = intent.getAction();

		if (Intent.ACTION_VIEW.equals(action)){
			mUri = intent.getData();
			//new ItineraryLoader().execute(mUri);
			final Uri casts = Itinerary.getCastsUri(mUri);

			final CursorLoader itinCl = new CursorLoader(this, mUri, Itinerary.PROJECTION, null, null, null);
			itinCl.registerListener(0, this);
			mItinerary = itinCl.loadInBackground();

			final CursorLoader castCl = new CursorLoader(this, casts, Itinerary.PROJECTION, null, null, null);
			castCl.registerListener(0, mCastLoadCompleteListener);
			initCastList(castCl.loadInBackground());

		}else{
			finish();
		}
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	public void loadFromCursor() {
		final Cursor c = mItinerary;

		((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Itinerary._DESCRIPTION)));
		((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Itinerary._TITLE)));
		((TextView)findViewById(R.id.author)).setText(c.getString(c.getColumnIndex(Itinerary._AUTHOR)));
	}

	public void onCursorItemDeleted() {
		finish();

	}

	@Override
	protected void onPause() {
		super.onPause();
	}

//	private class ItineraryLoader extends AsyncTask<Uri, Long, Cursor>{
//		private ProgressDialog mPd;
//
//		@Override
//		protected void onPreExecute() {
//			mPd = ProgressDialog.show(ItineraryDetail.this, "", "Loading itinerary...");
//		}
//
//		@Override
//		protected Cursor doInBackground(Uri... params) {
//
//			final Cursor casts = ItineraryDetail.this.managedQuery(params[0], Cast.PROJECTION, Cast._LATITUDE+" not null", null, Cast.SORT_ORDER_DEFAULT);
//			return casts;
//		}
//
//		@Override
//		protected void onPostExecute(Cursor itinerary) {
//			mPd.dismiss();
//			final Uri castsUri = Itinerary.getCastsUri(itinerary);
//			final CastCursorAdapter castAdapter = new CastCursorAdapter(ItineraryDetail.this, casts);
//
//		}
//	}

	private void initCastList(Cursor casts){
		final CastCursorAdapter castAdapter = new CastCursorAdapter(ItineraryDetail.this, casts);

		mCastView.setAdapter((new ThumbnailAdapter(ItineraryDetail.this, castAdapter, imgCache, new int[]{R.id.media_thumbnail})));
		mItineraryOverlay = new ItineraryOverlay(ItineraryDetail.this, casts);
		mMapView.getOverlays().add(mItineraryOverlay);
	}

	private final OnLoadCompleteListener<Cursor> mCastLoadCompleteListener = new OnLoadCompleteListener<Cursor>() {
		@Override
		public void onLoadComplete(Loader<Cursor> loader, Cursor cast) {

			mMapController.zoomToSpan(mItineraryOverlay.getLatSpanE6(), mItineraryOverlay.getLonSpanE6());
			mMapController.setCenter(mItineraryOverlay.getCenter());
		}
	};

	@Override
	public void onLoadComplete(Loader<Cursor> loader, Cursor c) {

		((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Itinerary._DESCRIPTION)));
		((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Itinerary._TITLE)));
		((TextView)findViewById(R.id.author)).setText(c.getString(c.getColumnIndex(Itinerary._AUTHOR)));

	}

	private class ItineraryOverlay extends ItemizedOverlay<OverlayItem> {
		private final Cursor mCasts;
		private final int mTitleCol, mDescriptionCol;

		public ItineraryOverlay(Context context, Cursor casts) {
			super(boundCenterBottom(context.getResources().getDrawable(R.drawable.ic_map_community)));

			mCasts = casts;
			mTitleCol = mCasts.getColumnIndex(Cast._TITLE);
			mDescriptionCol = mCasts.getColumnIndex(Cast._DESCRIPTION);

			populate();
		}

		@Override
		protected OverlayItem createItem(int i) {
			mCasts.moveToPosition(i);

			final Location castLoc =  Locatable.toLocation(mCasts);
			final GeoPoint castLocGP = new GeoPoint((int)(castLoc.getLatitude()*1E6), (int)(castLoc.getLongitude()*1E6));

			return new OverlayItem(castLocGP, mCasts.getString(mTitleCol), mCasts.getString(mDescriptionCol));
		}


		@Override
		public int size() {

			return mCasts.getCount();
		}
	}
}