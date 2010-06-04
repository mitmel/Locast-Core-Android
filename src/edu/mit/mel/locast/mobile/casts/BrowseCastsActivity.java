package edu.mit.mel.locast.mobile.casts;
/*
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
import org.jsharkey.blog.android.SeparatedListAdapter;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;

public class BrowseCastsActivity extends CastListActivity implements LocationListener {

	private LocationManager lm;
	private CastCursorAdapter nearbyCursorAdapter;
	private boolean gotLocation = false;
	
	static final Criteria initialCriteria = new Criteria();
	static final Criteria accurateCriteria = new Criteria();
	
	private String currentProvider;
	
	static {
		
		initialCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
		
		accurateCriteria.setAccuracy(Criteria.ACCURACY_FINE);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final SeparatedListAdapter adapter = new SeparatedListAdapter(this, R.layout.list_section_header);
		
		adapter.addSection("featured", new CastCursorAdapter(this, managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, Cast._ID + "=1", null, null)));
		
		nearbyCursorAdapter = new CastCursorAdapter(this, managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, Cast._ID + "=-1", null, null)); 
		
		adapter.addSection("nearby", nearbyCursorAdapter);
		
		adapter.addSection("starred", new CastCursorAdapter(this, managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, null, null, null)));
		
		lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		


		final String roughProvider = lm.getBestProvider(initialCriteria, true);
		final Location loc = lm.getLastKnownLocation(roughProvider);
		if (loc != null){
			updateNearbyLocation(loc);
		}
		requestLocationUpdates(roughProvider);
		getListView().setFastScrollEnabled(true);
		setListAdapter(adapter);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		lm.removeUpdates(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		requestLocationUpdates();
	}
	
	private void requestLocationUpdates(){
		if (currentProvider != null){
			requestLocationUpdates(currentProvider);
		}
	}
	
	private void requestLocationUpdates(String provider){
		if (currentProvider != null){
			lm.removeUpdates(this);
		}
		currentProvider = provider;
		lm.requestLocationUpdates(provider, 60000, 100, this);
	}
	
	private void updateNearbyLocation(Location location){
		final String[] nearLoc = {String.valueOf(location.getLatitude()), 
				String.valueOf(location.getLongitude())};

		nearbyCursorAdapter.changeCursor(managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, Cast.SELECTION_LAT_LON, nearLoc, null));
	}

	public void onLocationChanged(Location location) {
		if (!gotLocation){
			final String accurateProvider = lm.getBestProvider(accurateCriteria, true);
			requestLocationUpdates(accurateProvider);
			gotLocation = true;
		}
		
		updateNearbyLocation(location);
	}

	public void onProviderDisabled(String provider) {}

	public void onProviderEnabled(String provider) {}

	public void onStatusChanged(String provider, int status, Bundle extras) {}
}
