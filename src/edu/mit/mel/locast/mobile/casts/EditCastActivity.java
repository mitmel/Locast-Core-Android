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
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.rmozone.mobilevideo.AnnotationActivity;

import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.WebImageLoader;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.widget.TagList;

public class EditCastActivity extends Activity implements OnClickListener, LocationListener {
	public static final String ACTION_CAST_FROM_MEDIA_URI = "edu.mit.mel.locast.mobile.share.ACTION_CAST_FROM_MEDIA_URI";
	
	private final static int UNPUBLISHED_CAST = -1;
	
	private int castPublicId = UNPUBLISHED_CAST;
	
	private LocationManager lm;
	private String locProvider;
	
	private EditText mTitleField;
	private EditText descriptionField;
	private Spinner privacy;
	private Button sendButton;
	private Button cancelButton;
	private ImageView videoThumbView;
	
	private TagList tagList;
	private UpdateRecommendedTagsTask tagRecommendationTask = null;
	private Uri localMediaUri;
	private Uri mediaUri;
	private String contentType;
	private Location location;
	
	private Cursor c;
	private Uri castUri;
	
	WebImageLoader imgLoader;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.newcast);
		
		lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		
		imgLoader = ((Application)getApplication()).getImageLoader();
		
		final Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setCostAllowed(false);
		locProvider = lm.getBestProvider(criteria, true);
        if (locProvider == null){
                Toast.makeText(getApplicationContext(), 
                                getString(R.string.error_no_providers), 
                                Toast.LENGTH_LONG).show();
        }
        
        
		tagList = (TagList)findViewById(R.id.new_cast_tags);
		mTitleField = (EditText) findViewById(R.id.edit_cast_title);
		descriptionField = (EditText) findViewById(R.id.edit_cast_description);
		privacy = ((Spinner)findViewById(R.id.privacy));
		
        sendButton = (Button) findViewById(R.id.done);
		cancelButton = (Button) findViewById(R.id.edit_cast_cancel);
		((Button)findViewById(R.id.location_set)).setOnClickListener(this);
		
		videoThumbView = (ImageView) findViewById(R.id.edit_cast_thumb);
		
		videoThumbView.setOnClickListener(this);
        sendButton.setOnClickListener(this);
		cancelButton.setOnClickListener(this);
		
		
		final Intent i = getIntent();
        final Uri data = i.getData();
        final String action = i.getAction();
        final String type = i.getType();
        
        if (ACTION_CAST_FROM_MEDIA_URI.equals(action) ){
        	localMediaUri = data;
        	contentType = type;
        	
        } else if ((Intent.ACTION_SEND.equals(action)
				&& (type != null && (type.startsWith("video/") 
						|| type.startsWith("audio/"))))) {
        	final Bundle extras = i.getExtras();
        	localMediaUri = (Uri)extras.get(Intent.EXTRA_STREAM);
        	
        	sendButton.setText("Send");
        	
        } else if (Intent.ACTION_EDIT.equals(action)) {
        	c = managedQuery(data, Cast.PROJECTION, null, null, null);
        	c.moveToFirst();
        	castUri = data;
        	loadFromCursor();
        } 
	}
	
	protected void loadFromCursor() {
		if (!Cast.canEdit(c)){
			Toast.makeText(this, getText(R.string.error_cannot_edit), Toast.LENGTH_LONG).show();
			finish();
		}
		if (!c.isNull(c.getColumnIndex(Cast._TITLE))){
			mTitleField.setText(c.getString(c.getColumnIndex(Cast._TITLE)));
		}
		
		if (!c.isNull(c.getColumnIndex(Cast._DESCRIPTION))){
			descriptionField.setText(c.getString(c.getColumnIndex(Cast._DESCRIPTION)));
		}
		
		tagList.addTags(Cast.getTags(getContentResolver(), castUri));
		
		if (!c.isNull(c.getColumnIndex(Cast._PUBLIC_ID))){
			castPublicId = c.getInt(c.getColumnIndex(Cast._PUBLIC_ID));
		}
		if (!c.isNull(c.getColumnIndex(Cast._LOCAL_URI))){
			localMediaUri = Uri.parse(c.getString(c.getColumnIndex(Cast._LOCAL_URI)));
		}
		if (!c.isNull(c.getColumnIndex(Cast._PUBLIC_URI))){
			mediaUri = Uri.parse(c.getString(c.getColumnIndex(Cast._PUBLIC_URI)));
		}
		if (!c.isNull(c.getColumnIndex(Cast._THUMBNAIL_URI))){
			final String thumbString = c.getString(c.getColumnIndex(Cast._THUMBNAIL_URI));
			Uri.parse(thumbString);
			try {
				imgLoader.loadImage(videoThumbView, thumbString);
				//videoThumbView.setImageBitmap(imc.getImage(new URL(thumbString)));
				
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
		
		contentType = c.getString(c.getColumnIndex(Cast._CONTENT_TYPE));
		
		if (! c.isNull(c.getColumnIndex(Cast._PRIVACY))){
			privacy.setSelection(Arrays.asList(Project.PRIVACY_LIST).indexOf(c.getString(c.getColumnIndex(Cast._PRIVACY))));
			privacy.setEnabled(Cast.canChangePrivacyLevel(c));
		}
		
		location = Cast.toLocation(c);
		updateLocations(null);
	}
	
	private void updateLocations(Location currentLocation){
		if (location != null){
			final String locString = String.format("%.4f, %.4f", location.getLatitude(), location.getLongitude());
			((TextView)findViewById(R.id.location)).setText(locString);
		}
		if (currentLocation != null){
			final String locString = String.format("%.4f, %.4f ±%.2fm", currentLocation.getLatitude(), currentLocation.getLongitude(), currentLocation.getAccuracy());
			((TextView)findViewById(R.id.location_new)).setText(locString);

			if (tagRecommendationTask == null || tagRecommendationTask.getStatus() == AsyncTask.Status.FINISHED){
				tagRecommendationTask = new UpdateRecommendedTagsTask();
				tagRecommendationTask.execute(currentLocation);
			}
			
		}
	}
	
	protected ContentValues toContentValues() {
		final ContentValues cv = new ContentValues();
		cv.put(Cast._TITLE, mTitleField.getText().toString());
		cv.put(Cast._DESCRIPTION, descriptionField.getText().toString());
		cv.put(Cast._PRIVACY, "PUBLIC");
		cv.put(Cast._LOCAL_URI, (localMediaUri != null && ! "null".equals(localMediaUri)) ? localMediaUri.toString(): null);
		cv.put(Cast._PUBLIC_URI, (mediaUri != null && !mediaUri.equals("null")) ? mediaUri.toString(): null);
		cv.put(Cast._CONTENT_TYPE, contentType);
		//Cast.putTags(cv, tagList.getTags());
		if (castPublicId != UNPUBLISHED_CAST) {
			cv.put(Cast._PUBLIC_ID, castPublicId);
		}
		cv.put(Project._PRIVACY, Project.PRIVACY_LIST[privacy.getSelectedItemPosition()]);
		if (location != null){	
			cv.put(Cast._LATITUDE, location.getLatitude());
			cv.put(Cast._LONGITUDE, location.getLongitude());
		}

		Log.d("EditCast", cv.toString());
		return cv;
	}
	
	protected void saveCast(){
		final ContentValues cv = toContentValues();
		final ContentResolver cr = getContentResolver();
		
		final String action = getIntent().getAction();
		if (ACTION_CAST_FROM_MEDIA_URI.equals(action)
				|| Intent.ACTION_SEND.equals(action) 
				|| Intent.ACTION_INSERT.equals(action)){
			castUri = cr.insert(Cast.CONTENT_URI, cv);
			
			Toast.makeText(this, "Cast has been added to upload queue. Please wait for it to upload.", Toast.LENGTH_LONG);
		}else{
			cr.update(castUri, cv, null, null);
		}
		
		Cast.putTags(getContentResolver(), castUri, tagList.getTags());
	}

	public void onClick(View v) {
		switch (v.getId()){
		case R.id.edit_cast_cancel:
			finish();
			break;

		case R.id.done:
			saveCast();
			finish();
			break;

		case R.id.edit_cast_thumb:{
				Log.println(1, "EditCastActivity.java", "Trying to annotate cast");
	
				Uri uri = localMediaUri;
				if (uri == null){
					uri = mediaUri;
				}
				if (uri != null){
					//open AnnotateView to watch and comment on video
					
					if(this.castPublicId > 0) {
						final Uri uri_with_global_awareness = uri.buildUpon().appendPath("id").appendPath(""+castPublicId).build();
						
						final Intent i = new Intent();
						i.setClass(this, AnnotationActivity.class);
						i.setData(uri_with_global_awareness);
						i.setAction(AnnotationActivity.ACTION_ANNOTATE_CAST_FROM_LOCAST_ID);
						
						startActivity(i);					
					}
	
					final Intent viewVideo = new Intent(AnnotationActivity.ACTION_ANNOTATE_CAST_FROM_MEDIA_URI);
					viewVideo.setDataAndType(uri, contentType);
					startActivity(viewVideo);
	

				}else{
					Toast.makeText(getApplicationContext(), "No video :-(", Toast.LENGTH_LONG).show();
				}
			}
		break;
		
		case R.id.location_set:
			location = lm.getLastKnownLocation(locProvider);
			updateLocations(location);
			break;
		}
	}

	public void onLocationChanged(Location location) {
		updateLocations(location);
		if (location == null){
			this.location = location;
		}
		
	}

	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		lm.removeUpdates(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		// we'll get updates and use the last-known location.
		lm.requestLocationUpdates(locProvider, 5000, 1, this);
	}

	private class UpdateRecommendedTagsTask extends AsyncTask<Location, Long, List<String>>{

		@Override
		protected List<String> doInBackground(Location... params) {
			final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(getApplicationContext());
			try {
				// this is done first so that tags aren't cleared if there's an error getting new ones.
				final List<String> recommended = nc.getRecommendedTagsList(params[0]);
				return recommended;
			} catch (final Exception e) {
				e.printStackTrace();
				// We don't actually care about this content that much...
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(List<String> result) {
			if (result != null){
				tagList.clearRecommendedTags();
				tagList.addedRecommendedTags(result);
			}
		}
	}
}
