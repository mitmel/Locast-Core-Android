package edu.mit.mobile.android.locast.itineraries;

import java.util.ArrayList;
import java.util.Random;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
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

public class ItineraryDetail extends MapActivity {

	private MapView mMapView;
	private MapController mMapController;
	private ListView mCastView;

	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	@Override
	protected void onCreate(Bundle icicle) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(icicle);
		setContentView(R.layout.itinerary_detail);

		imgCache = ((Application)getApplication()).getImageCache();

		mCastView = (ListView)findViewById(R.id.casts);

		mCastView.addHeaderView(getLayoutInflater().inflate(R.layout.itinerary_detail_list_header, mCastView, false));

		((TextView)findViewById(R.id.description)).setText(R.string.itinerary_test_text);

		mMapView = (MapView)findViewById(R.id.map);
		mMapController = mMapView.getController();

		new ItineraryLoader().execute(Cast.CONTENT_URI);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	private class ItineraryLoader extends AsyncTask<Uri, Long, Cursor>{

		@Override
		protected void onPreExecute() {
			findViewById(R.id.progress).setVisibility(View.VISIBLE);
		}

		@Override
		protected Cursor doInBackground(Uri... params) {
			final Cursor casts = ItineraryDetail.this.managedQuery(params[0], Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);
			return casts;
		}

		@Override
		protected void onPostExecute(Cursor casts) {
			findViewById(R.id.progress).setVisibility(View.INVISIBLE);

			final CastCursorAdapter castAdapter = new CastCursorAdapter(ItineraryDetail.this, casts);


			mCastView.setAdapter((new ThumbnailAdapter(ItineraryDetail.this, castAdapter, imgCache, new int[]{R.id.media_thumbnail})));

			final ItineraryOverlay itineraryOverlay = new ItineraryOverlay(ItineraryDetail.this);
			mMapView.getOverlays().add(itineraryOverlay);

			mMapController.zoomToSpan(itineraryOverlay.getLatSpanE6(), itineraryOverlay.getLonSpanE6());
			mMapController.setCenter(itineraryOverlay.getCenter());
		}
	}



	private class ItineraryOverlay extends ItemizedOverlay<OverlayItem> {
		Random r = new Random();
		ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();

		public ItineraryOverlay(Context context) {
			super(boundCenterBottom(context.getResources().getDrawable(R.drawable.map_marker_user_cast)));

			int lat = 42464753;
			int lon = 14230299;
			for (int i = 0; i < 5; i++){
				lat += r.nextInt(10000) - 5000;
				lon += r.nextInt(10000) - 5000;
				items.add(new OverlayItem(new GeoPoint(lat, lon), "foo", "bar"));
			}
			populate();
		}

		@Override
		protected OverlayItem createItem(int i) {
			return items.get(i);

		}


		@Override
		public int size() {

			return items.size();
		}

	}


}