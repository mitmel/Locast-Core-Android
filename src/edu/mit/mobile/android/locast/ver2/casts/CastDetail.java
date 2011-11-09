package edu.mit.mobile.android.locast.ver2.casts;
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

import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.Gallery;
import android.widget.TextView;
import android.widget.Toast;

import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.accounts.AuthenticatorActivity;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.maps.CastsOverlay;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.ver2.browser.BrowserHome;
import edu.mit.mobile.android.locast.ver2.itineraries.LocatableItemOverlay;
import edu.mit.mobile.android.locast.widget.FavoriteClickHandler;
import edu.mit.mobile.android.widget.ValidatingCheckBox;

public class CastDetail extends LocatableDetail implements
		LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener,
		OnClickListener {
	private LoaderManager mLoaderManager;
	private CastsOverlay mCastsOverlay;
	private MapController mMapController;
	private SimpleCursorAdapter mCastMedia;

	private ValidatingCheckBox vcb;

	private static final int
		LOADER_CAST = 0,
		LOADER_CAST_MEDIA = 1;

	private Uri mCastMediaUri;

	private static final int REQUEST_SIGNIN = 0;

	private static final String[] CAST_PROJECTION = ArrayUtils.concat(
			new String[] { Cast._ID, Cast._TITLE, Cast._AUTHOR,
					Cast._DESCRIPTION, Cast._FAVORITED },
			CastsOverlay.CASTS_OVERLAY_PROJECTION);

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_detail);

		initOverlays();

		final Uri data = getIntent().getData();

		mCastMediaUri = Cast.getCastMediaUri(data);

		mMapController = ((MapView) findViewById(R.id.map)).getController();
		mLoaderManager = getSupportLoaderManager();
		mLoaderManager.initLoader(LOADER_CAST, null, this);
		mLoaderManager.initLoader(LOADER_CAST_MEDIA, null, this);
		findViewById(R.id.home).setOnClickListener(this);
		findViewById(R.id.refresh).setOnClickListener(this);

		vcb = (ValidatingCheckBox) findViewById(R.id.favorite);

		vcb.setValidatedClickHandler(new MyFavoriteClickHandler(this,
				data));

		final Gallery castMediaView = (Gallery) findViewById(R.id.cast_media);

		mCastMedia = new CastMediaAdapter(this);

		castMediaView.setEmptyView(findViewById(android.R.id.empty));
		castMediaView.setAdapter(new ImageLoaderAdapter(this, mCastMedia,
				ImageCache.getInstance(this),
				new int[] { R.id.media_thumbnail }, 480, 360,
				ImageLoaderAdapter.UNIT_DIP));

		castMediaView.setOnItemClickListener(this);

		castMediaView.setEnabled(true);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.home:
			startActivity(new Intent(this, BrowserHome.class));
			break;

		case R.id.refresh:
			startService(new Intent(Intent.ACTION_SYNC, getIntent().getData()));
			break;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position,
			long id) {

		final Cursor c = (Cursor) adapter.getItemAtPosition(position);
		final String mediaString = c.getString(c
				.getColumnIndex(CastMedia._MEDIA_URL));
		final String locMediaString = c.getString(c
				.getColumnIndex(CastMedia._LOCAL_URI));
		String mimeType = null;

		Uri media;

		if (locMediaString != null) {
			media = Uri.parse(locMediaString);
			if ("file".equals(media.getScheme())) {
				mimeType = c.getString(c.getColumnIndex(CastMedia._MIME_TYPE));
			}

		} else if (mediaString != null) {
			media = Uri.parse(mediaString);
			mimeType = c.getString(c.getColumnIndex(CastMedia._MIME_TYPE));

			// we strip this because we don't really want to force them to go to the browser.
			if ("text/html".equals(mimeType)){
				mimeType = null;
			}
		} else {
			return;
		}

		final Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(media, mimeType);

		if (mimeType != null && mimeType.startsWith("video/")){
			startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mCastMediaUri, id)));
		}else{
			// setting the MIME type for URLs doesn't work.
			try {
				startActivity(i);
			}catch (final ActivityNotFoundException e){
				// try it again, but without a mime type.
				if (mimeType != null){
					i.setDataAndType(media, null);
				}
				try {
					startActivity(i);
				}catch (final ActivityNotFoundException e2){
					Toast.makeText(this, R.string.error_cast_media_no_activities, Toast.LENGTH_LONG).show();
				}
			}
		}

	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		final Uri data = getIntent().getData();
		CursorLoader cl = null;
		switch (id) {
		case LOADER_CAST:
			cl = new CursorLoader(this, data, CAST_PROJECTION, null, null,
					null);
			break;
		case LOADER_CAST_MEDIA:

			cl = new CursorLoader(this, mCastMediaUri,
					CastMediaAdapter.CAST_MEDIA_PROJECTION, null, null, null);
			break;
		}
		cl.setUpdateThrottle(Constants.UPDATE_THROTTLE);
		return cl;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		switch (loader.getId()) {
		case LOADER_CAST:
			mCastsOverlay.swapCursor(c);
			if (c.moveToFirst()) {
				// MediaProvider.dumpCursorToLog(c, Cast.PROJECTION);
				((TextView) findViewById(R.id.title)).setText(c.getString(c
						.getColumnIndex(Cast._TITLE)));
				((TextView) findViewById(R.id.author)).setText(c.getString(c
						.getColumnIndex(Cast._AUTHOR)));
				((TextView) findViewById(R.id.description)).setText(c
						.getString(c.getColumnIndex(Cast._DESCRIPTION)));
				((CheckBox) findViewById(R.id.favorite)).setChecked(c.getInt(c
						.getColumnIndex(Cast._FAVORITED)) != 0);

				setPointerFromCursor(c, mMapController);
			}

			break;

		case LOADER_CAST_MEDIA:
			mCastMedia.swapCursor(c);
			/*
			 * for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
			 * MediaProvider.dumpCursorToLog(c, CastMedia.PROJECTION); }
			 */
			break;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		switch (loader.getId()) {
		case LOADER_CAST:
			mCastsOverlay.swapCursor(null);
			break;

		case LOADER_CAST_MEDIA:
			mCastMedia.swapCursor(null);
			break;
		}
	}

	@Override
	protected LocatableItemOverlay createItemOverlay() {
		mCastsOverlay = new CastsOverlay(this);
		return mCastsOverlay;
	}

	private class MyFavoriteClickHandler extends FavoriteClickHandler {
		private boolean shouldActuallyDoIt = true;

		public MyFavoriteClickHandler(Context context, Uri favoritableItem) {
			super(context, favoritableItem);
		}

		@Override
		public Boolean performClick(ValidatingCheckBox checkBox) {
			if (shouldActuallyDoIt) {
				return super.performClick(checkBox);
			} else {
				return null;
			}
		}

		@Override
		public void prePerformClick(final ValidatingCheckBox checkBox) {
			if (!Authenticator.hasAccount(CastDetail.this)) {
				startActivityForResult(new Intent(CastDetail.this,
						AuthenticatorActivity.class), REQUEST_SIGNIN);
				shouldActuallyDoIt = false;
				mDoAfterAuthentication = new Runnable() {

					@Override
					public void run() {
						shouldActuallyDoIt = true;
						performClick(checkBox);
					}
				};
			}
		}
	}

	private Runnable mDoAfterAuthentication;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_SIGNIN:
			if (resultCode == RESULT_OK) {
				runOnUiThread(mDoAfterAuthentication);
			}
			mDoAfterAuthentication = null;
			break;
		}
	}
}
