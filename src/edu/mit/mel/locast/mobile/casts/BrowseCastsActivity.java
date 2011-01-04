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
import java.util.ArrayList;

import org.jsharkey.blog.android.SeparatedListAdapter;

import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import edu.mit.mel.locast.mobile.IncrementalLocator;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Locatable;
import edu.mit.mel.locast.mobile.data.TaggableItem;

/**
 * @author steve
 *
 */
public class BrowseCastsActivity extends CastListActivity implements LocationListener, OnClickListener {

	private IncrementalLocator iloc;
	private CastCursorAdapter nearbyCursorAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final SeparatedListAdapter adapter = new SeparatedListAdapter(this, R.layout.list_section_header);
		final ArrayList<String> tag = new ArrayList<String>();
		tag.add(TaggableItem.addPrefixToTag(TaggableItem.SYSTEM_PREFIX, "_featured"));

		adapter.addSection(getString(R.string.section_drafts), new CastCursorAdapter(this, managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, Cast._PUBLIC_URI + "=null OR "+Cast._DRAFT, null, null)));
		adapter.addSection(getString(R.string.section_featured), new CastCursorAdapter(this, managedQuery(TaggableItem.getTagUri(Cast.CONTENT_URI, tag), TaggableItem.getTagProjection(Cast.PROJECTION), null, null, Cast.SORT_ORDER_DEFAULT)));

		nearbyCursorAdapter = new CastCursorAdapter(this, managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, Cast._ID + "=-1", null, null));

		adapter.addSection(getString(R.string.section_nearby), nearbyCursorAdapter);

		adapter.addSection(getString(R.string.section_starred), new CastCursorAdapter(this, managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, Cast._FAVORITED + " != 0", null, null)));
		adapter.addSection(getString(R.string.section_all), new CastCursorAdapter(this, managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, null, null, null)));
		iloc = new IncrementalLocator(this);

		getListView().setFastScrollEnabled(true);
		setListAdapter(adapter);

		final View v = findViewById(R.id.new_cast);
		if (v != null){
			v.setOnClickListener(this);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		iloc.removeLocationUpdates(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		iloc.requestLocationUpdates(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.cast_list, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
		case R.id.refresh:

			startService(new Intent(Intent.ACTION_SYNC, getIntent().getData()));
			return true;

		case R.id.moment:
			startActivity(new Intent(Intent.ACTION_INSERT, Cast.CONTENT_URI, this, Moment.class));
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void updateNearbyLocation(Location location){
		final String[] nearLoc = {String.valueOf(location.getLatitude()),
				String.valueOf(location.getLongitude())};

		nearbyCursorAdapter.changeCursor(managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, Locatable.SELECTION_LAT_LON, nearLoc, null));
	}

	public void onLocationChanged(Location location) {
		updateNearbyLocation(location);
	}

	public void onProviderDisabled(String provider) {}

	public void onProviderEnabled(String provider) {}

	public void onStatusChanged(String provider, int status, Bundle extras) {}

	public void onClick(View v) {
		startActivity(new Intent(Intent.ACTION_INSERT, Cast.CONTENT_URI));

	}
}
