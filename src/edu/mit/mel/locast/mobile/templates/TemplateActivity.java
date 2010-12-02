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
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import edu.mit.mel.locast.mobile.IncrementalLocator;
import edu.mit.mel.locast.mobile.ListUtils;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.CastMedia;
import edu.mit.mel.locast.mobile.data.MediaProvider;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.ShotList;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.widget.LocationLink;
import edu.mit.mel.locast.mobile.widget.SegmentedProgressBar;

// XXX left off loading cast videos from a saved cast
public class TemplateActivity extends VideoRecorder implements OnClickListener, LocationListener {
	private static final String TAG = TemplateActivity.class.getSimpleName();
	public final static String ACTION_RECORD_TEMPLATED_VIDEO = "edu.mit.mobile.android.locast.ACTION_RECORD_TEMPLATED_VIDEO";

	// stateful
	private long instanceId;
	private ArrayList<CastMediaInProgress> mCastMediaInProgressList = new ArrayList<CastMediaInProgress>();
	private int currentCastVideo;
	private boolean isDraft;
	private Uri currentCast;

	// non-stateful
	private Uri projectUri;
	private String publicProjectUri;
	private TemplateAdapter templateAdapter;
	private TemplateAdapter fullTemplateAdapter;
	private ImageView mIndicator;
	private ListView lv;
	private SegmentedProgressBar progressBar;
	private ViewSwitcher shotlistSwitch;
	private String filePrefix;
	private boolean hasDoneFirstInit;
	private int totalDuration;

	private IncrementalLocator iloc;
	private Location location;

	private final static String
		RUNTIME_STATE_INSTANCE_ID = "edu.mit.mobile.android.locast.instanceid",
		RUNTIME_STATE_CURRENT_CAST = "edu.mit.mobile.android.locast.current_cast",
		RUNTIME_STATE_CURRENT_CAST_VIDEO = "edu.mit.mobile.android.locast.current_cast_video",
		RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS = "edu.mit.mobile.android.locast.cast_media_in_progress";

	private final static int
		SHOTLIST_SWITCH_MAIN = 0,
		SHOTLIST_SWITCH_FULL_LIST = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		initialStartPreview();

		setContentView(R.layout.template_main);

		final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera_view));

		showDialog(DIALOG_LOADING);

		// mSurfaceHolder is set from the callback so we can ensure
		// that we have one initialized.
		initSurfaceHolder(sv.getHolder());

		final EditText castTitle = ((EditText)findViewById(R.id.cast_title));
		castTitle.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				castTitle.setError(null);
			}

			@Override
			public void afterTextChanged(Editable s) {}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}

		});

		progressBar = (SegmentedProgressBar)findViewById(R.id.progress);
		mIndicator = (ImageView)findViewById(R.id.indicator);
		shotlistSwitch = (ViewSwitcher)findViewById(R.id.shot_list_switch);
		((Button)findViewById(R.id.done)).setOnClickListener(this);
		((Button)findViewById(R.id.preview)).setOnClickListener(this);

		lv = (ListView)findViewById(R.id.instructions_list);

		// so we can tap to record.
		mIndicator.setOnClickListener(this);

		findViewById(R.id.list_overlay).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showFullList();
			}
		});

		// restore state
		final Uri data = getIntent().getData();
		if (savedInstanceState == null){
			savedInstanceState = new Bundle();
		}

		instanceId = savedInstanceState.getLong(RUNTIME_STATE_INSTANCE_ID, System.currentTimeMillis());
		isDraft = true;
		currentCast = savedInstanceState.<Uri>getParcelable(RUNTIME_STATE_CURRENT_CAST);
		currentCastVideo = savedInstanceState.getInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, 0);
		if (savedInstanceState.containsKey(RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS)){
			mCastMediaInProgressList = savedInstanceState.getParcelableArrayList(RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS);
		}else{
			final String type = getContentResolver().getType(data);
			if (MediaProvider.TYPE_SHOTLIST_DIR.equals(type)){
				projectUri = ShotList.getProjectUri(data);
				loadShotList(data);

			}else if (MediaProvider.TYPE_PROJECT_CAST_ITEM.equals(type)){

				projectUri = Cast.getProjectUri(loadCast(data));
				loadShotList(Project.getShotListUri(projectUri));

			}else{
				throw new IllegalArgumentException("Don't know how to handle URI: " + data);
			}
		}

		totalDuration = 0;
		for (final CastMediaInProgress cmip: mCastMediaInProgressList){
			progressBar.addSegment(cmip.duration * 1000);
			totalDuration += cmip.duration * 1000;
		}
		//progressBar.setMax(totalDuration);

		templateAdapter = new TemplateAdapter(this, this, mCastMediaInProgressList, R.layout.template_item);
		lv.setAdapter(templateAdapter);
		fullTemplateAdapter = new TemplateAdapter(this, this, mCastMediaInProgressList, R.layout.template_item_full);
		final ListView instructionListFull = ((ListView)findViewById(R.id.instructions_list_full));
		instructionListFull.setAdapter(fullTemplateAdapter);
		instructionListFull.setOnItemClickListener(new ListView.OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final CastMediaInProgress castMedia = (CastMediaInProgress) parent.getItemAtPosition(position);
				if (castMedia.localUri != null){
					final Intent viewCastPart = new Intent(Intent.ACTION_VIEW, null, getApplicationContext(), TemplatePlayer.class);
					viewCastPart.setDataAndType(castMedia.localUri, "video/3gpp");
					startActivity(viewCastPart);
				}else{
					hideFullList();
					prepareToRecord(position);
					recordCastVideo(position);
				}
			}
		});

		lv.setEnabled(false);


		final Cursor parent = managedQuery(projectUri, Project.PROJECTION, null, null, null);
		if (parent.getCount() > 1){
			Log.w(TAG, "got more than one project for " + projectUri);
		}else if (parent.getCount() == 0){
			Log.e(TAG, "did not find a project at "+ projectUri);
			Toast.makeText(this, "Error loading project", Toast.LENGTH_LONG);
			finish();
		}
		parent.moveToFirst();
		((TextView)findViewById(android.R.id.title)).setText(parent.getString(parent.getColumnIndex(Project._TITLE)));
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
		hasDoneFirstInit = false;
		iloc.removeLocationUpdates(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		iloc.requestLocationUpdates(this);
	}

	private void loadShotList(Uri shotlist){
		final Cursor c = managedQuery(shotlist, ShotList.PROJECTION, null, null, null);
		final int directionColumn = c.getColumnIndex(ShotList._DIRECTION);
		final int durationColumn = c.getColumnIndex(ShotList._DURATION);
		final int idxColumn = c.getColumnIndex(ShotList._LIST_IDX);

		if (c.getCount() > 0){
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
				mCastMediaInProgressList.add(new CastMediaInProgress(c.getString(directionColumn), c.getInt(durationColumn), c.getInt(idxColumn)));
			}
		}else{
			mCastMediaInProgressList.add(new CastMediaInProgress(getString(R.string.template_default_direction), 0, 0));

		}
		c.close();

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putLong(RUNTIME_STATE_INSTANCE_ID, instanceId);
		outState.putInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, currentCastVideo);
		outState.putParcelableArrayList(RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS, mCastMediaInProgressList);
		outState.putParcelable(RUNTIME_STATE_CURRENT_CAST, currentCast);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
	    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
	    	if (hideFullList()){
	    		return true;
	    	}
	    }

	    return super.onKeyDown(keyCode, event);
	}

	/******************** dialogs ************************/

	private static final int
		DIALOG_CONFIRM_DELETE = 0,
		DIALOG_CONFIRM_RERECORD = 1,
		DIALOG_LOADING = 2;
	// this is to work around the missing bundle API introduced in API level 5
	private int whichToDelete = -1;

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

	/**
	 * Hides the full shot list.
	 * @return true if the list was hidden.
	 */
	private boolean hideFullList(){
    	if (shotlistSwitch.getDisplayedChild() == SHOTLIST_SWITCH_FULL_LIST){
    		shotlistSwitch.setDisplayedChild(SHOTLIST_SWITCH_MAIN);
    		return true;
    	}else{
    		return false;
    	}
	}

	/**
	 * Shows the full shot list.
	 * @return true if the list was shown.
	 */
	private boolean showFullList(){
    	if (shotlistSwitch.getDisplayedChild() == SHOTLIST_SWITCH_MAIN){
    		shotlistSwitch.setDisplayedChild(SHOTLIST_SWITCH_FULL_LIST);
    		return true;
    	}else{
    		return false;
    	}
	}

	private void showRecordHelp(){
		final Toast t = Toast.makeText(TemplateActivity.this, R.string.template_toast_start_record, Toast.LENGTH_LONG);
		final DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		// position it above the red circle.
		t.setGravity(Gravity.CENTER, 0, (int)(-50.0 * metrics.density));

		t.show();
	}

	/************ indicator *******************/
	private static final int
		INDICATOR_NONE = 0,
		INDICATOR_RECORD = 1,
		INDICATOR_RECORD_PAUSE = 2,
		INDICATOR_STOP = 3;

	private int mIndicatorState = 0;

	private static final RelativeLayout.LayoutParams osdLayoutCenter = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	private static final RelativeLayout.LayoutParams osdLayoutLeft = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

	static {
		osdLayoutLeft.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		osdLayoutLeft.addRule(RelativeLayout.CENTER_VERTICAL);

		osdLayoutCenter.addRule(RelativeLayout.CENTER_IN_PARENT);
	}
	private void setIndicator(int indicator){

		if(mIndicatorState == INDICATOR_STOP && indicator != INDICATOR_STOP){
			mIndicator.setLayoutParams(osdLayoutCenter);
		}

		switch (indicator){
		case INDICATOR_RECORD:
			mIndicator.setImageResource(R.drawable.osd_record);
			mIndicator.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in));
		break;

		case INDICATOR_RECORD_PAUSE:
			mIndicator.setImageResource(R.drawable.osd_record_pause);
			break;

		case INDICATOR_STOP:
			mIndicator.setImageResource(R.drawable.osd_stop);
			mIndicator.setLayoutParams(osdLayoutLeft);
			mIndicator.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in));
			break;

		case INDICATOR_NONE:
			if (mIndicatorState != INDICATOR_NONE){
				mIndicator.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_out));
				mIndicator.setVisibility(View.GONE);
			}
		}
		if (mIndicatorState == INDICATOR_NONE && indicator != INDICATOR_NONE){
			mIndicator.setVisibility(View.VISIBLE);

		}
		mIndicatorState = indicator;
	}

	/*********************** cast video manipulators *************************/

	public static final int CAST_VIDEO_DONE = -1;

	private int getNextCastVideo(){
		for (final CastMediaInProgress castMedia : mCastMediaInProgressList){
			if (castMedia.localUri == null){
				return castMedia.index;
			}
		}
		return -1;
	}

	private void deleteCastVideo(int index){
		final CastMediaInProgress item = mCastMediaInProgressList.get(index);
		// TODO check to ensure that it's a file:/// uri
		final File localUri = new File(item.localUri.getPath());
		if (localUri.delete()){
			item.localUri = null;
			fullTemplateAdapter.notifyDataSetChanged();
		}else{
			Toast.makeText(getApplicationContext(), R.string.template_delete_error, Toast.LENGTH_LONG).show();
		}
	}

	private void prepareToRecord(int index){
		templateRunnable = null;
		listHandler.obtainMessage(MSG_SET_SECTION, index, 0).sendToTarget();
		setIndicator(INDICATOR_RECORD_PAUSE);
		initRecorder();
		setOutputFilename(filePrefix + "-" + (index + 1) +"-"+instanceId);
		prepareRecorder();
	}


	private void recordCastVideo(int index){
		if (templateRunnable == null){
			templateRunnable = new TemplateRunnable(templateAdapter, index);
			currentCastVideo = index;
			new Thread(templateRunnable).start();
		}else{
			Log.e(TAG, "requested to start recording when already recording");
		}
	}

	private void stopRecording(){
		if (templateRunnable != null){
			templateRunnable.stop();
			templateRunnable = null;
		}
	}

	private void saveCastVideo(){

		final CastMediaInProgress inProgressItem = mCastMediaInProgressList.get(currentCastVideo);
		inProgressItem.localUri = Uri.fromFile(getFullOutputFile());
		fullTemplateAdapter.notifyDataSetChanged();
		Log.d(TAG, "Video recorded to " + getFullOutputFile());
	}

	private boolean isRecording(){
		return templateRunnable != null;
	}

	/**
	 * Records the next needed video.
	 *
	 * @return true if a video could be recorded.
	 */
	private boolean recordCastVideo(){
		final int nextIndex = getNextCastVideo();
		if (nextIndex == CAST_VIDEO_DONE){
			return false;
		}else{
			recordCastVideo(nextIndex);
			return true;
		}
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
		final Uri castMediaUri = Cast.getCastMediaUri(currentCast);
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
			Log.d(TAG, "created cast "+ currentCast);
		}else{
			cr.update(currentCast, cast, null, null);
			Log.d(TAG, "updating cast "+ currentCast);
		}

		final Uri castMediaUri = Cast.getCastMediaUri(currentCast);

		final ContentValues[] castMedia = new ContentValues[mCastMediaInProgressList.size()];
		for (int i = 0; i < castMedia.length; i++){
			final CastMediaInProgress item = mCastMediaInProgressList.get(i);
			castMedia[i] = new ContentValues();
			castMedia[i].put(CastMedia._LIST_IDX, i);
			castMedia[i].put(CastMedia._LOCAL_URI, item.localUri != null ? item.localUri.toString() : null);
			castMedia[i].put(CastMedia._DURATION, item.duration);
			castMedia[i].put(CastMedia._MIME_TYPE, item.localUri != null ? "video/3gpp": null);
		}
		final int inserts = cr.bulkInsert(castMediaUri, castMedia);
		if (inserts == castMedia.length){
			Toast.makeText(this, "Cast saved"+(isDraft ? " as draft" : "")+".", Toast.LENGTH_LONG).show();
		}else{
			Toast.makeText(this, "Error saving cast videos. Only saved "+inserts, Toast.LENGTH_LONG).show();
		}
		return currentCast;
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
				if (!hasDoneFirstInit){
					final int nextId = getNextCastVideo();
					if (nextId < 0){
						// done!
						return;
					}
					prepareToRecord(nextId);
					hasDoneFirstInit = true;
					if (nextId == 0){
						showRecordHelp();
					}
				}

				break;
			}
		}
	};

	/**
	 * Handles the timing of
	 * @author steve
	 *
	 */
	private class TemplateRunnable implements Runnable {
		private final TemplateAdapter adapter;

		private boolean stoppable = false;
		private boolean stop = false;
		private final int section;

		public TemplateRunnable(TemplateAdapter adapter, int section) {
			this.adapter = adapter;
			this.section = section;
		}

		synchronized public void stop(){
			this.stop = true;
		}

		synchronized public boolean isStoppable(){
			return stoppable;
		}

		public void run() {
			final int count = adapter.getCount();

			listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_SECTION, section, count));

			listHandler.sendMessage(Message.obtain(listHandler, MSG_START_SECTION, section, count));

			int segmentTime = 0;   // time for the given segment
			segmentTime = adapter.getTotalTime(section);
			stoppable = (segmentTime == 0); // this allows for recording a segment of unlimited length. call stop() to stop
			int elapsedTime = 0;
			for (elapsedTime = 0; (stoppable && !stop) || elapsedTime <= segmentTime; elapsedTime += 100){

				if (elapsedTime % 1000 == 0){
					listHandler.sendMessage(Message.obtain(listHandler, MSG_UPDATE_TIME, section, elapsedTime/1000));
				}

				listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS, section, elapsedTime));

				try {
					Thread.sleep(100);
				} catch (final InterruptedException e) {
					break;
				}
			}
			listHandler.sendMessage(Message.obtain(listHandler, MSG_END_SECTION, section, count));


		}
	}

	private TemplateRunnable templateRunnable;

	public void onClick(View v) {
		switch (v.getId()){
		case R.id.indicator:

			if (! isRecording()){
				recordCastVideo();

			}else if (mIndicatorState == INDICATOR_STOP){
				stopRecording();
			}
			break;

		case R.id.delete:
			whichToDelete = (Integer)v.getTag();
			showDialog(DIALOG_CONFIRM_DELETE);
			break;

		case R.id.done:{
			final EditText title =((EditText)findViewById(R.id.cast_title));
			if (title.getText().toString().trim().length() == 0){
				title.setError("Please enter a title"); // XXX i18n
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

		case R.id.preview:{
			isDraft = true;
			final Uri cast = save();
			final Intent previewCast = new Intent(Intent.ACTION_VIEW, null, getApplicationContext(), TemplatePlayer.class);
			previewCast.setData(cast);
			startActivity(previewCast);
		}break;

		case R.id.cancel:
			break;
		}
	}

	private final static int
		MSG_SET_SECTION = 0,
		MSG_UPDATE_TIME = 1,
		MSG_SET_PROGRESS = 2,
		MSG_START_SECTION = 4,
		MSG_END_SECTION = 5;

	private final Handler listHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch(msg.what){
				case MSG_SET_SECTION:
					lv.setSelectionFromTop(msg.arg1, 0);

					break;

				case MSG_UPDATE_TIME:
					mCastMediaInProgressList.get(msg.arg1).elapsedDuration = msg.arg2;
					templateAdapter.notifyDataSetChanged();

					break;

				case MSG_SET_PROGRESS:{
					progressBar.setProgress(msg.arg1, msg.arg2);
				}break;

				case MSG_START_SECTION:{
					if (templateRunnable.isStoppable()){
						setIndicator(INDICATOR_STOP);
					}else{
						setIndicator(INDICATOR_NONE);
					}
					startRecorder();
				} break;

				case MSG_END_SECTION:{
					stopRecorder();
					saveCastVideo();

					final int nextSection = getNextCastVideo();

					if (nextSection == -1){
						setIndicator(INDICATOR_NONE);
						showFullList();
					}else{
						prepareToRecord(nextSection);
					}

				}break;
			}
		}
	};

	public void onLocationChanged(Location location) {
		this.location = location;
		((LocationLink)findViewById(R.id.location)).setLocation(location);
	}

	public void onProviderDisabled(String provider) {}

	public void onProviderEnabled(String provider) {}

	public void onStatusChanged(String provider, int status, Bundle extras) {}

}