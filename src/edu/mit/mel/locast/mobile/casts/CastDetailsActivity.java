package edu.mit.mel.locast.mobile.casts;
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
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.WebImageLoader;
import edu.mit.mel.locast.mobile.casts.BasicCursorContentObserver.BasicCursorContentObserverWatcher;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.CastMedia;
import edu.mit.mel.locast.mobile.data.Locatable;
import edu.mit.mel.locast.mobile.data.MediaProvider;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.templates.TemplatePlayer;
import edu.mit.mel.locast.mobile.widget.LocationLink;
import edu.mit.mel.locast.mobile.widget.TagListView;

public class CastDetailsActivity extends Activity implements OnClickListener, BasicCursorContentObserverWatcher {
	private Cursor c;
	private Uri castUri;

	private ImageView mediaThumbView;
	private String contentType;
	private Uri publicUri;
	private boolean hasLocalVids = false;
	private Uri geoUri;
	private int publicId = -1;

	private WebImageLoader imgLoader;

	private final BasicCursorContentObserver mContentObserver = new BasicCursorContentObserver(this);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Uri data = getIntent().getData();
        final String action = getIntent().getAction();

        setContentView(R.layout.view_cast);

        imgLoader = ((Application)getApplication()).getImageLoader();

        mediaThumbView = ((ImageView)findViewById(R.id.media_thumbnail));
        mediaThumbView.setOnClickListener(this);
        ((Button)findViewById(R.id.location)).setOnClickListener(this);
        ((ImageButton)findViewById(R.id.refresh)).setOnClickListener(this);

		if (Intent.ACTION_VIEW.equals(action)) {
        	loadFromUri(data);
        }
		loadFromCursor();
	}

	private void loadFromUri(Uri data){
		c = managedQuery(data, Cast.PROJECTION, null, null, null);
		c.moveToFirst();
		castUri = data;

		final Cursor castMedia = managedQuery(Cast.getCastMediaUri(castUri), CastMedia.PROJECTION, null, null, null);

		Log.d("CastDetails", "Cast Media:");
		final int localUriIdx = castMedia.getColumnIndex(CastMedia._LOCAL_URI);
		for (castMedia.moveToFirst(); ! castMedia.isAfterLast(); castMedia.moveToNext()){
			if (!castMedia.isNull(localUriIdx) && castMedia.getString(localUriIdx).length() > 0){
				hasLocalVids = true;
			}
			MediaProvider.dumpCursorToLog(castMedia, CastMedia.PROJECTION);
		}
	}


	@Override
	protected void onPause() {
		super.onPause();
		c.unregisterContentObserver(mContentObserver);
	}
	@Override
	protected void onResume() {
		c.registerContentObserver(mContentObserver);
		super.onResume();
	}

	public Cursor getCursor() {
		return c;
	}

	public void loadFromCursor(){
		MediaProvider.dumpCursorToLog(c, Cast.PROJECTION);

		((TextView)findViewById(R.id.item_title)).setText(
				c.getString(c.getColumnIndex(Cast._TITLE)));

		((TagListView)findViewById(R.id.tags))
			.addTags(Cast.getTags(getContentResolver(), castUri));

		((TextView)findViewById(R.id.description)).setText(
				c.getString(c.getColumnIndex(Cast._DESCRIPTION)));

		((TextView)findViewById(R.id.item_authors))
			.setText(c.getString(c.getColumnIndex(Cast._AUTHOR)));


		if (!c.isNull(c.getColumnIndex(Cast._CONTENT_TYPE))){
			contentType = c.getString(c.getColumnIndex(Cast._CONTENT_TYPE));
		}else{
			contentType = "video/3gpp";
		}

		if (!c.isNull(c.getColumnIndex(Cast._PUBLIC_URI))){
			publicUri = Uri.parse(c.getString(c.getColumnIndex(Cast._PUBLIC_URI)));
		}
		if (!c.isNull(c.getColumnIndex(Cast._PUBLIC_ID))) {
			publicId = c.getInt(c.getColumnIndex(Cast._PUBLIC_ID));
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
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.cast_view, menu);
        if (c != null){
        	final MenuItem editItem = menu.findItem(R.id.menu_edit_cast);
        	editItem.setEnabled(Cast.canEdit(c));
        }

        return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()){
		case R.id.add_cast_to_project:
			startActivity(new Intent(Intent.ACTION_ATTACH_DATA, getIntent().getData()));
			break;

		case R.id.menu_edit_cast:
			startActivity(new Intent(Intent.ACTION_EDIT,
					getIntent().getData()));
			break;

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
		}
		return true;
	}


	public void onClick(View v) {
		switch (v.getId()){
		case R.id.media_thumbnail:{

			if (publicUri != null && !hasLocalVids && AndroidNetworkClient.getInstance(this).isConnectionWorking()){
				final Intent viewVideo = new Intent(Intent.ACTION_VIEW);
				viewVideo.setDataAndType(publicUri, contentType);
				startActivity(viewVideo);

			}else if (hasLocalVids){
				final Intent viewVideos = new Intent(Intent.ACTION_VIEW, castUri, this, TemplatePlayer.class);
				startActivity(viewVideos);

			}else{
				if (publicUri != null){
					Toast.makeText(this, "Could not play video.", Toast.LENGTH_SHORT).show();
				}else{
					Toast.makeText(this, "Cast video has not been uploaded yet.", Toast.LENGTH_SHORT).show();
				}
			}

			/*
			if (localUri == null){
				try {
					if (publicUri != null){
						Cast.checkForMediaEntry(getApplicationContext(), getIntent().getData(), publicUri);

						final Intent viewVideo = new Intent(Intent.ACTION_VIEW);
						// try to look up content type if we don't have it...
						if (false && contentType == null){ // XXX
							try {
								final HttpResponse resp = AndroidNetworkClient.getInstance(getApplicationContext()).head(publicUri.toString());
								contentType = resp.getFirstHeader("Content-Type").getValue();

							} catch (final Exception e) {
								e.printStackTrace();
							}
						}
						viewVideo.setDataAndType(publicUri, contentType);

						startActivity(viewVideo);
					}
				} catch (final SyncException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else{
				if (localUri.getScheme() == null){
					// XXX
					Toast.makeText(getApplicationContext(), "Sorry, viewing of un-published casts is not fully implemented yet.", Toast.LENGTH_SHORT).show();
				}else{

					final Intent viewVideo = new Intent(Intent.ACTION_VIEW);
					viewVideo.setDataAndType(localUri, contentType);

					startActivity(viewVideo);
				}

			}*/

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
			Toast.makeText(this, "Synchronizing cast...", Toast.LENGTH_SHORT).show();

			startService(new Intent(Intent.ACTION_SYNC, getIntent().getData()));
			break;
		}
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
	}
}
