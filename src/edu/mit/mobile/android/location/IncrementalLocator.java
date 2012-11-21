package edu.mit.mobile.android.location;

/*
 * Copyright (C) 2011-2012  MIT Mobile Experience Lab
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
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;
import edu.mit.mobile.android.locast.R;

/**
 * A wrapper for a LocationManager that incrementally gets location for you, starting with a coarse
 * locator then moving to a more accurate one.
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public class IncrementalLocator {
    private final static String TAG = IncrementalLocator.class.getSimpleName();

    private final LocationManager lm;

    static final Criteria initialCriteria = new Criteria();
    static final Criteria sustainedCriteria = new Criteria();

    private boolean gotLocation = false;

    private String currentProvider;

    private LocationListener mWrappedLocationListener;

    private final Context mContext;

    public static final int PROVIDER_TYPE_LAST_KNOWN = 1;
    public static final int PROVIDER_TYPE_COARSE = 2;
    public static final int PROVIDER_TYPE_FINE = 3;

    private int mProviderType;

    public static long NO_MAX_AGE = 0;

    private long mMaxAge = NO_MAX_AGE;

    static {

        initialCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sustainedCriteria.setAccuracy(Criteria.ACCURACY_FINE);
    }

    public IncrementalLocator(Context context) {
        lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mContext = context;
    }

    /**
     * Sets the maximum age a location fix can be in order to be delivered to the location listener.
     * Set to {@link #NO_MAX_AGE} to disable (this is the default).
     *
     * @param milliseconds
     */
    public void setMaximumAge(long milliseconds) {
        mMaxAge = milliseconds;
    }

    /**
     * Registers a locationListener. This will immediately get the last known location if there is
     * one that satisfies {@link #setMaximumAge(long)}. When location updates are no longer needed
     * or when the activity is paused, please {@link #removeLocationUpdates(LocationListener)}.
     *
     * This can safely be called multiple times for the same listener.
     *
     * @param locationListener
     */
    public void requestLocationUpdates(LocationListener locationListener) {
        // stop any existing location listeners or back out if we're already registered
        if (mWrappedLocationListener != null) {
            if (mWrappedLocationListener != locationListener) {
                removeLocationUpdates(mWrappedLocationListener);
            } else {
                // we're already registered.
                return;
            }
        }

        mWrappedLocationListener = locationListener;
        final String roughProvider = lm.getBestProvider(initialCriteria, true);

        if (roughProvider == null) {
            Toast.makeText(mContext, mContext.getString(R.string.error_no_providers),
                    Toast.LENGTH_LONG).show();
            return;
        }

        mProviderType = PROVIDER_TYPE_LAST_KNOWN;
        final Location loc = lm.getLastKnownLocation(roughProvider);

        notifyWrappedListener(loc);

        mProviderType = PROVIDER_TYPE_COARSE;

        requestLocationUpdates(roughProvider);

        if (currentProvider != null) {
            requestLocationUpdates(currentProvider);
        } else {
            Toast.makeText(mContext, R.string.error_no_providers, Toast.LENGTH_LONG).show();
        }
    }

    private void requestLocationUpdates(String provider) {
        if (currentProvider != null) {
            lm.removeUpdates(mLocationListener);
        }
        currentProvider = provider;
        lm.requestLocationUpdates(provider, 5000, 100, mLocationListener);
        mProviderType = PROVIDER_TYPE_FINE;
        lm.requestLocationUpdates(lm.getBestProvider(sustainedCriteria, true), 1000, 100,
                mLocationListener);
    }

    /**
     * Sends a location update to the wrapped listener if it meets the needed criteria.
     *
     * @param loc
     * @see #setMaximumAge(long)
     */
    private void notifyWrappedListener(Location loc) {
        if (mWrappedLocationListener != null && loc != null
                && (mMaxAge == NO_MAX_AGE || getLocationAge(loc) <= mMaxAge)) {
            mWrappedLocationListener.onLocationChanged(loc);
            if (mWrappedLocationListener instanceof OnIncrementalLocationListener) {
                ((OnIncrementalLocationListener) mWrappedLocationListener)
                        .onIncrementalLocationChanged(mProviderType, loc);
            }
        }
    }

    /**
     * Gets the age of a location. This may be inaccurate on systems earlier than Android r17, as
     * there was no method for getting the temporal age of a location before then (only a timestamp,
     * which will change validity if the clock changes).
     *
     * @param loc
     * @return age in ms
     */
    private long getLocationAge(Location loc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return (SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / 1000000;
        } else {
            // this isn't recommended, but it's all there is.
            return System.currentTimeMillis() - loc.getTime();
        }
    }

    public boolean isListenerRegistered(LocationListener locListener) {
        return mWrappedLocationListener != null && mWrappedLocationListener == locListener;
    }

    /**
     * Unregisters the location listener and stops location updates from coming in.
     *
     * @param locListener
     */
    public void removeLocationUpdates(LocationListener locListener) {
        lm.removeUpdates(mLocationListener);
        mWrappedLocationListener = null;
    }

    private final LocationListener mLocationListener = new LocationListener() {

        public void onLocationChanged(Location location) {
            notifyWrappedListener(location);

            if (!gotLocation) {
                mProviderType = PROVIDER_TYPE_FINE;
                final String accurateProvider = lm.getBestProvider(sustainedCriteria, true);
                requestLocationUpdates(accurateProvider);
                gotLocation = true;
            }
        }

        public void onProviderDisabled(String provider) {
            if (mWrappedLocationListener != null) {
                mWrappedLocationListener.onProviderDisabled(provider);
            }
        }

        public void onProviderEnabled(String provider) {
            if (mWrappedLocationListener != null) {
                mWrappedLocationListener.onProviderEnabled(provider);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (mWrappedLocationListener != null) {
                mWrappedLocationListener.onStatusChanged(provider, status, extras);
            }
        }
    };

    public Location getLastKnownLocation() {
        if (currentProvider == null) {
            return null;
        }
        return lm.getLastKnownLocation(currentProvider);
    }

    public static interface OnIncrementalLocationListener extends LocationListener {
        public void onIncrementalLocationChanged(int providerType, Location location);
    }
}
