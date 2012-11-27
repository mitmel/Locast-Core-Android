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
 * locator then moving to a more accurate one. This also incorporates the recommended
 * currentBestLocation system described in
 * http://developer.android.com/intl/de/guide/topics/location/strategies.html and will only return
 * locations that are the current best location.
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public class IncrementalLocator {
    private final static String TAG = IncrementalLocator.class.getSimpleName();

    private final LocationManager lm;

    static final Criteria sustainedCriteria = new Criteria();

    private LocationListener mWrappedLocationListener;

    private final Context mContext;

    public static final int PROVIDER_TYPE_LAST_KNOWN = 1;
    public static final int PROVIDER_TYPE_SENSED = 2;

    private static final long MIN_UPDATE_TIME = 5000; // ms

    private static final float MIN_UPDATE_DISTANCE = 100; // m

    private int mProviderType;

    public static long NO_MAX_AGE = 0;

    private long mMaxAge = NO_MAX_AGE;

    static {
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
        final String roughProvider = LocationManager.NETWORK_PROVIDER;

        final String fineProvider = lm.getBestProvider(sustainedCriteria, true);

        if (!lm.isProviderEnabled(fineProvider) || !lm.isProviderEnabled(roughProvider)) {
            Toast.makeText(mContext, R.string.error_no_providers, Toast.LENGTH_LONG).show();
            return;
        }

        mProviderType = PROVIDER_TYPE_LAST_KNOWN;
        final Location loc = lm.getLastKnownLocation(roughProvider);

        notifyWrappedListener(loc);

        lm.requestLocationUpdates(roughProvider, MIN_UPDATE_TIME, MIN_UPDATE_DISTANCE,
                mLocationListener);

        mProviderType = PROVIDER_TYPE_SENSED;

        lm.requestLocationUpdates(fineProvider, MIN_UPDATE_TIME, MIN_UPDATE_DISTANCE,
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

    /**
     * Gets the time of the fix. This will be either in time since 1970 or time since boot,
     * depending on Android version. It's mostly useful for comparing times.
     *
     * @param loc
     * @return fix time, in ms
     */
    private long getLocationTime(Location loc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return loc.getElapsedRealtimeNanos() / 1000000;
        } else {
            // this isn't recommended, but it's all there is.
            return loc.getTime();
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

    private Location mCurrentBestLocation;

    private final LocationListener mLocationListener = new LocationListener() {

        public void onLocationChanged(Location location) {
            if (mCurrentBestLocation == null || isBetterLocation(location, mCurrentBestLocation)) {
                notifyWrappedListener(location);
                mCurrentBestLocation = location;
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

    // from http://developer.android.com/intl/de/guide/topics/location/strategies.html

    private static final int TWO_MINUTES = 1000 * 60 * 2;

    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location
     *            The new Location that you want to evaluate
     * @param currentBestLocation
     *            The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        final long timeDelta = getLocationTime(location) - getLocationTime(currentBestLocation);
        final boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        final boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        final boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        final int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        final boolean isLessAccurate = accuracyDelta > 0;
        final boolean isMoreAccurate = accuracyDelta < 0;
        final boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        final boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    // end of section from developer.android.com

    /**
     * Checks all providers to find the best last known location.
     *
     * @return the best last known location
     */
    public Location getLastKnownLocation() {
        Location best = null;
        Location loc;

        for (final String provider : lm.getAllProviders()) {

            loc = lm.getLastKnownLocation(provider);
            if (loc == null) {
                continue;
            }

            best = (best == null || isBetterLocation(loc, best)) ? loc : best;
        }

        return best;
    }

    public static interface OnIncrementalLocationListener extends LocationListener {
        public void onIncrementalLocationChanged(int providerType, Location location);
    }
}
