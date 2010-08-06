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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
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

public class TemplateActivity extends VideoRecorder implements OnClickListener, LocationListener {
	private static final String TAG = TemplateActivity.class.getSimpleName();
	public final static String ACTION_RECORD_TEMPLATED_VIDEO = "edu.mit.mobile.android.locast.ACTION_RECORD_TEMPLATED_VIDEO";

	// stateful
	private long instanceId;
	private ArrayList<CastMediaInProgress> mCastMediaInProgressList = new ArrayList<CastMediaInProgress>();

	// non-stateful
	private Uri projectUri;
	private TemplateAdapter templateAdapter;
	private TemplateAdapter fullTemplateAdapter;
	private ImageView mIndicator;
	private ListView lv;
	private ProgressBar progressBar;
	private ViewSwitcher shotlistSwitch;
	private String filePrefix;
	private boolean hasDoneFirstInit;

	private IncrementalLocator iloc;
	private Location location;

	private final static String
		RUNTIME_STATE_INSTANCE_ID = "edu.mit.mobile.android.locast.instanceid",
		RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS = "edu.mit.mobile.android.locast.cast_media_in_progress";

	private final static int
		SHOTLIST_SWITCH_MAIN = 0,
		SHOTLIST_SWITCH_FULL_LIST = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		if (savedInstanceState != null
				&& savedInstanceState.containsKey(RUNTIME_STATE_INSTANCE_ID)){
			instanceId = savedInstanceState.getLong(RUNTIME_STATE_INSTANCE_ID);
		}else{
			instanceId = System.currentTimeMillis();
		}

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		initialStartPreview();


		setContentView(R.layout.template_main);

		final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera_view));


		// mSurfaceHolder is set from the callback so we can ensure
		// that we have one initialized.
		initSurfaceHolder(sv.getHolder());


		progressBar = (ProgressBar)findViewById(R.id.progress);
		mIndicator = (ImageView)findViewById(R.id.indicator);
		shotlistSwitch = (ViewSwitcher)findViewById(R.id.shot_list_switch);
		((Button)findViewById(R.id.done)).setOnClickListener(this);

		lv = (ListView)findViewById(R.id.instructions_list);

		// so we can tap to record.
		mIndicator.setOnClickListener(this);


		findViewById(R.id.list_overlay).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				shotlistSwitch.setDisplayedChild(SHOTLIST_SWITCH_FULL_LIST);
			}
		});
		final Uri data = getIntent().getData();
		if (savedInstanceState != null
				&& savedInstanceState.containsKey(RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS)){
			mCastMediaInProgressList = savedInstanceState.getParcelableArrayList(RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS);
		}else{
			final Cursor c = managedQuery(data, ShotList.PROJECTION, null, null, null);
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
		//CursorJoiner j = new CursorJoiner(c, columnNamesLeft, cursorRight, columnNamesRight);
		templateAdapter = new TemplateAdapter(this, mCastMediaInProgressList, R.layout.template_item);
		lv.setAdapter(templateAdapter);
		fullTemplateAdapter = new TemplateAdapter(this, mCastMediaInProgressList, R.layout.template_item_full);
		((ListView)findViewById(R.id.instructions_list_full)).setAdapter(fullTemplateAdapter);

		lv.setEnabled(false);

		projectUri = MediaProvider.removeLastPathSegment(data);
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

		final List<String> path = data.getPathSegments();
		filePrefix = ListUtils.join(path, "-");

		hasDoneFirstInit = false;
		setRecorderStateHandler(new Handler(){
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what){
				case MSG_RECORDER_INITIALIZED:
					if (!hasDoneFirstInit){
						setOutputFilename(filePrefix + "-0-"+instanceId);
						prepareRecorder();
						hasDoneFirstInit = true;
						setIndicator(INDICATOR_RECORD);
						final Toast t = Toast.makeText(TemplateActivity.this, R.string.template_toast_start_record, Toast.LENGTH_LONG);
						final DisplayMetrics metrics = new DisplayMetrics();
						getWindowManager().getDefaultDisplay().getMetrics(metrics);
						// position it above the red circle.
						t.setGravity(Gravity.CENTER, 0, (int)(-50.0 * metrics.density));

						t.show();

					}

					break;
				}
			}
		});

		iloc = new IncrementalLocator(this);

		waitForInitialStartPreview();
	}

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

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putLong(RUNTIME_STATE_INSTANCE_ID, instanceId);
		outState.putParcelableArrayList(RUNTIME_STATE_CAST_MEDIA_IN_PROGRESS, mCastMediaInProgressList);
	}

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

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
	    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
	    	if (shotlistSwitch.getDisplayedChild() == SHOTLIST_SWITCH_FULL_LIST){
	    		shotlistSwitch.setDisplayedChild(SHOTLIST_SWITCH_MAIN);
	    		return true;
	    	}
	    }

	    return super.onKeyDown(keyCode, event);
	}

	private static final int
		DIALOG_CONFIRM_DELETE = 0,
		DIALOG_CONFIRM_RERECORD = 1;
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

	private void deleteCastVideo(int index){
		final CastMediaInProgress item = mCastMediaInProgressList.get(index);
		final File localUri = new File(item.localUri);
		if (localUri.delete()){
			item.localUri = null;
			fullTemplateAdapter.notifyDataSetChanged();
		}else{
			Toast.makeText(getApplicationContext(), R.string.template_delete_error, Toast.LENGTH_LONG).show();
		}
	}

	private void recordCastVideo(int index){

	}

	/**
	 * Saves this as new cast associated with a given project
	 */
	private void save(){
		final ContentResolver cr = getContentResolver();

		final ContentValues cast = new ContentValues();
		cast.put(Cast._PROJECT_ID, ContentUris.parseId(projectUri));
		cast.put(Cast._PRIVACY, Cast.PRIVACY_PUBLIC);
		cast.put(Cast._TITLE, ((EditText)findViewById(R.id.cast_title)).getText().toString());
		cast.put(Cast._AUTHOR, AndroidNetworkClient.getInstance(this).getUsername());

		if (location != null){
			cast.put(Cast._LATITUDE, location.getLatitude());
			cast.put(Cast._LONGITUDE, location.getLongitude());
		}

		final Uri castUri = cr.insert(Uri.withAppendedPath(projectUri, Cast.PATH), cast);
		Log.d(TAG, "created cast "+ castUri);
		final Uri castMediaUri = Uri.withAppendedPath(castUri, CastMedia.PATH);

		final ContentValues[] castMedia = new ContentValues[mCastMediaInProgressList.size()];
		for (int i = 0; i < castMedia.length; i++){
			final CastMediaInProgress item = mCastMediaInProgressList.get(i);
			castMedia[i] = new ContentValues();
			castMedia[i].put(CastMedia._LIST_IDX, i);
			castMedia[i].put(CastMedia._LOCAL_URI, item.localUri);
			castMedia[i].put(CastMedia._DURATION, item.duration);
			castMedia[i].put(CastMedia._MIME_TYPE, "video/3gpp");
		}
		final int inserts = cr.bulkInsert(castMediaUri, castMedia);
		if (inserts == castMedia.length){
			Toast.makeText(this, "Cast saved.", Toast.LENGTH_LONG).show();
			finish();
		}else{
			Toast.makeText(this, "Error saving cast videos. Only saved "+inserts, Toast.LENGTH_LONG).show();
		}
	}

	private class TemplateRunnable implements Runnable {
		private final TemplateAdapter adapter;
		private final ListView listView;
		private boolean finished = false;
		private Boolean paused = false;

		private boolean stoppable = false;
		private boolean stop = false;
		private Integer section = 0;

		public TemplateRunnable(ListView listView) {
			this.listView = listView;
			this.adapter = (TemplateAdapter)listView.getAdapter();
		}
		public synchronized void advanceToNextSection(){
			paused = false;
			notify();
		}

		public void recordSection(int index){
			synchronized(section){
				section = index;
			}

		}

		public boolean isFinished(){
			return finished;
		}

		synchronized private void pause(){
			paused = true;
			while(paused){
				try {
					wait();
				} catch (final InterruptedException e) {break;}
			}
		}

		synchronized public void stop(){
			this.stop = true;
		}

		synchronized public boolean isStoppable(){
			return stoppable;
		}

		public void run() {
			final int count = adapter.getCount();

			int totalLength = 0;

			for (int section = 0; section < count; section++){
				totalLength += adapter.getTotalTime(section);
			}

			listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS_MAX, totalLength, 0));

			int totalTime = 0;     // running count of total time
			int segmentedTime = 0; // total time, broken up into segment chunks

			boolean isFirstSegment = true;

			for (int segment = 0; segment < count; segment++){
				final CastMediaInProgress item = adapter.getItem(segment);
				if (item.localUri != null){
					totalTime += item.elapsedDuration * 1000;
					segmentedTime += item.elapsedDuration * 1000;
					// skip over already recorded sections
					continue;
				}
				listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_SECTION, segment, count));
				// don't pause on the first one
				if (! isFirstSegment){
					pause();
				}
				isFirstSegment = false;
				listHandler.sendMessage(Message.obtain(listHandler, MSG_START_SECTION, segment, count));

				int segmentTime = 0;   // time for the given segment
				segmentTime = adapter.getTotalTime(segment);
				stoppable = (segmentTime == 0); // this allows for recording a segment of unlimited length. call stop() to stop
				int elapsedTime = 0;
				for (elapsedTime = 0; (stoppable && !stop) || elapsedTime <= segmentTime; elapsedTime += 100){
					totalTime += 100;

					if (elapsedTime % 1000 == 0){
						listHandler.sendMessage(Message.obtain(listHandler, MSG_UPDATE_TIME, segment, elapsedTime/1000));
					}

					listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS, segmentedTime, totalTime));

					try {
						Thread.sleep(100);
					} catch (final InterruptedException e) {
						break;
					}
				}
				segmentedTime += elapsedTime;
				listHandler.sendMessage(Message.obtain(listHandler, MSG_END_SECTION, segment, count));

			}
			listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS, totalLength, totalLength));
			finished = true;
			listHandler.sendEmptyMessage(MSG_FINISHED_LAST_SECTION);
		}
	}

	private class TemplateAdapter extends ArrayAdapter<CastMediaInProgress> {
		private final NumberFormat timeFormat  = NumberFormat.getInstance();
		private final int mItemLayout;

		public TemplateAdapter(Context context, List<CastMediaInProgress> array, int itemLayout) {
			super(context, R.layout.template_item, array);
			this.mItemLayout = itemLayout;

			timeFormat.setMinimumFractionDigits(1);
			timeFormat.setMaximumFractionDigits(1);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final CastMediaInProgress item = getItem(position);

			if (convertView == null){
				convertView = getLayoutInflater().inflate(mItemLayout, parent, false);
			}

			((TextView)(convertView.findViewById(R.id.template_item_numeral))).setText(item.index + 1 + ".");
			((TextView)(convertView.findViewById(android.R.id.text1))).setText(item.direction);
			final int shownSeconds = Math.abs(item.duration - item.elapsedDuration);
			final String secondsString = (shownSeconds == 0 && item.duration == 0) ? "∞" :  Integer.toString(shownSeconds)+"s";

			((TextView)(convertView.findViewById(R.id.time_remaining))).setText(secondsString);

			if (mItemLayout == R.layout.template_item_full){
				final Button delete = (Button)convertView.findViewById(R.id.delete);
				delete.setTag(position);
				delete.setOnClickListener(TemplateActivity.this);
				delete.setVisibility(item.localUri != null ? View.VISIBLE : View.GONE);
			}
			return convertView;
		}

		public int getTotalTime(int position){
			final CastMediaInProgress item = getItem(position);

			return item.duration * 1000;
		}
	}

	TemplateRunnable templateRunnable;


	public void onClick(View v) {
		switch (v.getId()){
		case R.id.indicator:
			if (templateRunnable == null){
				templateRunnable = new TemplateRunnable(lv);
				new Thread(templateRunnable).start();

			}else if (templateRunnable.isStoppable() && mIndicatorState == INDICATOR_STOP){
				templateRunnable.stop();

			}else if(! templateRunnable.isFinished()){

				templateRunnable.advanceToNextSection();
			}else{
				// we've finished recording, but the user tapped the screen, probably accidentally. Ignore them.
			}
			break;
		case R.id.delete:
			whichToDelete = (Integer)v.getTag();
			showDialog(DIALOG_CONFIRM_DELETE);
			break;

		case R.id.done:
			save();
			break;

		case R.id.cancel:
			break;
		}
	}

	private final static int
		MSG_SET_SECTION = 0,
		MSG_UPDATE_TIME = 1,
		MSG_SET_PROGRESS = 2,
		MSG_SET_PROGRESS_MAX = 3,
		MSG_START_SECTION = 4,
		MSG_END_SECTION = 5,
		MSG_FINISHED_LAST_SECTION = 6;

	private final Handler listHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch(msg.what){
			case MSG_SET_SECTION:
				lv.setSelectionFromTop(msg.arg1, 0);

				break;

			case MSG_UPDATE_TIME:
				//templateAdapter.setItemProperty(msg.arg1, "remainingtime", msg.arg2);
				mCastMediaInProgressList.get(msg.arg1).elapsedDuration = msg.arg2;
				templateAdapter.notifyDataSetChanged();

				break;

			case MSG_SET_PROGRESS:
				progressBar.setProgress(msg.arg1);
				progressBar.setSecondaryProgress(msg.arg2);
				break;

			case MSG_SET_PROGRESS_MAX:
				progressBar.setMax(msg.arg1);

				break;

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
				final CastMediaInProgress inProgressItem = mCastMediaInProgressList.get(msg.arg1);
				inProgressItem.localUri = getFullOutputFilename();
				fullTemplateAdapter.notifyDataSetChanged();
				Log.d(TAG, "Video recorded to " + getFullOutputFilename());

				if (msg.arg1 < msg.arg2 - 1){
					setIndicator(INDICATOR_RECORD_PAUSE);
					initRecorder();
					setOutputFilename(filePrefix + "-" + (msg.arg1 + 1) +"-"+instanceId);
					prepareRecorder();
				}

			}break;

			case MSG_FINISHED_LAST_SECTION:{
				setIndicator(INDICATOR_NONE);
				shotlistSwitch.setDisplayedChild(SHOTLIST_SWITCH_FULL_LIST);
			}
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

	private static class CastMediaInProgress implements Parcelable {
		public CastMediaInProgress(String direction, int duration, int index){
			this.duration = duration;
			this.direction = direction;
			this.index = index;
		}
		protected String localUri = null;
		protected int duration = 0;
		protected int elapsedDuration = 0;
		protected String direction = null;
		protected int index = 0;

		public int describeContents() {
			return 0;
		}

		public CastMediaInProgress(Parcel p){
			localUri = p.readString();
			duration = p.readInt();
			elapsedDuration = p.readInt();
			direction = p.readString();
			index = p.readInt();
		}
		public void writeToParcel(Parcel dest, int flags) {

			dest.writeString(localUri);
			dest.writeInt(duration);
			dest.writeInt(elapsedDuration);
			dest.writeString(direction);
			dest.writeInt(index);
		}

		public static final Parcelable.Creator<CastMediaInProgress> CREATOR
			= new Creator<CastMediaInProgress>() {

				public CastMediaInProgress[] newArray(int size) {
					return new CastMediaInProgress[size];
				}

				public CastMediaInProgress createFromParcel(Parcel source) {
					return new CastMediaInProgress(source);
				}
			};
	}
}