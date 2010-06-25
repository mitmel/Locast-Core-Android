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
/*
 * Some parts Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.ShotList;

public class TemplateActivity extends Activity implements OnClickListener {
	public final static String TAG = TemplateActivity.class.getSimpleName();
	public final static String ACTION_RECORD_TEMPLATED_VIDEO = "edu.mit.mobile.android.locast.ACTION_RECORD_TEMPLATED_VIDEO";

	private Camera mCamera;
	private SurfaceHolder mSurfaceHolder;
	private boolean mIsPreviewing = false;
	private MediaRecorder recorder;

	private TemplateAdapter templateAdapter; 
	private ListView lv;
	private ProgressBar progressBar;
	private final ListHandler listHandler = new ListHandler();


	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// run startPreview in the background to speed up creation
		final Thread startPreviewThread = new Thread(new Runnable() {
			
			public void run() {
				startPreview();
			}
		});
		startPreviewThread.start();
		
		setContentView(R.layout.template_main);

		final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera_view));

		// mSurfaceHolder is set from the callback so we can ensure
		// that we have one initialized.
		final SurfaceHolder holder = sv.getHolder();
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		holder.addCallback(cameraSHListener);
		
		// so we can tap to record.
		sv.setOnClickListener(this);


		recorder = new MediaRecorder();
		

		
		progressBar = (ProgressBar)findViewById(R.id.progress);
		
		lv = (ListView)findViewById(R.id.instructions_list);
		findViewById(R.id.list_overlay).setOnClickListener(new OnClickListener() { 
			public void onClick(View v) {
				final ViewSwitcher vs = (ViewSwitcher)findViewById(R.id.shot_list_switch);
				vs.setDisplayedChild(1);
				final Handler h = new Handler(){
					@Override
					public void handleMessage(Message msg) {
						vs.setDisplayedChild(0);
					}
				};
				final Thread t = new Thread(new Runnable() {
					
					public void run() {
						try {
							Thread.sleep(5000);
						} catch (final InterruptedException e) {
							// 
						}
						h.sendEmptyMessage(0);
					}
				});
				t.start();
			}
		});
		
		final Cursor c = getContentResolver().query(getIntent().getData(), ShotList.PROJECTION, null, null, null);
		startManagingCursor(c);
		if (c.getCount() >= 0){
			templateAdapter = new TemplateAdapter(this, c);
			lv.setAdapter(templateAdapter);
			((ListView)findViewById(R.id.instructions_list_full)).setAdapter(templateAdapter);
		}else{
			final ViewSwitcher vs = (ViewSwitcher)findViewById(R.id.shot_list_switch);
			vs.setVisibility(View.GONE);
		}
		lv.setEnabled(false);

		try {
			startPreviewThread.join();
		} catch (final InterruptedException e) {
			// ignore
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (!mIsPreviewing){
			startPreview();
		}
		/*
		if (mSurfaceHolder != null){
			initRecorder();
		}*/
		
		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		closeCamera();
	}
	
	@Override
	protected void onDestroy() {

		super.onDestroy();
	}
	
	private void startPreview(){
		if (mIsPreviewing){
			// already rolling...
			return;
		}
		if (mCamera == null){
			mCamera = Camera.open();
		}
		try {
			mCamera.setPreviewDisplay(mSurfaceHolder);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

        try {
    		mCamera.startPreview();
    		mIsPreviewing = true;
        } catch (final Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
        if (mSurfaceHolder != null){
        	unlockCamera();
        }
	}
	
	private void closeCamera(){
		if (mCamera != null){
			mIsPreviewing = false;
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			Log.d(TAG, "Camera has been shut down");
		}
	}
	
    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (final Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }


	private final SurfaceHolder.Callback cameraSHListener = new SurfaceHolder.Callback() {
		
		public void surfaceDestroyed(SurfaceHolder holder) {
			mSurfaceHolder = null;


		}

		public void surfaceCreated(SurfaceHolder holder) {
			mSurfaceHolder = holder;
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			if (mCamera == null){
				Log.d(TAG, "camera was null in surfaceChanged");
				return;
			}
			
			if (holder.isCreating()){
				setPreviewDisplay(holder);
				unlockCamera();
				Log.d(TAG, "surface changed set preview display");
				listHandler.sendEmptyMessage(MSG_INIT_RECORDER);
			}
			//mCamera.startPreview();


		}
	};
	
	
	
	public void unlockCamera(){
		// Call the unlock() method (introduced in API level 5) if possible
		// Otherwise just stop the preview to unlock.
		try {
			try {
				final Method unlock = mCamera.getClass().getDeclaredMethod("unlock");
				unlock.invoke(mCamera);

			}catch (final NoSuchMethodException m){
				// before API level 5, this seems to be the only way to unlock.
				mCamera.stopPreview();
			}
			
			Log.d(TAG, "unlocked camera");
			
		}catch (final InvocationTargetException ie){
			throw new RuntimeException(ie);

		} catch (final IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	private int videoCount = 0; 
	private void initRecorder(){
		Log.d(TAG, "initializing recorder...");
		try {

			recorder.setCamera(mCamera);
			recorder.setPreviewDisplay(mSurfaceHolder.getSurface());
			
			recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
			recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			
			recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			//recorder.setMaxDuration(5000); // XXX
			
			recorder.setVideoSize(320, 240);
			recorder.setVideoFrameRate(15);
			
			/*recorder.setVideoSize(720, 480); // N1-specific
			recorder.setVideoFrameRate(1000); */
			
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
			recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
			
			final File storage = Environment.getExternalStorageDirectory();
			
			if (!storage.canWrite()){
				// something's wrong; can't access SD card.
				throw new RuntimeException("cannot write to SD card");
			}
			
			final File locastBase = new File(storage, "locast");

			if (!locastBase.exists()){
				if (!locastBase.mkdirs()){
					throw new RuntimeException("could not make directory, "+locastBase+", for recording videos.");
				}
			}
			recorder.setOutputFile(locastBase + "video_"+ videoCount++ +".3gp");
			
			/*final Camera.Parameters params = camera.getParameters();
			params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
			params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
			params.setColorEffect(Camera.Parameters.EFFECT_NONE);*/
			//camera.setParameters(params);
			
			recorder.prepare();
		} catch (final IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private class TemplateRunnable implements Runnable {
		private final TemplateAdapter adapter;
		private final ListView listView;
		private boolean finished = false;
		private Boolean paused = false;

		public TemplateRunnable(ListView listView) {
			this.listView = listView;
			this.adapter = (TemplateAdapter)listView.getAdapter();
		}
		public synchronized void advanceToNextSection(){
			paused = false;
			notify();
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
		
		public void run() {
			final int count = adapter.getCount();
			
			int totalLength = 0;

			for (int section = 0; section < count; section++){
				totalLength += adapter.getTotalTime(section);
			}

			listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS_MAX, totalLength, 0));

			int curTime = 0;
			int segmentedTime = 0;
			int segmentTime = 0;
			
			for (int section = 0; section < count; section++){
				listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_SECTION, section, 0));
				// don't pause on the first one
				if (section > 0){
					pause();
				}
				listHandler.sendMessage(Message.obtain(listHandler, MSG_START_SECTION, section, 0));

				segmentTime = adapter.getTotalTime(section);
				for (int remainingTime = segmentTime; remainingTime >= 0; remainingTime -= 100){
					curTime += 100;
					
					listHandler.sendMessage(Message.obtain(listHandler, MSG_UPDATE_TIME, section, remainingTime));
					listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS, segmentedTime, curTime));

					try {
						Thread.sleep(100);
					} catch (final InterruptedException e) {
						break;
					}
				}
				segmentedTime += segmentTime;
				listHandler.sendMessage(Message.obtain(listHandler, MSG_END_SECTION, section, 0));
				
			}
			listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS, totalLength, totalLength));
			finished = true;
		}
	}

	private static String[] TEMPLATE_FROM = {ShotList._DIRECTION, ShotList._DURATION};
	private static int[]    TEMPLATE_TO   = {android.R.id.text1, R.id.time_remaining};
	
	private class TemplateAdapter extends SimpleCursorAdapter {
		private final NumberFormat timeFormat  = NumberFormat.getInstance();

		public TemplateAdapter(Context context, Cursor c) {
			super(context, R.layout.template_item, c, TEMPLATE_FROM, TEMPLATE_TO);
			setStringConversionColumn(c.getColumnIndex(ShotList._DIRECTION));
			
			timeFormat.setMinimumFractionDigits(1);
			timeFormat.setMaximumFractionDigits(1);
			
			
		}
		
		@Override
		public void setViewText(TextView v, String text) {
			switch (v.getId()){
			case R.id.time_remaining:
				//v.setText(timeFormat.format(Integer.valueOf(text)/.0));
				//break;
				default:
					super.setViewText(v, text);
			}
		}
		
		public int getTotalTime(int position){
			final Cursor c = getCursor();
			c.moveToPosition(position);
			return c.getInt(c.getColumnIndex(ShotList._DURATION)) * 1000;
		}
	}

	TemplateRunnable templateRunnable;
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.camera_view:
			if (templateRunnable == null || templateRunnable.isFinished()){
				templateRunnable = new TemplateRunnable(lv);
				new Thread(templateRunnable).start();
			}else{
				templateRunnable.advanceToNextSection();
			}
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
		MSG_INIT_RECORDER = 6;

	private class ListHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch(msg.what){
			case MSG_SET_SECTION:
				lv.setSelectionFromTop(msg.arg1, 0);
				
				break;

			case MSG_UPDATE_TIME:
				//templateAdapter.setItemProperty(msg.arg1, "remainingtime", msg.arg2);
				break;
				
			case MSG_SET_PROGRESS:
				progressBar.setProgress(msg.arg1);
				progressBar.setSecondaryProgress(msg.arg2);
				break;
				
			case MSG_SET_PROGRESS_MAX:
				progressBar.setMax(msg.arg1);
				
				break;
				
			case MSG_START_SECTION:
				recorder.start();
				break;
				
			case MSG_END_SECTION:
				recorder.stop();
				//recorder.reset();
				Log.d("template", "recorder stopped and reset");
				//cameraSHListener.unlockCamera();
				Log.d("template", "waiting for camera to settle");
				
				initRecorder();
				
				break;
				
			case MSG_INIT_RECORDER:
				initRecorder();
				break;
			}
		}
	}
}