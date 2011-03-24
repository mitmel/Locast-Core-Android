package edu.mit.mobile.android.locast.casts;

import java.util.Date;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapView;
import org.andnav.osm.views.OpenStreetMapViewController;
import org.andnav.osm.views.overlay.MyLocationOverlay;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;
import edu.mit.mobile.android.locast.IncrementalLocator;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.casts.EditCastActivity.UpdateRecommendedTagsTask;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.widget.LocationLink;
import edu.mit.mobile.android.locast.widget.TagList;

public class Moment extends Activity implements LocationListener, OnClickListener {
	private IncrementalLocator iloc;
	private Location mLocation;
	private LocationLink mLocationLink;
	private OpenStreetMapView osmView;
	private OpenStreetMapViewController osmController;
	private Uri saveToUri = Cast.CONTENT_URI;
	private MyLocationOverlay myLocationOverlay;
	private EditCastActivity.UpdateRecommendedTagsTask tagRecommendationTask;

	private TagList tags;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setTitle("Mark a moment");

        requestWindowFeature(Window.FEATURE_PROGRESS);
		setContentView(R.layout.moment);
		mLocationLink = (LocationLink) findViewById(R.id.location);
		mLocationLink.setShowAccuracy(false);
		osmView = (OpenStreetMapView)findViewById(R.id.map);
		osmController = new OpenStreetMapViewController(osmView);
		myLocationOverlay = new MyLocationOverlay(this, osmView);
		osmView.getOverlays().add(myLocationOverlay);

		tags = (TagList)findViewById(R.id.tags);

		findViewById(R.id.save).setOnClickListener(this);
		findViewById(R.id.cancel).setOnClickListener(this);

		iloc = new IncrementalLocator(this);

		final Uri data = getIntent().getData();
		if (Intent.ACTION_INSERT.equals(data)){
			saveToUri = data;
		}
	}

	private void updateLocation(Location location){
		mLocation = location;
		mLocationLink.setLocation(location);

		osmController.setZoom(osmView.getMaxZoomLevel());
		osmController.animateTo(new GeoPoint(location));

		if (tagRecommendationTask == null || tagRecommendationTask.getStatus() == AsyncTask.Status.FINISHED){
			tagRecommendationTask = new UpdateRecommendedTagsTask(getApplicationContext(), tags);
			tagRecommendationTask.execute(location);
		}
	}

	private Uri save(){
		final ContentResolver cr = getContentResolver();
		final Uri myUri = cr.insert(saveToUri, toContentValues());
		Cast.putTags(cr, myUri, tags.getTags());
		return myUri;
	}

	private ContentValues toContentValues(){
		final ContentValues cv = new ContentValues();
		final CharSequence time = DateFormat.format("k:mm:ss d MMM", new Date().getTime());
		cv.put(Cast._TITLE, "moment "+ time +" @"+ mLocationLink.getText().toString());
		cv.put(Cast._DESCRIPTION, ((EditText)findViewById(R.id.text)).getText().toString());
		cv.put(Cast._AUTHOR, NetworkClient.getInstance(this).getUsername());
		cv.put(Cast._PRIVACY, Cast.PRIVACY_PUBLIC);
		if (mLocation != null){
			cv.put(Cast._LATITUDE, mLocation.getLatitude());
			cv.put(Cast._LONGITUDE, mLocation.getLongitude());
		}

		return cv;
	}

	@Override
	protected void onPause() {
		super.onPause();
		iloc.removeLocationUpdates(this);
		myLocationOverlay.disableMyLocation();
	}

	@Override
	protected void onResume() {
		super.onResume();
		iloc.requestLocationUpdates(this);
		myLocationOverlay.enableMyLocation();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.save:{
			final Uri cast = save();
			if (cast != null){
				Toast.makeText(this, R.string.moment_saved, Toast.LENGTH_SHORT).show();
				finish();
			}else{
				Toast.makeText(this, R.string.error_moment_saving_moment, Toast.LENGTH_LONG).show();
			}
		}break;

		case R.id.cancel:
			finish();
			break;
		}

	}

	@Override
	public void onLocationChanged(Location location) {
		updateLocation(location);
	}

	@Override
	public void onProviderDisabled(String provider) {}

	@Override
	public void onProviderEnabled(String provider) {}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {}
}
