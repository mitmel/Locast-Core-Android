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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
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
import android.widget.Toast;
import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.IncrementalLocator;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.WebImageLoader;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.CastMedia;
import edu.mit.mel.locast.mobile.data.Locatable;
import edu.mit.mel.locast.mobile.data.MediaProvider;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.widget.LocationLink;
import edu.mit.mel.locast.mobile.widget.TagList;

public class EditCastActivity extends Activity implements OnClickListener, LocationListener {
	public static final String
		ACTION_CAST_FROM_MEDIA_URI = "edu.mit.mobile.android.locast.share.ACTION_CAST_FROM_MEDIA_URI",
		ACTION_TOGGLE_STARRED = "edu.mit.mobile.android.locast.ACTION_TOGGLE_STARRED";

	private String castPublicUri = null;

	private IncrementalLocator iloc;

	private EditText mTitleField;
	private EditText descriptionField;
	private Spinner privacy;
	private Button sendButton;
	private Button cancelButton;
	private ImageView videoThumbView;

	private TagList tagList;
	private UpdateRecommendedTagsTask tagRecommendationTask = null;
	private Uri mediaUri;
	private String contentType;
	private Location location;
	private List<Uri> locCastMedia;
	private Cursor c;
	private Uri castUri;


	WebImageLoader imgLoader;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Intent i = getIntent();
        final Uri data = i.getData();
        final String action = i.getAction();
        final String type = i.getType();

        if (ACTION_TOGGLE_STARRED.equals(action)){
        	//final ContentResolver cr = getContentResolver();
        	//String[] STARRED_PROJECTION = {Cast._ID, Cast._};
        	//cr.query(uri, projection, selection, selectionArgs, sortOrder);

        	//getContentResolver().update(data, values, where, selectionArgs);
        }

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.cast_edit);

		imgLoader = ((Application)getApplication()).getImageLoader();


		tagList = (TagList)findViewById(R.id.new_cast_tags);
		mTitleField = (EditText) findViewById(R.id.cast_title);
		descriptionField = (EditText) findViewById(R.id.cast_description);
		privacy = ((Spinner)findViewById(R.id.privacy));

        sendButton = (Button) findViewById(R.id.done);
		cancelButton = (Button) findViewById(R.id.cancel);
		((Button)findViewById(R.id.location_set)).setOnClickListener(this);

		videoThumbView = (ImageView) findViewById(R.id.cast_thumb);

		videoThumbView.setOnClickListener(this);
        sendButton.setOnClickListener(this);
		cancelButton.setOnClickListener(this);




        if (ACTION_CAST_FROM_MEDIA_URI.equals(action) ){
        	locCastMedia = new ArrayList<Uri>();
        	locCastMedia.add(data);
        	contentType = type;

        } else if ((Intent.ACTION_SEND.equals(action)
				&& (type != null && (type.startsWith("video/")
						|| type.startsWith("audio/"))))) {
        	final Bundle extras = i.getExtras();
        	locCastMedia = new ArrayList<Uri>();
        	locCastMedia.add((Uri)extras.get(Intent.EXTRA_STREAM));

        	sendButton.setText("Send");

        } else if (Intent.ACTION_EDIT.equals(action)) {
        	c = managedQuery(data, Cast.PROJECTION, null, null, null);
        	c.moveToFirst();
        	castUri = data;
        	loadFromCursor();
        }

        iloc = new IncrementalLocator(this);

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

		if (!c.isNull(c.getColumnIndex(Cast._PUBLIC_URI))){
			castPublicUri = c.getString(c.getColumnIndex(Cast._PUBLIC_URI));
		}
		if (!c.isNull(c.getColumnIndex(Cast._MEDIA_PUBLIC_URI))){
			mediaUri = Uri.parse(c.getString(c.getColumnIndex(Cast._MEDIA_PUBLIC_URI)));
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

		final Cursor castMedia = managedQuery(Uri.withAppendedPath(castUri, CastMedia.PATH), CastMedia.PROJECTION, null, null, null);

		locCastMedia = new ArrayList<Uri>(castMedia.getCount());
		final int locUriCol = castMedia.getColumnIndex(CastMedia._LOCAL_URI);
		for (castMedia.moveToFirst(); ! castMedia.isAfterLast(); castMedia.moveToNext()){
			MediaProvider.dumpCursorToLog(castMedia, CastMedia.PROJECTION);
			if (! castMedia.isNull(locUriCol)){
				locCastMedia.add(Uri.parse(castMedia.getString(locUriCol)));
			}else{
				locCastMedia.add(null);
			}
		}

		location = Locatable.toLocation(c);
		updateLocations(null);
	}

	/**
	 * Updates the view of the locations.
	 * @param currentLocation Pass the current location if it is available.
	 */
	private void updateLocations(Location currentLocation){
		((LocationLink)findViewById(R.id.location)).setLocation(location);

		((LocationLink)findViewById(R.id.location_new)).setLocation(currentLocation);
		if (currentLocation != null){
//			final String locString = String.format("%.4f, %.4f ±%.2fm", currentLocation.getLatitude(), currentLocation.getLongitude(), currentLocation.getAccuracy());
//			((TextView)findViewById(R.id.location_new)).setText(locString);

			if (tagRecommendationTask == null || tagRecommendationTask.getStatus() == AsyncTask.Status.FINISHED){
				tagRecommendationTask = new UpdateRecommendedTagsTask(getApplicationContext(), tagList);
				tagRecommendationTask.execute(currentLocation);
			}
		}
	}

	protected ContentValues toContentValues() {
		final ContentValues cv = new ContentValues();
		cv.put(Cast._TITLE, mTitleField.getText().toString());
		cv.put(Cast._DESCRIPTION, descriptionField.getText().toString());
		cv.put(Cast._MEDIA_PUBLIC_URI, (mediaUri != null && !mediaUri.equals("null")) ? mediaUri.toString(): null);
		cv.put(Cast._CONTENT_TYPE, contentType);

		if (castPublicUri != null) {
			cv.put(Cast._PUBLIC_URI, castPublicUri);
		}
		cv.put(Cast._PRIVACY, Cast.PRIVACY_LIST[privacy.getSelectedItemPosition()]);

		if (location != null){
			cv.put(Cast._LATITUDE, location.getLatitude());
			cv.put(Cast._LONGITUDE, location.getLongitude());
		}

		cv.put(Cast._AUTHOR, AndroidNetworkClient.getInstance(this).getUsername());
		cv.put(Cast._DRAFT, false);

		Log.d("EditCast", cv.toString());
		return cv;
	}

	protected ContentValues[] toCastMediaCV(){
		final int size = locCastMedia.size();
		final ContentValues[] cvAll = new ContentValues[size];
		for (int i = 0; i < size; i++){
			cvAll[i] = new ContentValues();
			cvAll[i].put(CastMedia._LIST_IDX, i);
			if (locCastMedia.get(i) != null){
				cvAll[i].put(CastMedia._LOCAL_URI, locCastMedia.get(i).toString());
			}
			cvAll[i].put(CastMedia._DURATION, 0);
			cvAll[i].put(CastMedia._MIME_TYPE, contentType);
		}
		return cvAll;
	}

	protected boolean saveCast(){
		final ContentValues[] allMediaCv = toCastMediaCV();
		final ContentResolver cr = getContentResolver();
		Uri castMediaUri;

		final String action = getIntent().getAction();
		if (ACTION_CAST_FROM_MEDIA_URI.equals(action)
				|| Intent.ACTION_SEND.equals(action)
				|| Intent.ACTION_INSERT.equals(action)){
			setLocation(); // ensure that there's a location set for all new casts

			castUri = cr.insert(Cast.CONTENT_URI, toContentValues());
			if (castUri != null){
				castMediaUri = Uri.withAppendedPath(castUri, CastMedia.PATH);
			}else{
				Toast.makeText(this, "Error saving cast.", Toast.LENGTH_LONG);
				return false;
			}
		}else{
			 castMediaUri = Uri.withAppendedPath(castUri, CastMedia.PATH);
			cr.update(castUri, toContentValues(), null, null);
		}

		// cast media is special in that inserts into it overwrite any existing data.
		cr.bulkInsert(castMediaUri, allMediaCv);
		Cast.putTags(getContentResolver(), castUri, tagList.getTags());

		Toast.makeText(this, "Cast saved. Uploading to server...", Toast.LENGTH_LONG).show();
		return true;
	}

	public void onClick(View v) {
		switch (v.getId()){
		case R.id.cancel:
			finish();
			break;

		case R.id.done:
			saveCast();
			finish();
			break;

		case R.id.location_set:
			setLocation();
			break;

		case R.id.cast_thumb:
			if (locCastMedia.size() > 0 && locCastMedia.get(0) != null){
				startActivity(new Intent(Intent.ACTION_VIEW, locCastMedia.get(0)));
			}else{
				Toast.makeText(this, R.string.error_cast_no_videos, Toast.LENGTH_SHORT).show();
			}
			break;
		}
	}

	private void setLocation(){
		location = iloc.getLastKnownLocation();
		updateLocations(location);
	}

	public void onLocationChanged(Location location) {
		updateLocations(location);
		if (location == null){
			this.location = location;
		}

	}

	public void onProviderDisabled(String provider) {}

	public void onProviderEnabled(String provider) {}

	public void onStatusChanged(String provider, int status, Bundle extras) {}

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

	public static class UpdateRecommendedTagsTask extends AsyncTask<Location, Long, List<String>>{
		private final TagList mTagList;
		private final Context mContext;
		public UpdateRecommendedTagsTask(Context context, TagList tagList) {
			mTagList = tagList;
			mContext = context;
		}

		@Override
		protected List<String> doInBackground(Location... params) {
			final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(mContext);
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
				mTagList.clearRecommendedTags();
				mTagList.addedRecommendedTags(result);
			}
		}
	}
}
