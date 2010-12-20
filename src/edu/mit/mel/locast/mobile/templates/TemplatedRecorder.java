package edu.mit.mel.locast.mobile.templates;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class TemplatedRecorder extends VideoRecorder  implements OnClickListener, LocationListener {
	@SuppressWarnings("unused")
	private static final String TAG = TemplateActivity.class.getSimpleName();
	public final static String ACTION_RECORD_TEMPLATED_VIDEO = "edu.mit.mobile.android.locast.ACTION_RECORD_TEMPLATED_VIDEO";

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLocationChanged(Location location) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}

}
