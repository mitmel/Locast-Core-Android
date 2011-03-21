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
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.WebImageLoader;
import edu.mit.mobile.android.locast.casts.BasicCursorContentObserver.BasicCursorContentObserverWatcher;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.templates.TemplatePlayer;
import edu.mit.mobile.android.locast.widget.LocationLink;
import edu.mit.mobile.android.locast.widget.TagListView;

public class CastDetailsActivity extends Activity implements OnClickListener, BasicCursorContentObserverWatcher {
	@SuppressWarnings("unused")
	private static final String TAG = CastDetailsActivity.class.getSimpleName();
	private Cursor c;
	private Uri castUri;

	public static final String ACTION_PLAY_CAST = "edu.mit.mobile.android.locast.ACTION_PLAY_CAST";

	private ImageView mediaThumbView;
	private String contentType;
	private Uri mediaPublicUri;
	private Uri mediaLocalUri;
	private boolean hasLocalVids = false;
	private Uri geoUri;

	private WebImageLoader imgLoader;

	private final BasicCursorContentObserver mContentObserver = new BasicCursorContentObserver(this);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Intent intent = getIntent();
		final Uri data = intent.getData();
        final String action = intent.getAction();

        setContentView(R.layout.view_cast);

        imgLoader = ((Application)getApplication()).getImageLoader();

        mediaThumbView = ((ImageView)findViewById(R.id.media_thumbnail));
        mediaThumbView.setOnClickListener(this);
        ((Button)findViewById(R.id.location)).setOnClickListener(this);

		if (Intent.ACTION_VIEW.equals(action) || ACTION_PLAY_CAST.equals(action)) {
        	loadFromUri(data);
        	loadFromCursor();
        }
		// loaded!

		if (ACTION_PLAY_CAST.equals(action)) {
			playCast();
		}
	}

	private void loadFromUri(Uri data){
		c = managedQuery(data, Cast.PROJECTION, null, null, null);
		c.moveToFirst();
		castUri = data;


		final Cursor castMedia = managedQuery(Cast.getCastMediaUri(castUri), CastMedia.PROJECTION, null, null, null);

		Log.d("CastDetails", "Cast Media:");
		final int localUriIdx = castMedia.getColumnIndex(CastMedia._LOCAL_URI);
		for (castMedia.moveToFirst(); !hasLocalVids && ! castMedia.isAfterLast(); castMedia.moveToNext()){
			if (!castMedia.isNull(localUriIdx) && castMedia.getString(localUriIdx).length() > 0){
				hasLocalVids = true;
			}
			MediaProvider.dumpCursorToLog(castMedia, CastMedia.PROJECTION);
		}
	}


	@Override
	protected void onPause() {
		super.onPause();
		if (c != null){
			c.unregisterContentObserver(mContentObserver);
		}
	}
	@Override
	protected void onResume() {
		super.onResume();
		if (c != null){
			c.registerContentObserver(mContentObserver);
			if (c.moveToFirst()){
				loadFromCursor();
			}else{
				// handle the case where this item is deleted
				finish();
			}
		}
	}

	public Cursor getCursor() {
		return c;
	}

	public void loadFromCursor(){
		MediaProvider.dumpCursorToLog(c, Cast.PROJECTION);

		((TagListView)findViewById(R.id.tags))
			.addTags(Cast.getTags(getContentResolver(), castUri));

		((TextView)findViewById(R.id.description)).setText(
				c.getString(c.getColumnIndex(Cast._DESCRIPTION)));


		if (!c.isNull(c.getColumnIndex(Cast._CONTENT_TYPE))){
			contentType = c.getString(c.getColumnIndex(Cast._CONTENT_TYPE));
		}else{
			contentType = "video/3gpp";
		}
		if (!c.isNull(c.getColumnIndex(Cast._MEDIA_LOCAL_URI))){
			mediaLocalUri = Uri.parse(c.getString(c.getColumnIndex(Cast._MEDIA_LOCAL_URI)));
		}

		if (!c.isNull(c.getColumnIndex(Cast._MEDIA_PUBLIC_URI))){
			mediaPublicUri = Uri.parse(c.getString(c.getColumnIndex(Cast._MEDIA_PUBLIC_URI)));
		}

		final LocationLink locButton = (LocationLink)findViewById(R.id.location);
		if (!c.isNull(c.getColumnIndex(Cast._LATITUDE))){
			geoUri = Locatable.toGeoUri(c);
			locButton.setEnabled(true);
			locButton.setLocation(Locatable.toLocation(c));

		}else{
			((Button)findViewById(R.id.location)).setEnabled(false);
		}

		final String thumbUrl = c.getString(c.getColumnIndex(Cast._THUMBNAIL_URI));

		if (thumbUrl != null){
			Log.d("ViewCast", "found thumbnail " + thumbUrl);

			imgLoader.loadImage(mediaThumbView, thumbUrl);
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.cast_options, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (c != null){
			final boolean canEdit = Cast.canEdit(this, c);
		       menu.findItem(R.id.cast_edit).setEnabled(canEdit);
		       menu.findItem(R.id.cast_delete).setEnabled(canEdit);
		}

		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		final Uri cast = getIntent().getData();

		switch (item.getItemId()){
		case R.id.add_cast_to_project:
			startActivity(new Intent(Intent.ACTION_ATTACH_DATA, cast));
			return true;

		case R.id.cast_edit:
			startActivity(new Intent(Intent.ACTION_EDIT, cast));
			return true;

		case R.id.cast_delete:
			startActivity(new Intent(Intent.ACTION_DELETE, cast));
			return true;

       case R.id.cast_play:
    	   startActivity(new Intent(CastDetailsActivity.ACTION_PLAY_CAST, cast));
    	   return true;

		case R.id.refresh:
			Toast.makeText(this, "Synchronizing cast...", Toast.LENGTH_SHORT).show();

			startService(new Intent(Intent.ACTION_SYNC, getIntent().getData()));
			return true;

		/*case R.id.menu_annotate_cast:
			if (localUri != null){
				if(publicId > 0) {
					final Uri uri_with_global_awareness = localUri.buildUpon().appendPath("id").appendPath(""+publicId).build();

					final Intent i = new Intent();
					i.setClass(this, AnnotationActivity.class);
					i.setData(uri_with_global_awareness);
					i.setAction(AnnotationActivity.ACTION_ANNOTATE_CAST_FROM_LOCAST_ID);

					startActivity(i);
				}
				else {
					startActivity(new Intent(AnnotationActivity.ACTION_ANNOTATE_CAST_FROM_MEDIA_URI,
						localUri));
				}
			}else{
				Toast.makeText(this, "Video annotation requires a local copy of the video.", Toast.LENGTH_LONG).show();
			}

			break;*/
			default:
				return super.onMenuItemSelected(featureId, item);
		}
	}

	private void playCast(){
		final boolean finishImmediately = ACTION_PLAY_CAST.equals(getIntent().getAction());

		final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		final NetworkInfo ni = cm.getActiveNetworkInfo();

		final boolean canStream = ni != null && ni.isConnected();

		final Intent viewVideo = new Intent(Intent.ACTION_VIEW);

		if (mediaLocalUri != null){
			viewVideo.setDataAndType(mediaLocalUri, contentType);
		}else{
			if (hasLocalVids){
				viewVideo.setData(castUri);
				viewVideo.setClass(this, TemplatePlayer.class);

			}else{
				if (mediaPublicUri != null){
					if (canStream){
						viewVideo.setDataAndType(mediaPublicUri, contentType);
					}else{
						Toast.makeText(this, R.string.cast_error_could_not_play_video_neither_net_nor_local, Toast.LENGTH_LONG).show();
					}
				}else{
					Toast.makeText(this, R.string.notice_cast_video_has_not_been_uploaded_yet, Toast.LENGTH_SHORT).show();
				}
			}
		}

		if (finishImmediately){
			viewVideo.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
			if (viewVideo.getData() != null){
				startActivity(viewVideo);
			}
			finish();
		}else{
			if (viewVideo.getData() != null){
				startActivity(viewVideo);
			}
		}
	}


	public void onClick(View v) {
		switch (v.getId()){
		case R.id.media_thumbnail:{
			playCast();

			break;
		}

		case R.id.location:
            final Intent mapsIntent = new Intent(Intent.ACTION_VIEW, geoUri);
            try {
            	startActivity(mapsIntent);
            }catch (final ActivityNotFoundException e){
            	// no maps :-(
            	Toast.makeText(this, R.string.error_no_maps, Toast.LENGTH_LONG);
            }
			break;

		case R.id.refresh:
			Toast.makeText(this, R.string.cast_synchronizing, Toast.LENGTH_SHORT).show();

			startService(new Intent(Intent.ACTION_SYNC, getIntent().getData()).putExtra(Sync.EXTRA_EXPLICIT_SYNC, true));
			break;
		}
	}

	@Override
	public void onCursorItemDeleted() {
		finish();

	}
}
