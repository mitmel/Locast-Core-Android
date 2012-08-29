package edu.mit.mobile.android.location;

/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mobile.android.locast.ver2.R;

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
        lm = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);
        mContext = context;
    }

    /**
     * Registers a locationListener, which will immediately get the last known
     * location (if there is one). When location updates are no longer needed or
     * when the activity is paused, please
     * {@link #removeLocationUpdates(LocationListener)}.
     *
     * This can safely be called multiple times for the same listener.
     *
     * @param locationListener
     */
    public void requestLocationUpdates(LocationListener locationListener) {
        // stop any existing location listeners or back out if we're already registered
        if (mLocationListener != null){
            if (mLocationListener != locationListener){
                removeLocationUpdates(mLocationListener);
            }else{
                // we're already registered.
                return;
            }
        }

        mLocationListener = locationListener;
        final String roughProvider = lm.getBestProvider(initialCriteria, true);

        if (roughProvider == null) {
            Toast.makeText(mContext,
                    mContext.getString(R.string.error_no_providers),
                    Toast.LENGTH_LONG).show();
            return;
        }

        final Location loc = lm.getLastKnownLocation(roughProvider);
        if (loc != null) {
            mLocationListener.onLocationChanged(loc);
        }
        requestLocationUpdates(roughProvider);

        if (currentProvider != null) {
            requestLocationUpdates(currentProvider);
        } else {
            Toast.makeText(mContext, R.string.error_no_providers,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestLocationUpdates(String provider) {
        if (currentProvider != null) {
            lm.removeUpdates(this);
        }
        currentProvider = provider;
        lm.requestLocationUpdates(provider, 5000, 100, this);
        lm.requestLocationUpdates(lm.getBestProvider(sustainedCriteria, true),
                1000, 100, this);
    }

    public boolean isListenerRegistered(LocationListener locListener){
        return mLocationListener != null && mLocationListener == locListener;
    }

    /**
     * Unregisters the location listener and stops location updates from coming in.
     *
     * @param locListener
     */
    public void removeLocationUpdates(LocationListener locListener) {
        lm.removeUpdates(this);
        mLocationListener = null;
    }

    public void onLocationChanged(Location location) {
        if (mLocationListener != null) {
            if (mLocationListener == this) {
                Log.e(TAG,
                        "passed IncremetalLocator itself for a location listener");
                return;
            }
            mLocationListener.onLocationChanged(location);
        }
        if (!gotLocation) {
            final String accurateProvider = lm.getBestProvider(
                    sustainedCriteria, true);
            requestLocationUpdates(accurateProvider);
            gotLocation = true;
        }
    }

    public void onProviderDisabled(String provider) {
        if (mLocationListener != null) {
            mLocationListener.onProviderDisabled(provider);
        }
    }

    public void onProviderEnabled(String provider) {
        if (mLocationListener != null) {
            mLocationListener.onProviderEnabled(provider);
        }
    }

    public Location getLastKnownLocation() {
        if (currentProvider == null){
            return null;
        }
        return lm.getLastKnownLocation(currentProvider);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (mLocationListener != null) {
            mLocationListener.onStatusChanged(provider, status, extras);
        }
    }
}
