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
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.Toast;
import android.widget.VideoView;
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

public class TemplateActivity extends VideoRecorder implements OnClickListener, LocationListener, OnItemClickListener {
	private static final String TAG = TemplateActivity.class.getSimpleName();
	public final static String ACTION_RECORD_TEMPLATED_VIDEO = "edu.mit.mobile.android.locast.ACTION_RECORD_TEMPLATED_VIDEO";

	// stateful
	private long mInstanceId; 		// a randomly-generated value to differentiate between different instances of a cast recording
	private int mCurrentCastVideo = -1; 	// the index of the currently-recording video
	private boolean mIsDraft; 		// casts are drafts until the user decides to publish
	private Uri mCurrentCast; 		// the uri of the current cast, or null if it's new
	private Integer mTemplateState = STATE_NULL; 	// state machine for the recording system.
	private Integer mPlaybackMode; 	// the mode of playback.

	// non-stateful
	private Uri mProjectUri;
	private String mPublicProjectUri;
	private Cursor mShotListCursor;
	private Cursor mCastMediaCursor;
	private boolean mStoppable;
	private int mLocUriCol;

	private Uri mCastMediaUri;
	private TextSwitcher mOsdSwitcher;
	private ImageButton mActionButton;
	private RelativeSizeListView mProgressBar;
	private CastMediaProgressAdapter mCastMediaProgressAdapter;
	private String mFilePrefix;

	private IncrementalLocator mIloc;
	private Location mLocation;


	private final static String
		RUNTIME_STATE_INSTANCE_ID 			= "edu.mit.mobile.android.locast.instanceid",
		RUNTIME_STATE_CURRENT_CAST 			= "edu.mit.mobile.android.locast.current_cast",
		RUNTIME_STATE_TEMPLATE_STATE 		= "edu.mit.mobile.android.locast.template_state",
		RUNTIME_STATE_PLAYBACK_MODE 		= "edu.mit.mobile.android.locast.playback_mode",
		RUNTIME_STATE_CURRENT_CAST_VIDEO 	= "edu.mit.mobile.android.locast.current_cast_video";


	private static final int
		STATE_NULL					= -1,
		STATE_INITIAL 				= 0,
		STATE_RECORDER_READY 		= 1,
		STATE_RECORDER_STARTING 	= 2,
		STATE_RECORDER_RECORDING 	= 3,
		STATE_RECORDER_STOPPING 	= 4,
		STATE_RECORDER_STOPPED 		= 5,
		STATE_PLAYBACK_PREPARING  	= 6,
		STATE_PLAYBACK 				= 7;

	private static final int
		PLAYBACK_MODE_SINGLE 	= 0,
		PLAYBACK_MODE_ALL_FIRST = 1,
		PLAYBACK_MODE_ALL 		= 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		super.onCreate(savedInstanceState);

		setContentView(R.layout.template_main);

		final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera)); // R.id.camera

		showDialog(DIALOG_LOADING_CAMERA);

		// mSurfaceHolder is set from the callback so we can ensure
		// that we have one initialized.
		initSurfaceHolder(sv.getHolder());

		mProgressBar = (RelativeSizeListView)findViewById(R.id.progress);
		mProgressBar.setOnItemClickListener(this);

		mActionButton = ((ImageButton)findViewById(R.id.shutter));
		updateRecordingIndicator(false, true);
		mActionButton.setOnClickListener(this);
		findViewById(R.id.done).setOnClickListener(this);
		mOsdSwitcher = (TextSwitcher) findViewById(R.id.osd_switcher);

		findViewById(R.id.playback_overlay).setOnClickListener(this);

		final VideoView playback = (VideoView)findViewById(R.id.playback);
		playback.setOnCompletionListener(mPlaybackCompletionListener);
		playback.setOnPreparedListener(mPlaybackOnPreparedListener);

		// restore state
		final Uri data = getIntent().getData();
		if (savedInstanceState == null){
			savedInstanceState = new Bundle();
		}

		mInstanceId = savedInstanceState.getLong(RUNTIME_STATE_INSTANCE_ID, System.currentTimeMillis());
		mIsDraft = true;
		mCurrentCast = savedInstanceState.<Uri>getParcelable(RUNTIME_STATE_CURRENT_CAST);

		setState(savedInstanceState.getInt(RUNTIME_STATE_TEMPLATE_STATE, STATE_INITIAL));
		mPlaybackMode = savedInstanceState.getInt(RUNTIME_STATE_PLAYBACK_MODE, PLAYBACK_MODE_SINGLE);

		final String type = getContentResolver().getType(data);

		if (MediaProvider.TYPE_SHOTLIST_DIR.equals(type)){
			mProjectUri = ShotList.getProjectUri(data);
			mShotListCursor = loadShotList(data);

		}else if (MediaProvider.TYPE_PROJECT_CAST_ITEM.equals(type)){
			mProjectUri = Cast.getProjectUri(loadCast(data));
			mShotListCursor = loadShotList(Project.getShotListUri(mProjectUri));

		}else if (type == null){
			throw new IllegalArgumentException("must provide a shotlist or project URI");

		}else{
			throw new IllegalArgumentException("Don't know how to handle URI: " + data);
		}

		// save a stub
		if (mCurrentCast == null){
			mCurrentCast = save();
		}
		mCastMediaUri = Cast.getCastMediaUri(mCurrentCast);
		mCastMediaCursor = managedQuery(mCastMediaUri, CastMedia.PROJECTION, null, null, CastMedia.DEFAULT_SORT);
		mLocUriCol = mCastMediaCursor.getColumnIndex(CastMedia._LOCAL_URI);

		mCastMediaProgressAdapter = new CastMediaProgressAdapter(this, mCastMediaCursor, mShotListCursor);
		mProgressBar.setAdapter(mCastMediaProgressAdapter);

		final Cursor parent = managedQuery(mProjectUri, Project.PROJECTION, null, null, null);
		if (parent.getCount() > 1){
			Log.w(TAG, "got more than one project for " + mProjectUri);
		}else if (parent.getCount() == 0){
			Log.e(TAG, "did not find a project at "+ mProjectUri);
			Toast.makeText(this, "Error loading project", Toast.LENGTH_LONG).show(); // XXX i18n
			finish();
		}
		parent.moveToFirst();
		mPublicProjectUri = parent.getString(parent.getColumnIndex(Project._PUBLIC_URI));


		final List<String> path = data.getPathSegments();
		mFilePrefix = ListUtils.join(path, "-");

		setRecorderStateHandler(recorderStateHandler);

		mIloc = new IncrementalLocator(this);

		selectCastVideo(savedInstanceState.getInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, 0));
		updateControls();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mIloc.removeLocationUpdates(this);
		mCastMediaCursor.unregisterContentObserver(castMediaObserver);
		if (mCurrentCast != null){
			save();
			if (mIsDraft){
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
		mCastMediaCursor.registerContentObserver(castMediaObserver);
		mIloc.requestLocationUpdates(this);
		findViewById(R.id.shutter).requestFocus();
	}

	private Cursor loadShotList(Uri shotlist){
		return managedQuery(shotlist, ShotList.PROJECTION, null, null, null);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putLong(RUNTIME_STATE_INSTANCE_ID, mInstanceId);
		outState.putInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, mCurrentCastVideo);
		outState.putParcelable(RUNTIME_STATE_CURRENT_CAST, mCurrentCast);
		outState.putInt(RUNTIME_STATE_TEMPLATE_STATE, mTemplateState);
	}

	/******************** dialogs ************************/

	private static final int
		DIALOG_CONFIRM_RERECORD = 0,
		DIALOG_LOADING_CAMERA 	= 1,
		DIALOG_LOADING_PREVIEW 	= 2,
		DIALOG_CAST_DELETE 		= 3;
	// this is to work around the missing bundle API introduced in API level 5
	private int mDialogWhichToDelete = -1;

	@Override
	protected Dialog onCreateDialog(int id) {
		final Builder dialogBuilder = new Builder(this);
		switch (id){
		case DIALOG_CAST_DELETE:
			dialogBuilder.setTitle(R.string.cast_delete_title);
			dialogBuilder.setPositiveButton(R.string.dialog_button_delete, dialogCastDeleteOnClickListener);
			dialogBuilder.setNeutralButton(android.R.string.cancel, dialogCastDeleteOnClickListener);
			dialogBuilder.setMessage(R.string.cast_delete_message);
			return dialogBuilder.create();

		case DIALOG_CONFIRM_RERECORD:
			dialogBuilder.setTitle(R.string.template_rerecord_video_title);
			dialogBuilder.setPositiveButton(R.string.dialog_button_rerecord, dialogRerecordOnClickListener);
			dialogBuilder.setNeutralButton(android.R.string.cancel, dialogRerecordOnClickListener);
			dialogBuilder.setMessage(R.string.template_rerecord_video_body);
			return dialogBuilder.create();

		case DIALOG_LOADING_CAMERA:{
            final ProgressDialog dialog = new ProgressDialog(this);
            dialog.setIndeterminate(true);
            dialog.setMessage(getString(R.string.template_dialog_loading_camera));
            dialog.setCancelable(true);
            dialog.setOnCancelListener(new OnCancelListener() {

				@Override
				public void onCancel(DialogInterface dialog) {
					finish();

				}
			});
            return dialog;
		}

		case DIALOG_LOADING_PREVIEW:{
            final ProgressDialog dialog = new ProgressDialog(this);
            dialog.setIndeterminate(true);
            dialog.setMessage(getString(R.string.template_dialog_loading_preview));
            dialog.setCancelable(true);
            dialog.setOnCancelListener(new OnCancelListener() {

				@Override
				public void onCancel(DialogInterface dialog) {
					finish();

				}
			});
            return dialog;
            }


			default:
				return super.onCreateDialog(id);
		}
	}

	private final DialogInterface.OnClickListener dialogRerecordOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			switch (which){
			case Dialog.BUTTON_POSITIVE:
				if (mDialogWhichToDelete != -1){
					deleteCastVideo(mDialogWhichToDelete);
					prepareToRecord(mDialogWhichToDelete);
				}
				break;
			case Dialog.BUTTON_NEUTRAL:
				dialog.dismiss();
				break;
			}
		}
	};

	private final DialogInterface.OnClickListener dialogCastDeleteOnClickListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			switch (which){
			case Dialog.BUTTON_POSITIVE:
				deleteCast();
				finish();
			case Dialog.BUTTON_NEUTRAL:
				dialog.dismiss();
				break;
			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.templated_video, menu);
		return true;

	};
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.publish_cast).setEnabled(isDone());
		return true;
	};

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
		case R.id.publish_cast:
			publish();
			return true;

		case R.id.delete_cast:
			showDialog(DIALOG_CAST_DELETE);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/*************** Template UI manipulators *********************/

    /**
     * @param showRecording if true, show that we're recording. Otherwise show that we're ready to record.
     * @param stoppable if true, show that we're stoppable during recording.
     */
	private void updateRecordingIndicator(boolean currentlyRecording, boolean stoppable){
		updateRecordingIndicator(currentlyRecording, stoppable, false);
	}
    private void updateRecordingIndicator(boolean currentlyRecording, boolean stoppable, boolean recordingExists) {
        final int drawableId =
                currentlyRecording ? R.drawable.btn_ic_video_record_stop
                        : R.drawable.btn_ic_video_record;

        // after trying it out with some users, this icon was confusing.
//        if (recordingExists){
//        	drawableId = R.drawable.btn_ic_review_delete;
//        }
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
    			Toast.makeText(this, R.string.template_done, Toast.LENGTH_LONG).show();
    		}
    	}
    }

	private void updateControls(){
		mProgressBar.setEnabled(mTemplateState == STATE_PLAYBACK
				|| mTemplateState == STATE_RECORDER_READY);
		updateRecordingIndicator(mTemplateState == STATE_RECORDER_RECORDING, mStoppable, mCastMediaCursor != null && mCurrentCastVideo >= 0 && hasCastVideo(mCurrentCastVideo));
		if (mCastMediaCursor != null){
			updateDoneState(getNextUnrecordedCastVideo() == CAST_VIDEO_DONE);
		}
	}

	private boolean isDone(){
		return getNextUnrecordedCastVideo() == CAST_VIDEO_DONE;
	}

	/*********************** cast video manipulators *************************/

	public static final int CAST_VIDEO_DONE = -2;

	private int getNextUnrecordedCastVideo(){
		for (mCastMediaCursor.moveToFirst(); !mCastMediaCursor.isAfterLast(); mCastMediaCursor.moveToNext()){

			if (mCastMediaCursor.isNull(mLocUriCol)){
				return mCastMediaCursor.getPosition();
			}
		}
		return CAST_VIDEO_DONE;
	}

	/**
	 * Returns the index of the next cast video.
	 *
	 * @param index current position
	 * @return the next position or AdapterView.INVALID_POSITION if the end has been reached.
	 */
	private int getNextCastVideo(int index){
		mCastMediaCursor.moveToPosition(index);
		if (mCastMediaCursor.moveToNext()){
			return mCastMediaCursor.getPosition();
		}else{
			return AdapterView.INVALID_POSITION;
		}
	}


	private boolean hasCastVideo(int index){
		mCastMediaCursor.moveToPosition(index);
		return !mCastMediaCursor.isNull(mLocUriCol);
	}

	private void deleteCastVideo(int index){
		if (hasCastVideo(index)){

			final ContentValues cv = new ContentValues();
			cv.putNull(CastMedia._LOCAL_URI);
			getContentResolver().update(ContentUris.withAppendedId(mCastMediaUri, index), cv, null, null);

		}else{
			throw new IllegalArgumentException("No cast video for given position");
		}

	}

	/**
	 * Updates the interface to reflect the selection state.
	 *
	 * @param index
	 */
	private void selectCastVideo(int index){
		final int state = getState();
		final boolean playAllMode =
			(state == STATE_PLAYBACK || state == STATE_PLAYBACK_PREPARING)
			&& (mPlaybackMode == PLAYBACK_MODE_ALL || mPlaybackMode == PLAYBACK_MODE_ALL_FIRST);
		mProgressBar.setSelected(playAllMode);

		if (mCurrentCastVideo == index){
			return;
		}

		if (index != CAST_VIDEO_DONE && index != RelativeSizeListView.INVALID_POSITION){
			mShotListCursor.moveToPosition(index);
			final int shotTextCol = mShotListCursor.getColumnIndex(ShotList._DIRECTION);
			if (!mShotListCursor.isNull(shotTextCol)){
				setOsdText((index + 1) + ". " + mShotListCursor.getString(shotTextCol));
			}else{
				setOsdText("");
			}

			updateRecordingIndicator(false, true, hasCastVideo(index));
		}

		if (index == CAST_VIDEO_DONE){
			mProgressBar.setSelection(RelativeSizeListView.INVALID_POSITION);
		}else{
			mProgressBar.setSelection(index);
		}

		mCurrentCastVideo = index;
	}

	private final ContentObserver castMediaObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			mCastMediaCursor.requery();
			shotHandler.sendEmptyMessage(MSG_SHOT_CAST_MEDIA_UPDATED);

		}
	};

	private void prepareToRecordNext(){
		final int nextShot = getNextUnrecordedCastVideo();
		if (nextShot != CAST_VIDEO_DONE){
			prepareToRecord(nextShot);
		}else{
			prepareToPlayback(0, PLAYBACK_MODE_ALL_FIRST);
		}
	}

	private void setState(int newState){
		synchronized(mTemplateState){
			Log.d(TAG, "Template State: moving from "+ mTemplateState + " to "+newState);
			if (newState != mTemplateState){ // new state
				switch (newState){
				case STATE_PLAYBACK:
				case STATE_RECORDER_READY:
					try {
						dismissDialog(DIALOG_LOADING_PREVIEW);
					}catch (final IllegalArgumentException e) {
						// This would happen if it's already dismissed. Which is fine.
					}
					try {
						dismissDialog(DIALOG_LOADING_CAMERA);
					}catch (final IllegalArgumentException e) {
						// This would happen if it's already dismissed. Which is fine.
					}
					break;

				case STATE_PLAYBACK_PREPARING:
					showDialog(DIALOG_LOADING_PREVIEW);
					break;
				}
			}
			mTemplateState = newState;
			updateControls();
		}
	}

	private int getState(){
		synchronized(mTemplateState){
			return mTemplateState;
		}
	}

	private void prepareToRecord(int index){
		if (getState() == STATE_PLAYBACK){
			stopPlayback();
		}
		// getState() is intentionally called twice here, as it should
		// change with stopPlayback()
		if (getState() == STATE_RECORDER_READY && index == mCurrentCastVideo){
			return;
		}

		selectCastVideo(index);
		initRecorder();
		setOutputFilename(mFilePrefix + "-" + (index + 1) +"-"+mInstanceId);
		prepareRecorder();
		setState(STATE_RECORDER_READY);
	}


	/**
	 * Must call prepareToRecord first.
	 *
	 * @param shot
	 */
	private void recordCastVideo(int shot){
		if (getState() != STATE_RECORDER_READY){
			throw new IllegalStateException("Must call prepareToRecord() first (state="+getState()+")");
		}

		if(mShotListCursor.moveToPosition(shot)){
			if (hasCastVideo(shot)){
				deleteCastVideo(shot);
			}
			setOsdText("");
			setState(STATE_RECORDER_STARTING);
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
		if (getContentResolver().update(ContentUris.withAppendedId(mCastMediaUri, shot), cv, null, null) != 1){
			Log.e(TAG, "got non-1 result from update in SaveCastVideo*()");
		}
	}



	/**
	 * Records the next needed video.
	 *
	 * @return true if a video could be recorded.
	 */
	private boolean recordCastVideo(){
		if (mCurrentCastVideo == CAST_VIDEO_DONE){
			return false;
		}

		recordCastVideo(mCurrentCastVideo);
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
		mCurrentCast = cast;
		loadCast(c);
		return c;
	}

	private void loadCast(Cursor cast){
		((EditText)findViewById(R.id.cast_title)).setText(cast.getString(cast.getColumnIndex(Cast._TITLE)));
		mIsDraft = cast.getInt(cast.getColumnIndex(Cast._DRAFT)) != 0;
	}

	/**
	 * Saves this as new cast associated with a given project
	 */
	private Uri save(){
		final ContentResolver cr = getContentResolver();

		final ContentValues cast = new ContentValues();
		cast.put(Cast._PROJECT_ID, ContentUris.parseId(mProjectUri));
		cast.put(Cast._PROJECT_URI, mPublicProjectUri);
		cast.put(Cast._PRIVACY, Cast.PRIVACY_PUBLIC);
		cast.put(Cast._TITLE, ((EditText)findViewById(R.id.cast_title)).getText().toString());
		cast.put(Cast._AUTHOR, AndroidNetworkClient.getInstance(this).getUsername());
		cast.put(Cast._DRAFT, mIsDraft);

		if (mLocation != null){
			cast.put(Cast._LATITUDE, mLocation.getLatitude());
			cast.put(Cast._LONGITUDE, mLocation.getLongitude());
		}
		if (mCurrentCast == null){
			Log.d(TAG, "saving cast:" + cast);
			mCurrentCast = cr.insert(Uri.withAppendedPath(mProjectUri, Cast.PATH), cast);

			final ContentValues[] castMedia = new ContentValues[mShotListCursor.getCount()];
			int i = 0;
			for (mShotListCursor.moveToFirst(); !mShotListCursor.isAfterLast(); mShotListCursor.moveToNext()){
				castMedia[i] = new ContentValues();
				castMedia[i].put(CastMedia._LIST_IDX, i);
				i++;
			}
			final Uri castMediaUri = Cast.getCastMediaUri(mCurrentCast);

			final int inserts = cr.bulkInsert(castMediaUri, castMedia);
			if (inserts != mShotListCursor.getCount()){
				Log.e(TAG, "only created template for some of the cast videos");
			}
			Log.d(TAG, "created cast "+ mCurrentCast);
		}else{
			cr.update(mCurrentCast, cast, null, null);
			Log.d(TAG, "updating cast "+ mCurrentCast);
		}

		return mCurrentCast;
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
		for (mCastMediaCursor.moveToFirst(); empty && !mCastMediaCursor.isAfterLast(); mCastMediaCursor.moveToNext()){
			empty = mCastMediaCursor.isNull(mLocUriCol);
		}
		if (empty){
			deleteCast();
		}
		return empty;
	}

	private void deleteCast(){
		final ContentResolver cr = getContentResolver();
		cr.delete(mCastMediaUri, null, null);
		cr.delete(mCurrentCast, null, null);
		Log.d(TAG, "deleted empty cast");
		mCurrentCast = null;
		mCastMediaUri = null;
	}

	private final Handler recorderStateHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
			// called when initRecorder() has finished initting.
			case MSG_RECORDER_INITIALIZED:
				if (getState() == STATE_INITIAL){
					prepareToRecordNext();
				}

				break;

			case MSG_RECORDER_STARTED:
				setState(STATE_RECORDER_RECORDING);
				startTimedRecording(mCurrentCastVideo);
				break;

			case MSG_PREVIEW_STOPPED:{
				if (getState() == STATE_PLAYBACK_PREPARING){
					prepareToPlaybackPart2();
				}
				}break;
			}

		}
	};

	private void performShutter(){
		switch (getState()){
		case STATE_RECORDER_READY:
			recordCastVideo();
			break;

		case STATE_RECORDER_RECORDING:
			stopRecordingCastVideo();
			break;

		case STATE_PLAYBACK:
			mDialogWhichToDelete = mCurrentCastVideo;
			showDialog(DIALOG_CONFIRM_RERECORD);

			break;
		}
	}

	/**
	 * @return true if successfully published.
	 */
	private boolean publish(){
		final EditText title =((EditText)findViewById(R.id.cast_title));
		if (title.getText().toString().trim().length() == 0){
			title.setError(getString(R.string.error_please_enter_a_title));
			title.requestFocus();
			return false;
		}else{
			title.setError(null);
		}
		mIsDraft = false;
		final Uri cast = save();
		if (cast != null){
			finish();
			return true;
		}
		return false;
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
			publish();
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

		case R.id.playback_overlay:{
			togglePlayback();
			}break;

		case R.id.delete_cast:{
			showDialog(DIALOG_CAST_DELETE);
		}break;
		}
	}

	/********************************************************
	 * ********************** playback **********************
	 * ******************************************************
	 */
	private void togglePlayback(){
		final VideoView vv = ((VideoView)findViewById(R.id.playback));
		if (vv.isPlaying()){
			vv.pause();

		}else{
			vv.start();
		}
		updatePlaybackIndicator();
	}

	private void updatePlaybackIndicator(){
		final VideoView vv = ((VideoView)findViewById(R.id.playback));

		if (vv.isPlaying()){
			((ImageView)findViewById(R.id.playback_overlay)).setImageDrawable(null);
			setOsdText("");
		}else{
			((ImageView)findViewById(R.id.playback_overlay)).setImageResource(R.drawable.osd_play);
		}
	}

	private int mPlaybackPosition;
	private void prepareToPlayback(int position){
		prepareToPlayback(position, PLAYBACK_MODE_SINGLE);
	}

	private void prepareToPlayback(int position, int playbackMode){
		if (playbackMode == PLAYBACK_MODE_ALL_FIRST && (position == AdapterView.INVALID_POSITION || position == CAST_VIDEO_DONE)){
			position = 0;
		}
		mPlaybackMode = playbackMode;


		switch (getState()){
		case STATE_INITIAL:
		case STATE_RECORDER_STOPPED:
		case STATE_RECORDER_READY:{
			selectCastVideo(position);
			setState(STATE_PLAYBACK_PREPARING);
			mPlaybackPosition = position;

			new Thread(new Runnable() {

				@Override
				public void run() {
					stopPreview();
				}
			}).start();

			findViewById(R.id.playback_overlay).setVisibility(View.VISIBLE);
			// once the preview is stopped, the callback will continue with prepareToPlaybackPart2()

		}break;

		case STATE_PLAYBACK:{
			selectCastVideo(position);
			final VideoView playback = ((VideoView)findViewById(R.id.playback));
			playback.stopPlayback();
			loadPlaybackVideo(position);
		}break;
		default:
			throw new IllegalStateException("Not expecting state "+ getState());
		}
	}

	private final OnCompletionListener mPlaybackCompletionListener = new OnCompletionListener() {

		@Override
		public void onCompletion(MediaPlayer mp) {
			switch (mPlaybackMode){
			case PLAYBACK_MODE_SINGLE:
				updatePlaybackIndicator();
				break;

			case PLAYBACK_MODE_ALL:{
				final int next = getNextCastVideo(mPlaybackPosition);
				if (next == AdapterView.INVALID_POSITION){
					mPlaybackMode = PLAYBACK_MODE_ALL_FIRST;
					loadPlaybackVideo(0);
				}else{
					loadPlaybackVideo(next);
				}
			}break;
			}
		}
	};

	private final OnPreparedListener mPlaybackOnPreparedListener = new OnPreparedListener() {

		@Override
		public void onPrepared(MediaPlayer mp) {
			Log.d(TAG, "video prepared");

			setState(STATE_PLAYBACK);

			switch (mPlaybackMode){
				case PLAYBACK_MODE_ALL_FIRST:
					mPlaybackMode = PLAYBACK_MODE_ALL;
					break;

				case PLAYBACK_MODE_ALL:{
					mp.start();

				}break;
			}
			updatePlaybackIndicator();
		}
	};

	private void prepareToPlaybackPart2(){
		if (getState() != STATE_PLAYBACK_PREPARING){
			throw new IllegalStateException();
		}

		final VideoView playback = ((VideoView)findViewById(R.id.playback));
		playback.setOnCompletionListener(mPlaybackCompletionListener);

		playback.setOnPreparedListener(mPlaybackOnPreparedListener);

		final SurfaceView camera = ((SurfaceView)findViewById(R.id.camera));
		camera.setVisibility(View.GONE);
		playback.setVisibility(View.VISIBLE);
		updatePlaybackIndicator();

		loadPlaybackVideo(mPlaybackPosition);

	}

	/**
	 * Must call prepareToPlayback() first.
	 * @param position
	 */
	private void loadPlaybackVideo(int position){
		if (position < 0){
			throw new IllegalArgumentException("position must be valid");
		}

		mPlaybackPosition = position;

		selectCastVideo(position);

		mCastMediaCursor.moveToPosition(position);


		final VideoView playback = ((VideoView)findViewById(R.id.playback));

		if (!mCastMediaCursor.isNull(mLocUriCol)){
			final String castMediaUri = mCastMediaCursor.getString(mLocUriCol);
			playback.setVideoURI(Uri.parse(castMediaUri));

			// next is handled by onPrepared
			Log.d(TAG, "cast media uri is " + castMediaUri);
		}else{
			mStaticUiHandler.sendEmptyMessage(MSG_STATIC_SHOW);
			mStaticUiHandler.sendMessageDelayed(mStaticUiHandler.obtainMessage(MSG_STATIC_HIDE), 3000); //

		}
	}

	private void stopPlayback(){
		final int state = getState();
		if (state != STATE_PLAYBACK && state != STATE_PLAYBACK_PREPARING){
			throw new IllegalStateException();
		}
		showDialog(DIALOG_LOADING_CAMERA);
		mPlaybackMode = PLAYBACK_MODE_SINGLE;

		final VideoView playback = ((VideoView)findViewById(R.id.playback));
		if (playback.isPlaying()){
			playback.stopPlayback();
		}
		playback.setVideoURI(null);
		final SurfaceView camera = ((SurfaceView)findViewById(R.id.camera));
		camera.setVisibility(View.VISIBLE);
		playback.setVisibility(View.GONE);
		findViewById(R.id.playback_overlay).setVisibility(View.GONE);

	}


	public static final int
	MSG_STATIC_HIDE = 0,
	MSG_STATIC_SHOW = 1;

	private final Runnable startAnim = new Runnable() {

		@Override
		public void run() {
			final ImageView noise = (ImageView)findViewById(R.id.noise);
			((AnimationDrawable)noise.getDrawable()).start();
		}
	};

	private final Handler mStaticUiHandler = new Handler(){
		@Override
		public void handleMessage(android.os.Message msg) {
			switch (msg.what){
				case MSG_STATIC_SHOW:{
					findViewById(R.id.staticview).setVisibility(View.VISIBLE);
					final ImageView noise = (ImageView)findViewById(R.id.noise);
					noise.post(startAnim);
					setState(STATE_PLAYBACK);
				}break;

				case MSG_STATIC_HIDE:{
					findViewById(R.id.staticview).setVisibility(View.GONE);
					mPlaybackCompletionListener.onCompletion(null);
				}break;
			}
		};
	};


	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {

		if (adapter.getSelectedItemPosition() != position || mPlaybackMode != PLAYBACK_MODE_SINGLE){
			if (!hasCastVideo(position)){
				prepareToRecord(position);
			}else{
				prepareToPlayback(position);
			}
		}else{
			prepareToPlayback(position, PLAYBACK_MODE_ALL_FIRST);
		}

	}

	private final static int
		MSG_SHOT_SET_PROGRESS = 0,
		MSG_SHOT_START_SHOT = 1,
		MSG_SHOT_END_SHOT = 2,
		MSG_SHOT_CAST_MEDIA_UPDATED = 3;

	private final Handler shotHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
				case MSG_SHOT_END_SHOT:{
					mTimedRecording = null;
					stopRecorder();
					setState(STATE_RECORDER_STOPPED);
					saveCastVideo(msg.arg1, msg.arg2);
					// from here, the content observer will take over the next step.
				}break;

				case MSG_SHOT_START_SHOT:{
					mStoppable = msg.arg2 != 0;
					updateRecordingIndicator(true, mStoppable);

				}break;

				case MSG_SHOT_SET_PROGRESS:{
					mCastMediaProgressAdapter.updateProgress(msg.arg1, msg.arg2);
				}break;

				case MSG_SHOT_CAST_MEDIA_UPDATED:{
					if (mCastMediaCursor.getCount() > 0){
						if (getState() == STATE_RECORDER_STOPPED){
							prepareToRecordNext();
						}
					updateControls();
					}
				}break;
			}
		};
	};

	private void startTimedRecording(int shot){
		mTimedRecording = new TimedRecording(mShotListCursor, shotHandler, shot);
		new Thread(mTimedRecording).start();
	}

	private void stopRecordingCastVideo(){
		setState(STATE_RECORDER_STOPPING);
		if (mTimedRecording != null){
			mTimedRecording.stopRecording();
			mTimedRecording = null;
		}
	}

	private TimedRecording mTimedRecording = null;

	/**
	 * Handle a timed recording. This will update progress bars, send messages at the
	 * start and end of a shot to the s
	 *
	 * @author steve
	 *
	 */
	private class TimedRecording implements Runnable {
		private final Cursor mShotList;
		private final int mShotIndex;
		private final int maxTime;
		private final Handler mShotHandler;

		public TimedRecording(Cursor shotList, Handler shotHandler, int shotIndex) {
			mShotList = shotList;
			mShotIndex = shotIndex;
			mShotList.moveToPosition(mShotIndex);
			maxTime = mShotList.getInt(mShotList.getColumnIndex(ShotList._DURATION)) * 1000;
			mShotHandler = shotHandler;
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

			mShotHandler.obtainMessage(MSG_SHOT_START_SHOT, mShotIndex, stoppable ? 1 : 0).sendToTarget();

			int elapsedTime = 0;
			while(running){
				elapsedTime = (int)(System.currentTimeMillis() - startTime);
				mShotHandler.obtainMessage(MSG_SHOT_SET_PROGRESS, mShotIndex, elapsedTime).sendToTarget();

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
			mShotHandler.obtainMessage(MSG_SHOT_END_SHOT, mShotIndex, elapsedTime).sendToTarget();
		}
	}

	private void setOsdText(String osdText){
		mOsdSwitcher.setText(osdText);

	}

	public void onLocationChanged(Location location) {
		this.mLocation = location;
//		((LocationLink)findViewById(R.id.location)).setLocation(location);
	}

	public void onProviderDisabled(String provider) {}

	public void onProviderEnabled(String provider) {}

	public void onStatusChanged(String provider, int status, Bundle extras) {}


}