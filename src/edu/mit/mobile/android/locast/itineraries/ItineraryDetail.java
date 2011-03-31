package edu.mit.mobile.android.locast.itineraries;

import android.database.Cursor;
import android.os.Bundle;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;

public class ItineraryDetail extends MapActivity {

	private MapView mMapView;
	private MapController mMapController;

	@Override
	protected void onCreate(Bundle icicle) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(icicle);
		setContentView(R.layout.itinerary_detail);

		final ListView castView = (ListView)findViewById(R.id.casts);
		final Cursor casts = managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);
		final CastCursorAdapter castAdapter = new CastCursorAdapter(this, casts);

		castView.addHeaderView(getLayoutInflater().inflate(R.layout.itinerary_detail_list_header, castView, false));

		castView.setAdapter(castAdapter);

		((TextView)findViewById(R.id.description)).setText(R.string.itinerary_test_text);

		mMapView = (MapView)findViewById(R.id.map);
		mMapController = mMapView.getController();

		mMapController.setCenter(new GeoPoint(42464753,14230299));
		mMapController.setZoom(16);

	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
}
