package edu.mit.mobile.android.locast.ver2.events;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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

import android.content.Intent;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;
import edu.mit.mobile.android.locast.data.Event;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.browser.BrowserHome;
import edu.mit.mobile.android.locast.ver2.casts.LocatableDetail;
import edu.mit.mobile.android.locast.ver2.itineraries.BasicLocatableOverlay;
import edu.mit.mobile.android.locast.ver2.itineraries.LocatableItemOverlay;

public class EventDetail extends LocatableDetail implements LoaderManager.LoaderCallbacks<Cursor>, OnClickListener {
	private static final String TAG = EventDetail.class.getSimpleName();

	private static final int LOADER_EVENT = 0;

	private Uri mEvent;
	private MapController mMapController;
	private BasicLocatableOverlay mEventOverlay;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.event_detail);

		findViewById(R.id.refresh).setOnClickListener(this);

		initOverlays();

		mEvent = getIntent().getData();
		getSupportLoaderManager().initLoader(LOADER_EVENT, null, this);
		mMapController = ((MapView)findViewById(R.id.map)).getController();

	}

	@Override
	protected LocatableItemOverlay createItemOverlay() {
		mEventOverlay = new BasicLocatableOverlay(
				BasicLocatableOverlay.boundCenterBottom(
						getResources().getDrawable(R.drawable.ic_map_event)), new DefaultResourceProxyImpl(this));
		return mEventOverlay;

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.refresh:
			// TODO add handler
			break;

		case R.id.home:
			startActivity(new Intent(this, BrowserHome.class));
			break;
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		switch (id){
		case LOADER_EVENT:
			return new CursorLoader(this, mEvent, Event.PROJECTION, null, null, null);

			default:
				return null;
		}
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		switch (loader.getId()){
		case LOADER_EVENT:
			loadFromCursor(data);
			break;
		}
	}

	private void loadFromCursor(Cursor c){
		if (c.moveToFirst()){
			((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Event._TITLE)));
			((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Event._DESCRIPTION)));
			((TextView)findViewById(R.id.author)).setText(c.getString(c.getColumnIndex(Event._AUTHOR)));
			final long
				start = c.getLong(c.getColumnIndex(Event._START_DATE)),
				end = c.getLong(c.getColumnIndex(Event._END_DATE));

			((TextView)findViewById(R.id.author)).setText(
					DateUtils.formatDateRange(this, start, end,
							DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_WEEKDAY));

			setPointerFromCursor(c, mMapController);

			mEventOverlay.swapCursor(c);
		}else{
			Toast.makeText(this, R.string.error_loading_event, Toast.LENGTH_LONG).show();
			Log.e(TAG, "cursor has no content");
			finish();
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mEventOverlay.swapCursor(null);
	}

}
