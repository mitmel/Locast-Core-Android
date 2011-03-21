package edu.mit.mobile.android.locast.casts;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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


import android.app.Dialog;
import android.app.TabActivity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.WebImageLoader;
import edu.mit.mobile.android.locast.casts.BasicCursorContentObserver.BasicCursorContentObserverWatcher;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Comment;
import edu.mit.mobile.android.locast.widget.FavoriteClickHandler;

public class ViewCastActivity extends TabActivity implements BasicCursorContentObserverWatcher {
	public static final String TAG = ViewCastActivity.class.getSimpleName();

	private WebImageLoader imgLoader;

	private Uri myUri;
	private Cursor mCursor;

	private final BasicCursorContentObserver mContentObserver = new BasicCursorContentObserver(this);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.window_title_thick_tabhost);

		final String action = getIntent().getAction();

		imgLoader = ((Application)getApplication()).getImageLoader();

		if (Intent.ACTION_VIEW.equals(action)){
			myUri = getIntent().getData();

		}else if (Intent.ACTION_DELETE.equals(action)){
			myUri = getIntent().getData();
			showDialog(DIALOG_DELETE);
		}
		mCursor = managedQuery(myUri, Cast.PROJECTION, null, null, null);
		mCursor.moveToFirst();

		loadFromCursor();
	}

	@Override
	protected void onResume() {
		super.onResume();

		mCursor.registerContentObserver(mContentObserver);

		if (mCursor.moveToFirst()){
			loadFromCursor();
			startService(new Intent(Intent.ACTION_SYNC, myUri));
		}else{
			// handle the case where this item is deleted
			finish();
		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		mCursor.unregisterContentObserver(mContentObserver);
	};

	public Cursor getCursor() {
		return mCursor;
	}

	public void loadFromCursor(){

		final Intent intent = getIntent();
		final TabHost tabHost = getTabHost();
		final Uri data = intent.getData();

		final int currentTab = tabHost.getCurrentTab();
		// workaround for TabWidget bug: http://code.google.com/p/android/issues/detail?id=2772
		tabHost.setCurrentTab(0);
		tabHost.clearAllTabs();

		final Resources r = getResources();
		tabHost.addTab(tabHost.newTabSpec("cast")
				.setContent(new Intent(Intent.ACTION_VIEW, data,
										this, CastDetailsActivity.class))
				.setIndicator(r.getString(R.string.tab_cast), r.getDrawable(R.drawable.icon_cast)));

		final String title = mCursor.getString(mCursor.getColumnIndex(Cast._TITLE));
		((TextView)(getWindow().findViewById(android.R.id.title))).setText(title);
		setTitle(title);

		((TextView)(getWindow().findViewById(android.R.id.text1)))
			.setText(mCursor.getString(mCursor.getColumnIndex(Cast._AUTHOR)));

		final String thumbUrl = mCursor.getString(mCursor.getColumnIndex(Cast._THUMBNAIL_URI));

		FavoriteClickHandler.setStarred(this, mCursor, data);

		if (thumbUrl != null){
			Log.d("ViewCast", "found thumbnail " + thumbUrl);
			final ImageView mediaThumbView = ((ImageView)findViewById(android.R.id.icon));
			imgLoader.loadImage(mediaThumbView, thumbUrl);
		}

		// only add the discussion tab if it is published already.
		if (!mCursor.isNull(mCursor.getColumnIndex(Cast._PUBLIC_URI))){
			tabHost.addTab(tabHost.newTabSpec("discussion")
					.setContent(new Intent(Intent.ACTION_VIEW,
							Uri.withAppendedPath(data, Comment.PATH)))
							.setIndicator(r.getString(R.string.tab_discussion), r.getDrawable(R.drawable.icon_discussion)));

		}

		tabHost.setCurrentTab(currentTab);
	}

	private final static int
	DIALOG_DELETE = 0;

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id){
		case DIALOG_DELETE:{
			final Builder b = new Builder(this);
			b.setTitle(R.string.cast_delete_title);
			b.setMessage(R.string.cast_delete_message);
			b.setPositiveButton(R.string.dialog_button_delete, dialogDeleteOnClickListener);
			b.setNeutralButton(android.R.string.cancel, dialogDeleteOnClickListener);

			return b.create();
		}

		default:
			return null;
		}
	}

	private final DialogInterface.OnClickListener dialogDeleteOnClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which){
			case DialogInterface.BUTTON_POSITIVE:{
				getContentResolver().delete(myUri, null, null);
				setResult(RESULT_OK);
				// from here, the cast view will notice that the cursor has emptied and will finish()

			}break;

			case DialogInterface.BUTTON_NEUTRAL:{
				dialog.dismiss();
				if (Intent.ACTION_DELETE.equals(getIntent().getAction())){
					setResult(RESULT_CANCELED);
					finish();
				}
			}break;
			}
		}
	};

	@Override
	public void onCursorItemDeleted() {
		finish();

	}
}
