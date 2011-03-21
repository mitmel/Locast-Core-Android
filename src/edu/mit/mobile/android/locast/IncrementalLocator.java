package edu.mit.mobile.android.locast;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * A wrapper for a LocationManager that incrementally gets location for you,
 * starting with a coarse locator then moving to a more accurate one.
 *
 * @author steve
 *
 */
public class IncrementalLocator implements LocationListener {
	private final static String TAG = IncrementalLocator.class.getSimpleName();

	private final LocationManager lm;

	static final Criteria initialCriteria = new Criteria();
	static final Criteria sustainedCriteria = new Criteria();

	private boolean gotLocation = false;

	private String currentProvider;

	private LocationListener mLocationListener;
	private final Context mContext;

	static {

		initialCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
		sustainedCriteria.setAccuracy(Criteria.ACCURACY_FINE);
	}

	public IncrementalLocator(Context context) {
		lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		mContext = context;
	}

	public void requestLocationUpdates(LocationListener locationListener){
		mLocationListener = locationListener;
		final String roughProvider = lm.getBestProvider(initialCriteria, true);

        if (roughProvider == null){
            Toast.makeText(mContext,
                            mContext.getString(R.string.error_no_providers),
                            Toast.LENGTH_LONG).show();
            return;
        }

		final Location loc = lm.getLastKnownLocation(roughProvider);
		if (loc != null){
			mLocationListener.onLocationChanged(loc);
		}
		requestLocationUpdates(roughProvider);


		if (currentProvider != null){
			requestLocationUpdates(currentProvider);
		}else{
			Toast.makeText(mContext, R.string.error_no_providers, Toast.LENGTH_LONG).show();
		}
	}

	private void requestLocationUpdates(String provider){
		if (currentProvider != null){
			lm.removeUpdates(this);
		}
		currentProvider = provider;
		lm.requestLocationUpdates(provider, 5000, 100, this);
		lm.requestLocationUpdates(lm.getBestProvider(sustainedCriteria, true), 1000, 100, this);
	}

	public void removeLocationUpdates(LocationListener locListener){
		lm.removeUpdates(this);
		mLocationListener = null;
	}

	public void onLocationChanged(Location location) {
		if (mLocationListener != null){
			if (mLocationListener == this){
				Log.e(TAG, "passed IncremetalLocator itself for a location listener");
				return;
			}
			mLocationListener.onLocationChanged(location);
		}
		if (!gotLocation){
			final String accurateProvider = lm.getBestProvider(sustainedCriteria, true);
			requestLocationUpdates(accurateProvider);
			gotLocation = true;
		}
	}

	public void onProviderDisabled(String provider) {
		if (mLocationListener != null){
			mLocationListener.onProviderDisabled(provider);
		}
	}

	public void onProviderEnabled(String provider) {
		if (mLocationListener != null){
			mLocationListener.onProviderEnabled(provider);
		}
	}

	public Location getLastKnownLocation(){
		return lm.getLastKnownLocation(currentProvider);
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		if (mLocationListener != null){
			mLocationListener.onStatusChanged(provider, status, extras);
		}
	}
}
