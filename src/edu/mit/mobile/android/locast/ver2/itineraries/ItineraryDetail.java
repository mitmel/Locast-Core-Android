package edu.mit.mobile.android.locast.ver2.itineraries;
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

import java.util.List;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.maps.CastsOverlay;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.browser.BrowserHome;

public class ItineraryDetail extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, OnClickListener {
	private static final String TAG = ItineraryDetail.class.getSimpleName();

	// if the layout for this doesn't need a map, set this to false.
	private static final boolean USE_MAP = true;

	private MapView mMapView;
	private MapController mMapController;

	private ListView mCastView;
	private CastCursorAdapter mCastAdapter;

	private ImageCache mImageCache;

	private Uri mUri;
	private Uri mCastsUri;

	private CastsOverlay mCastsOverlay;
	private PathOverlay mPathOverlay;

	CursorLoader itinLoader;
	CursorLoader castLoader;



	@Override
	protected void onCreate(Bundle icicle) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		super.onCreate(icicle);
		setContentView(R.layout.itinerary_detail);

		mImageCache = ImageCache.getInstance(this);

		mCastView = (ListView)findViewById(R.id.casts);
		findViewById(R.id.refresh).setOnClickListener(this);
		findViewById(R.id.home).setOnClickListener(this);

		final LayoutInflater layoutInflater = getLayoutInflater();

		mCastView.addHeaderView(layoutInflater.inflate(R.layout.itinerary_detail_list_header, mCastView, false), null, false);
		mCastView.addFooterView(layoutInflater.inflate(R.layout.list_footer, null), null, false);
		mCastView.setEmptyView(layoutInflater.inflate(R.layout.itinerary_detail_list_empty, mCastView, false));
		mCastView.setEmptyView(findViewById(R.id.empty_message));


		findViewById(R.id.add_cast).setOnClickListener(this);
		mCastView.setOnItemClickListener(this);

		mCastView.setAdapter(null);
		registerForContextMenu(mCastView);

		if (USE_MAP){
			mMapView = (MapView)findViewById(R.id.map);
			mMapController = mMapView.getController();
		}

		final Intent intent = getIntent();
		final String action = intent.getAction();

		if (Intent.ACTION_VIEW.equals(action)){
			mUri = intent.getData();

			mCastsUri = Itinerary.getCastsUri(mUri);

			final LoaderManager lm = getSupportLoaderManager();
			Bundle args = new Bundle();
			args.putParcelable(LOADER_ARG_DATA, mUri);
			lm.initLoader(LOADER_ITINERARY, args, this);

			args = new Bundle();
			args.putParcelable(LOADER_ARG_DATA, mCastsUri);
			lm.initLoader(LOADER_CASTS, args, this);

			initCastList();

		}else{
			finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh(false);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
       } catch (final ClassCastException e) {
           Log.e(TAG, "bad menuInfo", e);
           return;
       }

       // XXX the "- 1" below is due to having a header. I'm not sure where this is supposed to be handled.
       final Cursor c = (Cursor) mCastAdapter.getItem(info.position - 1);
       if (c == null){
    	   return;
       }

       // load the base menus.
		final MenuInflater menuInflater = getMenuInflater();
	    menuInflater.inflate(R.menu.cast_context, menu);
	    menuInflater.inflate(R.menu.cast_options, menu);

       menu.setHeaderTitle(c.getString(c.getColumnIndex(Cast._TITLE)));

       final boolean canEdit = Cast.canEdit(this, c);
       menu.findItem(R.id.cast_edit).setVisible(canEdit);
       menu.findItem(R.id.cast_delete).setVisible(canEdit);

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
       } catch (final ClassCastException e) {
           Log.e(TAG, "bad menuInfo", e);
           return false;
       }

       final Uri cast = Cast.getCanonicalUri(this, ContentUris.withAppendedId(mCastsUri, info.id));

       switch (item.getItemId()){
       case R.id.cast_view:
    	   startActivity(new Intent(Intent.ACTION_VIEW, cast));
    	   return true;

       case R.id.cast_edit:
    	   startActivity(new Intent(Intent.ACTION_EDIT, cast));
    	   return true;

       case R.id.cast_delete:
    	   startActivity(new Intent(Intent.ACTION_DELETE, cast));
    	   return true;

//       case R.id.cast_play:
//    	   startActivity(new Intent(CastDetailsActivity.ACTION_PLAY_CAST, cast));
//    	   return true;

       default:
    	   return super.onContextItemSelected(item);
       }
	}

	private void initCastList(){
		mCastAdapter = new CastCursorAdapter(ItineraryDetail.this, null);

		mCastView.setAdapter(new ImageLoaderAdapter(this, mCastAdapter, mImageCache, new int[]{R.id.media_thumbnail}, 48, 48, ImageLoaderAdapter.UNIT_DIP ));

		if (USE_MAP){
			mCastsOverlay = new CastsOverlay(ItineraryDetail.this);
			final List<Overlay> overlays = mMapView.getOverlays();
			mPathOverlay = new PathOverlay(this);
			overlays.add(mPathOverlay);
			overlays.add(mCastsOverlay);
		}
	}

	private void refresh(boolean explicitSync){
		startService(new Intent(Intent.ACTION_SYNC, mUri).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
		startService(new Intent(Intent.ACTION_SYNC, mCastsUri).putExtra(Sync.EXTRA_EXPLICIT_SYNC, explicitSync));
	}


	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {

		final Cursor cast = (Cursor) adapter.getItemAtPosition(position);
		final int dratCol = cast.getColumnIndex(Cast._DRAFT);
		final boolean isDraft = ! cast.isNull(dratCol) && cast.getInt(dratCol) == 1;

		if (isDraft){
			startActivity(new Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(mCastsUri, id)));
		}else{
			startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mCastsUri, id)));
		}
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.refresh:
			refresh(true);
			break;

		case R.id.home:
			startActivity(new Intent(this, BrowserHome.class));
            break;

		case R.id.add_cast:
			startActivity(new Intent(Intent.ACTION_INSERT, Itinerary.getCastsUri(getIntent().getData())));
			break;
		}
	}

	private static final int
		LOADER_ITINERARY = 0,
		LOADER_CASTS = 1;
	private static final String
		LOADER_ARG_DATA = "edu.mit.mobile.android.locast.LOADER_ARG_DATA";

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri uri = args.getParcelable(LOADER_ARG_DATA);

		CursorLoader cl = null;

		switch (id){
		case LOADER_ITINERARY:
			cl = new CursorLoader(this, uri, Itinerary.PROJECTION, null, null, null);
			break;

		case LOADER_CASTS:
			cl = new CursorLoader(this, uri, Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);
			break;

		}

		cl.setUpdateThrottle(Constants.UPDATE_THROTTLE);

		return cl;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		switch (loader.getId()){
		case LOADER_ITINERARY:{
			if (c.moveToFirst()){
				((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Itinerary._DESCRIPTION)));
				((TextView)findViewById(R.id.title)).setText(c.getString(c.getColumnIndex(Itinerary._TITLE)));

				if (USE_MAP){
					final List<GeoPoint> path = Itinerary.getPath(c);
					mPathOverlay.setPath(path);

					mMapController.zoomToSpan(mPathOverlay.getLatSpanE6(), mPathOverlay.getLonSpanE6());
					mMapController.setCenter(mPathOverlay.getCenter());
					mMapView.setVisibility(View.VISIBLE);
				}
			}else{
				Log.e(TAG, "error loading itinerary");
			}

		}break;

		case LOADER_CASTS:{
			mCastAdapter.swapCursor(c);
			if (USE_MAP){
				mCastsOverlay.swapCursor(c);
			}
		}break;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		switch (loader.getId()){
		case LOADER_CASTS:
			mCastAdapter.swapCursor(null);
			if (USE_MAP){
				mCastsOverlay.swapCursor(null);
			}
			break;

		case LOADER_ITINERARY:

			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.itinerary_detail, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()){
		case R.id.add_cast:
			startActivity(new Intent(Intent.ACTION_INSERT, Itinerary.getCastsUri(getIntent().getData())));
			return true;

		case R.id.refresh:
			refresh(true);
			return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
