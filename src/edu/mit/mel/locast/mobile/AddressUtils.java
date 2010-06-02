package edu.mit.mel.locast.mobile;

import android.location.Address;
import android.util.Log;

public class AddressUtils {
	public final static String TAG = AddressUtils.class.getSimpleName();
	// this regular expression should match what would be considered a bad description of the address.
	private static String  BAD_DESCRIPTION_RE = "^[\\d\\s-]+$";

	public static String addressToName(Address address){
		
		final Address thisLocation = address;
		Log.d(TAG, "Location: " + thisLocation);
		String title = thisLocation.getFeatureName();
		
		if (title == null || title.matches(BAD_DESCRIPTION_RE)){
			title = thisLocation.getAddressLine(0);
		}
		
		if (title == null || title.matches(BAD_DESCRIPTION_RE)){
			title = thisLocation.getSubLocality();	
		}

		if (title == null){
			title = thisLocation.getLocality();
		}
		
		return title;
	}
}
