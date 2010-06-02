package edu.mit.mel.locast.mobile.widget;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.widget.Button;
import edu.mit.mel.locast.mobile.AddressUtils;

public class LocationLink extends Button {
	private Location location;
	private static String LAT_LON_FORMAT = "%.4f, %.4f";

	public LocationLink(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	public LocationLink(Context context, AttributeSet attrs){
		this(context, attrs, 0);
	}
	
	public LocationLink(Context context){
		this(context, null);
	}
	
	public void setLocation(Location location){
		this.location = location;
		setText(String.format(LAT_LON_FORMAT, location.getLatitude(), location.getLongitude()));
		final GeocoderTask t = new GeocoderTask();
		t.execute(location);
	}
	
	public void setLocation(double latitude, double longitude){
		final Location l = new Location("internal");
		l.setLatitude(latitude);
		l.setLongitude(longitude);
		setLocation(l);
	}
	public Location getLocation(){
		return location;
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
				setText(result);
			}
		}
	}

}
