package edu.mit.mel.locast.mobile.widget;

/*
 * LocationLink.java
 * Copyright (C) 2010 MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.Button;
import edu.mit.mel.locast.mobile.AddressUtils;
import edu.mit.mel.locast.mobile.R;

/**
 * A clickable link that shows a reverse-geocoded location.
 *
 * @author steve
 *
 */
public class LocationLink extends Button {
	public static final String TAG = LocationLink.class.getSimpleName();

	private Location location;
	private static String LAT_LON_FORMAT = "%.4f, %.4f";
	private String geocodedName;
	private final int noLocationResId = R.string.location_link_no_location;
	private boolean showAccuracy = true;

	final ConnectivityManager cm;

	public LocationLink(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setText(noLocationResId);
		cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	public LocationLink(Context context, AttributeSet attrs){
		this(context, attrs, 0);
	}

	public LocationLink(Context context){
		this(context, null);
	}

	/**
	 * Sets the location that is displayed on the link. Will display accuracy information if present
	 * and if showAccuracy is set.
	 *
	 * @param location
	 */
	public void setLocation(Location location){
		// only update if it's a distinctly new location.
		if (this.location == null ||
				location == null ||
				this.location.distanceTo(location) != 0){

			this.location = location;
			geocodedName = null; // invalidated

			updateLabel();
		}
	}

	/**
	 * Sets the location that is displayed on the link. Will display accuracy information if present
	 * and if showAccuracy is set.
	 *
	 * @param latitude
	 * @param longitude
	 */
	public void setLocation(double latitude, double longitude){
		final Location l = new Location("internal");
		l.setLatitude(latitude);
		l.setLongitude(longitude);
		setLocation(l);
	}

	public Location getLocation(){
		return location;
	}

	/**
	 * Shows the accuracy of the location.
	 *
	 * @param showAccuracy
	 */
	public void setShowAccuracy(boolean showAccuracy){
		this.showAccuracy = showAccuracy;
	}

	public boolean getShowAccuracy(){
		return showAccuracy;
	}

	private void setGeocodedName(String placename){
		geocodedName = placename;
		updateLabel();
	}

	private void updateLabel(){
		if (location == null){
			setText(noLocationResId);
		}else{
			String accuracy = "";
			if (showAccuracy && location.hasAccuracy()){
				final float accuracyVal = location.getAccuracy();
				if (accuracyVal <= 500){
					accuracy = " " + getResources().getString(R.string.location_link_accuracy_meter, accuracyVal);
				}else{
					accuracy = " " + getResources().getString(R.string.location_link_accuracy_kilometer, accuracyVal / 1000.0);
				}
			}

			String placename;

			if (geocodedName == null){
				placename = String.format(LAT_LON_FORMAT, location.getLatitude(), location.getLongitude());
		    	final NetworkInfo activeNet = cm.getActiveNetworkInfo();
		    	final boolean hasNetConnection = activeNet != null && activeNet.isConnected();
		    	if (hasNetConnection){
					final GeocoderTask t = new GeocoderTask();
					t.execute(location);
		    	}
			}else{
				placename = geocodedName;
			}

			setText(placename + accuracy);
		}
	}


	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (!state.getClass().equals(SavedState.class)){
			super.onRestoreInstanceState(state);
			return;
		}

		final SavedState ss = (SavedState)state;
		super.onRestoreInstanceState(ss.getSuperState());

		this.location = ss.location;
		this.geocodedName = ss.geocodedName;
		updateLabel();

	}

	@Override
	public Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();

		final SavedState ss = new SavedState(superState);
		ss.geocodedName = geocodedName;
		ss.location = location;
		return ss;
	}

	/**
	 * This will preserve the looked-up placename, preventing unnecessary geocoder lookups.
	 * @author stevep
	 *
	 */
	private class SavedState extends BaseSavedState {
		String geocodedName;
		Location location;

		public SavedState(Parcel in) {
			super(in);

			geocodedName = in.readString();
			location = Location.CREATOR.createFromParcel(in);
		}

		public SavedState(Parcelable superState){
			super(superState);
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);

			dest.writeString(geocodedName);
			dest.writeParcelable(location, flags);
		}
	}

	private class GeocoderTask extends AsyncTask<Location, Long, String> {
		@Override
		protected String doInBackground(Location... params) {
			final Location location = params[0];
			final Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
			try {
				final List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
				if (addresses.size() > 0){
					final Address thisLocation = addresses.get(0);

					return AddressUtils.addressToName(thisLocation);
				}
			} catch (final IOException e) {
				e.printStackTrace();

			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null){
				setGeocodedName(result);
			}
		}
	}
}
