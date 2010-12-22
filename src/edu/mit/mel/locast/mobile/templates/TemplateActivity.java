package edu.mit.mel.locast.mobile.templates;
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
import java.util.List;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextSwitcher;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import edu.mit.mel.locast.mobile.IncrementalLocator;
import edu.mit.mel.locast.mobile.ListUtils;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.CastMedia;
import edu.mit.mel.locast.mobile.data.MediaProvider;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.ShotList;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mobile.android.widget.RelativeSizeListView;

// XXX left off loading cast videos from a saved cast
public class TemplateActivity extends VideoRecorder implements OnClickListener, LocationListener, OnItemClickListener {
	private static final String TAG = TemplateActivity.class.getSimpleName();
	public final static String ACTION_RECORD_TEMPLATED_VIDEO = "edu.mit.mobile.android.locast.ACTION_RECORD_TEMPLATED_VIDEO";

	// stateful
	private long instanceId; 		// a randomly-generated value to differentiate between different instances of a cast recording
	private int currentCastVideo; 	// the index of the currently-recording video
	private boolean isDraft; 		// casts are drafts until the user decides to publish
	private Uri currentCast; 		// the uri of the current cast, or null if it's new
	private Integer templateState; 	// state machine for the recording system.

	// non-stateful
	private Uri projectUri;
	private String publicProjectUri;
	private Cursor shotListCursor;
	private Cursor castMediaCursor;

	private Uri castMediaUri;
	private TextSwitcher osdSwitcher;
	private ImageButton mActionButton;
	private RelativeSizeListView progressBar;
	private CastMediaProgressAdapter mCastMediaProgressAdapter;
	private String filePrefix;

	private IncrementalLocator iloc;
	private Location location;

	private final static String
		RUNTIME_STATE_INSTANCE_ID 			= "edu.mit.mobile.android.locast.instanceid",
		RUNTIME_STATE_CURRENT_CAST 			= "edu.mit.mobile.android.locast.current_cast",
		RUNTIME_STATE_TEMPLATE_STATE 		= "edu.mit.mobile.android.locast.template_state",
		RUNTIME_STATE_CURRENT_CAST_VIDEO 	= "edu.mit.mobile.android.locast.current_cast_video";


	private static final int
		STATE_INITIAL 				= 0,
		STATE_PREPARED_TO_RECORD 	= 1,
		STATE_STARTING_RECORDER 	= 2,
		STATE_RECORDING 			= 3,
		STATE_STOPPING_RECORDER 	= 4,
		STATE_STOPPED_RECORDING 	= 5,
		STATE_DONE					= 6;

	@Override
	public void onCreate(Bundle savedInstanceState) {
;
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		initialStartPreview();

		setContentView(R.layout.template_main);

		final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera));

		showDialog(DIALOG_LOADING);

		// mSurfaceHolder is set from the callback so we can ensure
		// that we have one initialized.
		initSurfaceHolder(sv.getHolder());

		progressBar = (RelativeSizeListView)findViewById(R.id.progress);
		progressBar.setOnItemClickListener(this);
		//((Button)findViewById(R.id.preview)).setOnClickListener(this);
		mActionButton = ((ImageButton)findViewById(R.id.shutter));
		updateRecordingIndicator(false, true);
		mActionButton.setOnClickListener(this);
		findViewById(R.id.done).setOnClickListener(this);
		osdSwitcher = (TextSwitcher) findViewById(R.id.osd_switcher);


		// restore state
		final Uri data = getIntent().getData();
		if (savedInstanceState == null){
			savedInstanceState = new Bundle();
		}

		instanceId = savedInstanceState.getLong(RUNTIME_STATE_INSTANCE_ID, System.currentTimeMillis());
		isDraft = true;
		currentCast = savedInstanceState.<Uri>getParcelable(RUNTIME_STATE_CURRENT_CAST);
		currentCastVideo = savedInstanceState.getInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, 0);
		templateState = savedInstanceState.getInt(RUNTIME_STATE_TEMPLATE_STATE, STATE_INITIAL);

		final String type = getContentResolver().getType(data);

		if (MediaProvider.TYPE_SHOTLIST_DIR.equals(type)){
			projectUri = ShotList.getProjectUri(data);
			shotListCursor = loadShotList(data);

		}else if (MediaProvider.TYPE_PROJECT_CAST_ITEM.equals(type)){
			projectUri = Cast.getProjectUri(loadCast(data));
			shotListCursor = loadShotList(Project.getShotListUri(projectUri));

		}else if (type == null){
			throw new IllegalArgumentException("must provide a shotlist or project URI");

		}else{
			throw new IllegalArgumentException("Don't know how to handle URI: " + data);
		}

		// save a stub
		if (currentCast == null){
			currentCast = save();
		}
		castMediaUri = Cast.getCastMediaUri(currentCast);
		castMediaCursor = managedQuery(castMediaUri, CastMedia.PROJECTION, null, null, CastMedia.DEFAULT_SORT);

		mCastMediaProgressAdapter = new CastMediaProgressAdapter(this, castMediaCursor, shotListCursor);
		progressBar.setAdapter(mCastMediaProgressAdapter);

		final Cursor parent = managedQuery(projectUri, Project.PROJECTION, null, null, null);
		if (parent.getCount() > 1){
			Log.w(TAG, "got more than one project for " + projectUri);
		}else if (parent.getCount() == 0){
			Log.e(TAG, "did not find a project at "+ projectUri);
			Toast.makeText(this, "Error loading project", Toast.LENGTH_LONG).show(); // XXX i18n
			finish();
		}
		parent.moveToFirst();
		publicProjectUri = parent.getString(parent.getColumnIndex(Project._PUBLIC_URI));


		final List<String> path = data.getPathSegments();
		filePrefix = ListUtils.join(path, "-");

		setRecorderStateHandler(recorderStateHandler);

		iloc = new IncrementalLocator(this);

		waitForInitialStartPreview();
	}

	@Override
	protected void onPause() {
		super.onPause();
		iloc.removeLocationUpdates(this);
		castMediaCursor.unregisterContentObserver(castMediaObserver);
		if (currentCast != null){
			save();
			if (isDraft){
				Toast.makeText(this, "Cast saved as draft", Toast.LENGTH_LONG).show();
			}
		}
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		deleteCastIfEmpty();
	}

	@Override
	protected void onResume() {
		super.onResume();
		castMediaCursor.registerContentObserver(castMediaObserver);
		iloc.requestLocationUpdates(this);
		findViewById(R.id.shutter).requestFocus();
	}

	private Cursor loadShotList(Uri shotlist){
		return managedQuery(shotlist, ShotList.PROJECTION, null, null, null);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putLong(RUNTIME_STATE_INSTANCE_ID, instanceId);
		outState.putInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, currentCastVideo);
		outState.putParcelable(RUNTIME_STATE_CURRENT_CAST, currentCast);
		outState.putInt(RUNTIME_STATE_TEMPLATE_STATE, templateState);
	}

	/******************** dialogs ************************/

	private static final int
		DIALOG_CONFIRM_DELETE = 0,
		DIALOG_CONFIRM_RERECORD = 1,
		DIALOG_LOADING = 2;
	// this is to work around the missing bundle API introduced in API level 5
	private final int whichToDelete = -1;

	@Override
	protected Dialog onCreateDialog(int id) {
		final Builder dialogBuilder = new Builder(this);
		switch (id){
		case DIALOG_CONFIRM_DELETE:
			dialogBuilder.setTitle(R.string.template_delete_video_title);
			dialogBuilder.setPositiveButton(R.string.dialog_button_delete, dialogDeleteOnClickListener);
			dialogBuilder.setMessage(R.string.template_delete_video_body);
			return dialogBuilder.create();

		case DIALOG_CONFIRM_RERECORD:
			dialogBuilder.setTitle(R.string.template_rerecord_video_title);
			dialogBuilder.setPositiveButton(R.string.dialog_button_rerecord, dialogRerecordOnClickListener);
			dialogBuilder.setMessage(R.string.template_rerecord_video_body);
			return dialogBuilder.create();

		case DIALOG_LOADING:
            final ProgressDialog dialog = new ProgressDialog(this);
            dialog.setIndeterminate(true);
            dialog.setMessage(getString(R.string.template_loading_dialog));
            dialog.setCancelable(true);
            dialog.setOnCancelListener(new OnCancelListener() {

				@Override
				public void onCancel(DialogInterface dialog) {
					finish();

				}
			});
            return dialog;


			default:
				return super.onCreateDialog(id);
		}
	}

	private final DialogInterface.OnClickListener dialogDeleteOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			switch (which){
			case Dialog.BUTTON_POSITIVE:
				if (whichToDelete != -1){
					deleteCastVideo(whichToDelete);
				}
				break;
			}
		}
	};

	private final DialogInterface.OnClickListener dialogRerecordOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			switch (which){
			case Dialog.BUTTON_POSITIVE:
				if (whichToDelete != -1){
					deleteCastVideo(whichToDelete);
					recordCastVideo(whichToDelete);
				}
				break;
			}
		}
	};

	/*************** Template UI manipulators *********************/

	private void showRecordHelp(){
		final Toast t = Toast.makeText(TemplateActivity.this, R.string.template_toast_start_record, Toast.LENGTH_LONG);
		final DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		// position it above the red circle.
		t.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT, 0, (int)(-50.0 * metrics.density));

		t.show();
	}

    /**
     * @param showRecording if true, show that we're recording. Otherwise show that we're ready to record.
     * @param stoppable if true, show that we're stoppable during recording.
     */
    private void updateRecordingIndicator(boolean currentlyRecording, boolean stoppable) {
        final int drawableId =
                currentlyRecording ? R.drawable.btn_ic_video_record_stop
                        : R.drawable.btn_ic_video_record;
        final Drawable drawable = getResources().getDrawable(drawableId);
        mActionButton.setImageDrawable(drawable);

        mActionButton.setEnabled(!currentlyRecording || stoppable);
    }

    private void updateDoneState(boolean isDone){
    	final Button doneButton = (Button) findViewById(R.id.done);

    	if (doneButton.isEnabled() != isDone){
    		doneButton.setEnabled(isDone);

    		findViewById(R.id.cast_title).setVisibility(isDone ? View.VISIBLE : View.INVISIBLE);

    		if (isDone){
    			setOsdText("");
    			Toast.makeText(TemplateActivity.this, "done!", Toast.LENGTH_LONG).show();
    		}
    	}
    }

	/*********************** cast video manipulators *************************/

	public static final int CAST_VIDEO_DONE = -1;

	private int getNextCastVideo(){
		final int locUriCol = castMediaCursor.getColumnIndex(CastMedia._LOCAL_URI);
		for (castMediaCursor.moveToFirst(); !castMediaCursor.isAfterLast(); castMediaCursor.moveToNext()){

			if (castMediaCursor.isNull(locUriCol)){
				return castMediaCursor.getPosition();
			}
		}
		return CAST_VIDEO_DONE;
	}

	private boolean hasCastVideo(int index){
		final int locUriCol = castMediaCursor.getColumnIndex(CastMedia._LOCAL_URI);
		castMediaCursor.moveToPosition(index);
		return !castMediaCursor.isNull(locUriCol);
	}

	private void deleteCastVideo(int index){
		final int locUriCol = castMediaCursor.getColumnIndex(CastMedia._LOCAL_URI);
		castMediaCursor.moveToPosition(index);

		if (!castMediaCursor.isNull(locUriCol)){

			final ContentValues cv = new ContentValues();
			cv.putNull(CastMedia._LOCAL_URI);
			getContentResolver().update(ContentUris.withAppendedId(castMediaUri, index), cv, null, null);

		}else{
			throw new IllegalArgumentException("No cast video for given position");
		}

	}

	private void selectCastVideo(int index){
		shotListCursor.moveToPosition(index);
		final int shotTextCol = shotListCursor.getColumnIndex(ShotList._DIRECTION);
		if (!shotListCursor.isNull(shotTextCol)){
			setOsdText((index + 1) + ". " + shotListCursor.getString(shotTextCol));
		}else{
			setOsdText("");
		}
		progressBar.setSelection(index);
	}

	private final ContentObserver castMediaObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			castMediaCursor.requery();
			shotHandler.sendEmptyMessage(MSG_CAST_MEDIA_UPDATED);
		}
	};

	private void prepareToRecordNext(){
		final int nextShot = getNextCastVideo();
		if (nextShot != CAST_VIDEO_DONE){
			prepareToRecord(nextShot);
		}else{
			setState(STATE_DONE);
			currentCastVideo = CAST_VIDEO_DONE;
		}
	}


	private void setState(int newState){
		synchronized(templateState){
			Log.d(TAG, "setState("+newState+")");
			templateState = newState;
			updateDoneState(templateState == STATE_DONE);
		}
	}

	private int getState(){
		synchronized(templateState){
			return templateState;
		}
	}

	private void prepareToRecord(int index){
		selectCastVideo(index);
		initRecorder();
		setOutputFilename(filePrefix + "-" + (index + 1) +"-"+instanceId);
		prepareRecorder();
		currentCastVideo = index;
		setState(STATE_PREPARED_TO_RECORD);
	}


	/**
	 * Must call prepareToRecord first.
	 *
	 * @param shot
	 */
	private void recordCastVideo(int shot){
		if (getState() != STATE_PREPARED_TO_RECORD){
			throw new IllegalStateException("Must call prepareToRecord() first (state="+getState()+")");
		}

		if(shotListCursor.moveToPosition(shot)){
			if (hasCastVideo(shot)){
				deleteCastVideo(shot);
			}
			setOsdText("");
			setState(STATE_STARTING_RECORDER);
			startRecorder();
		}
	}



	private void saveCastVideo(int shot, int duration){
		final ContentValues cv = new ContentValues();
		final Uri localUri = Uri.fromFile(getFullOutputFile());
		cv.put(CastMedia._LIST_IDX, shot);
		cv.put(CastMedia._DURATION, duration);
		cv.put(CastMedia._MIME_TYPE, localUri != null ? "video/3gpp": null);
		cv.put(CastMedia._LOCAL_URI, localUri.toString());
		if (getContentResolver().update(ContentUris.withAppendedId(castMediaUri, shot), cv, null, null) != 1){
			Log.e(TAG, "got non-1 result from update in SaveCastVideo*()");
		}
	}



	/**
	 * Records the next needed video.
	 *
	 * @return true if a video could be recorded.
	 */
	private boolean recordCastVideo(){
		if (currentCastVideo == CAST_VIDEO_DONE){
			return false;
		}

		recordCastVideo(currentCastVideo);
		return true;
	}

	/**
	 * Loads the given cast into the UI and returns a live cursor pointing to it.
	 * @param cast
	 * @return
	 */
	private Cursor loadCast(Uri cast){
		final Cursor c = managedQuery(cast, Cast.PROJECTION, null, null, null);
		if (!c.moveToFirst()){
			throw new IllegalArgumentException("No content could be found at: " + cast);
		}
		currentCast = cast;
		loadCast(c);
		return c;
	}

	private void loadCast(Cursor cast){
		((EditText)findViewById(R.id.cast_title)).setText(cast.getString(cast.getColumnIndex(Cast._TITLE)));
		isDraft = cast.getInt(cast.getColumnIndex(Cast._DRAFT)) != 0;
	}

	/**
	 * Saves this as new cast associated with a given project
	 */
	private Uri save(){
		final ContentResolver cr = getContentResolver();

		final ContentValues cast = new ContentValues();
		cast.put(Cast._PROJECT_ID, ContentUris.parseId(projectUri));
		cast.put(Cast._PROJECT_URI, publicProjectUri);
		cast.put(Cast._PRIVACY, Cast.PRIVACY_PUBLIC);
		cast.put(Cast._TITLE, ((EditText)findViewById(R.id.cast_title)).getText().toString());
		cast.put(Cast._AUTHOR, AndroidNetworkClient.getInstance(this).getUsername());
		cast.put(Cast._DRAFT, isDraft);

		if (location != null){
			cast.put(Cast._LATITUDE, location.getLatitude());
			cast.put(Cast._LONGITUDE, location.getLongitude());
		}
		if (currentCast == null){
			currentCast = cr.insert(Uri.withAppendedPath(projectUri, Cast.PATH), cast);

			final ContentValues[] castMedia = new ContentValues[shotListCursor.getCount()];
			int i = 0;
			for (shotListCursor.moveToFirst(); !shotListCursor.isAfterLast(); shotListCursor.moveToNext()){
				castMedia[i] = new ContentValues();
				castMedia[i].put(CastMedia._LIST_IDX, i);
				i++;
			}
			final Uri castMediaUri = Cast.getCastMediaUri(currentCast);

			final int inserts = cr.bulkInsert(castMediaUri, castMedia);
			if (inserts != shotListCursor.getCount()){
				Log.e(TAG, "only created template for some of the cast videos");
			}
			Log.d(TAG, "created cast "+ currentCast);
		}else{
			cr.update(currentCast, cast, null, null);
			Log.d(TAG, "updating cast "+ currentCast);
		}

		return currentCast;
	}

	/**
	 * If the cast being created is entirely empty (no recorded videos),
	 * delete it. This should be called if the user backs out of the activity
	 * intentionally (as opposed to wandering off).
	 *
	 * @return true if the cast was empty and was therefore deleted.
	 */
	private boolean deleteCastIfEmpty(){
		boolean empty = true;
		final int vidCol = castMediaCursor.getColumnIndex(CastMedia._LOCAL_URI);
		for (castMediaCursor.moveToFirst(); empty && !castMediaCursor.isAfterLast(); castMediaCursor.moveToNext()){
			empty = castMediaCursor.isNull(vidCol);
		}
		if (empty){
			final ContentResolver cr = getContentResolver();
			cr.delete(castMediaUri, null, null);
			cr.delete(currentCast, null, null);
			Log.d(TAG, "deleted empty cast");
			currentCast = null;
			castMediaUri = null;

		}
		return empty;
	}

	private final Handler recorderStateHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
			// called when initRecorder() has finished initting.
			case MSG_RECORDER_INITIALIZED:
				try {
					removeDialog(DIALOG_LOADING);
				}catch (final IllegalArgumentException e) {
					// This would happen if it's already dismissed. Which is fine.
				}
				if (getState() == STATE_INITIAL){
					prepareToRecordNext();
				}

				break;

			case MSG_RECORDER_STARTED:
				setState(STATE_RECORDING);
				startTimedRecording(currentCastVideo);
				break;
			}

		}
	};

	private void performShutter(){
		switch (getState()){
		case STATE_PREPARED_TO_RECORD:
			recordCastVideo();
			break;

		case STATE_RECORDING:
			stopRecordingCastVideo();
			break;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode){
		case KeyEvent.KEYCODE_CAMERA:
			performShutter();
			return true;

		default:
			return super.onKeyDown(keyCode, event);
		}
	}

	public void onClick(View v) {
		switch (v.getId()){

		case R.id.done:{
			final EditText title =((EditText)findViewById(R.id.cast_title));
			if (title.getText().toString().trim().length() == 0){
				title.setError(getString(R.string.error_please_enter_a_title));
				title.requestFocus();
				break;
			}else{
				title.setError(null);
			}
			isDraft = false;
			final Uri cast = save();
			if (cast != null){
				finish();
			}
		}break;

		case R.id.shutter:{
			performShutter();
		}break;

//		case R.id.preview:{
//			isDraft = true;
//			final Uri cast = save();
//			final Intent previewCast = new Intent(Intent.ACTION_VIEW, null, getApplicationContext(), TemplatePlayer.class);
//			previewCast.setData(cast);
//			startActivity(previewCast);
//		}break;

		case R.id.cancel:
			break;
		}
	}


	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		prepareToRecord(position);
		adapter.setSelection(position);

	}

	private final static int
		MSG_SET_PROGRESS = 0,
		MSG_START_SHOT = 1,
		MSG_END_SHOT = 2,
		MSG_CAST_MEDIA_UPDATED = 3;

	private final Handler shotHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
				case MSG_END_SHOT:{
					mTimedRecording = null;
					updateRecordingIndicator(false, true);
					stopRecorder();
					setState(STATE_STOPPED_RECORDING);
					saveCastVideo(msg.arg1, msg.arg2);
					// from here, the content observer will take over the next step.
				}break;

				case MSG_START_SHOT:{
					final boolean stoppable = msg.arg2 != 0;
					updateRecordingIndicator(true, stoppable);

				}break;

				case MSG_SET_PROGRESS:{
					mCastMediaProgressAdapter.updateProgress(msg.arg1, msg.arg2);
				}break;

				case MSG_CAST_MEDIA_UPDATED:{
					if (getState() == STATE_STOPPED_RECORDING && castMediaCursor.getCount() > 0){
						prepareToRecordNext();
					}
				}break;
			}
		};
	};

	private void startTimedRecording(int shot){
		mTimedRecording = new TimedRecording(shotListCursor, shot);
		new Thread(mTimedRecording).start();
	}

	private void stopRecordingCastVideo(){
		setState(STATE_STOPPING_RECORDER);
		if (mTimedRecording != null){
			mTimedRecording.stopRecording();
			mTimedRecording = null;
		}
	}

	private TimedRecording mTimedRecording = null;

	/**
	 * Pass a cursor pointing to a given shot list to start the countdown!
	 *
	 * @author steve
	 *
	 */
	private class TimedRecording implements Runnable {
		private final Cursor mShotList;
		private final int mShotIndex;
		private final int maxTime;

		public TimedRecording(Cursor shotList, int shotIndex) {
			mShotList = shotList;
			mShotIndex = shotIndex;
			mShotList.moveToPosition(mShotIndex);
			maxTime = mShotList.getInt(mShotList.getColumnIndex(ShotList._DURATION)) * 1000;
		}

		private boolean stopRequested = false;
		public void stopRecording(){
			stopRequested = true;
		}

		@Override
		public void run() {
			final boolean infinite = maxTime == 0;
			final boolean stoppable = infinite || maxTime > 7000; // XXX switch to database

			boolean running = true;
			final long startTime = System.currentTimeMillis();

			shotHandler.obtainMessage(MSG_START_SHOT, mShotIndex, stoppable ? 1 : 0).sendToTarget();

			int elapsedTime = 0;
			while(running){
				elapsedTime = (int)(System.currentTimeMillis() - startTime);
				shotHandler.obtainMessage(MSG_SET_PROGRESS, mShotIndex, elapsedTime).sendToTarget();

				if ((!infinite && elapsedTime >= maxTime) || (stoppable && stopRequested)){
					running = false;

				}else{
					try {
						Thread.sleep(100);
					} catch (final InterruptedException e) {
						running = false;
					}
				}
			}
			shotHandler.obtainMessage(MSG_END_SHOT, mShotIndex, elapsedTime).sendToTarget();
		}
	}

	private void setOsdText(String osdText){
		osdSwitcher.setText(osdText);

	}

	public void onLocationChanged(Location location) {
		this.location = location;
//		((LocationLink)findViewById(R.id.location)).setLocation(location);
	}

	public void onProviderDisabled(String provider) {}

	public void onProviderEnabled(String provider) {}

	public void onStatusChanged(String provider, int status, Bundle extras) {}


}