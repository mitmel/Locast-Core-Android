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

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

public abstract class VideoRecorder extends Activity {

	private static final String TAG = VideoRecorder.class.getSimpleName();
	private Camera mCamera;
	private SurfaceHolder mSurfaceHolder;
	private boolean mIsPreviewing = false;
	private MediaRecorder recorder = null;

	private static final int
		MSG_INIT_RECORDER  = 1,
		MSG_START_RECORD   = 2,
		MSG_PREPARE_RECORD = 3;

	private final Handler mHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
			case MSG_INIT_RECORDER:
				initRecorder();
				break;

			case MSG_START_RECORD:
				startRecorderActual();
				break;

			case MSG_PREPARE_RECORD:
				prepareRecorderActual();
				break;
			}
		}
	};

	private Handler recorderStateHandler;

	public final static int MSG_RECORDER_INITIALIZED = 100,
							MSG_RECORDER_SHUTDOWN = 101;
	public void setRecorderStateHandler(Handler recorderStateHandler) {
		this.recorderStateHandler = recorderStateHandler;
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
				//unlockCamera();
				Log.d(TAG, "surface changed set preview display");
				mHandler.sendEmptyMessage(MSG_INIT_RECORDER);
			}
			//mCamera.startPreview();


		}
	};


	private Thread startPreviewThread;

	public void initialStartPreview(){
		startPreviewThread = new Thread(new Runnable() {

				public void run() {
					startPreview();
				}
			});
		startPreviewThread.start();
	}

	public void waitForInitialStartPreview(){
		try {
			startPreviewThread.join();

		} catch (final InterruptedException e) {
			// ignore
		}
	}

	public void initSurfaceHolder(SurfaceHolder holder){
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		holder.addCallback(cameraSHListener);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!mIsPreviewing){
			startPreview();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		//mHandler.sendEmptyMessage(MSG_CLOSE_CAMERA);

		closeCamera();
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
	}

	/**
	 * Parameters must be set for the camera to the same settings as the media recorder.
	 */
	private void setCameraParameters(){

		final Camera.Parameters params = mCamera.getParameters();
		Log.i(TAG, "Build: "+ Build.MODEL);

		if (Build.MODEL.equals("Milestone")){
			params.setPreviewSize(640, 480);
			params.setPreviewFrameRate(15);

		}else if (Build.MODEL.equals("Nexus One")) {
			// verified good 2010-07-06; API level 8
			params.setPreviewSize(720, 480);
			params.setPreviewFrameRate(15);

		}else{ // for all other devices, fall back on a well-known default.
			// CIF size, Android 1.6; "high quality" on the G1.
			params.setPreviewSize(352, 288);
			params.setPreviewFrameRate(10);
		}

		mCamera.setParameters(params);
	}

	protected void startPreview() {
		if (mIsPreviewing){
			// already rolling...
			return;
		}
		if (mCamera == null){
			mCamera = Camera.open();
		}


		try {
			lockCamera();

			setCameraParameters();

			mCamera.setPreviewDisplay(mSurfaceHolder);
		} catch (final IOException e) {
			alertFailSetup(e);
		}

	    try {
			mCamera.startPreview();
			mIsPreviewing = true;
	    } catch (final Throwable ex) {
	    	alertFailSetup(ex);
	    	return;
	    }
	    if (mSurfaceHolder != null){
	    	unlockCamera();
	    }
	}

	private void closeCamera() {
		if (mCamera == null){
			return;
		}
		recorder.release();
		recorder = null;
		Log.d(TAG, "closing camera");
		lockCamera();
		//mCamera.stopPreview();
		Log.d(TAG, "preview stopped");
		//
		mCamera.release();
		mIsPreviewing = false;
		mCamera = null;
		Log.d(TAG, "Camera has been shut down");

	}

	private void setPreviewDisplay(SurfaceHolder holder) {
	    try {
	        mCamera.setPreviewDisplay(holder);
	    } catch (final Throwable ex) {
	        closeCamera();
	        throw new RuntimeException("setPreviewDisplay failed", ex);
	    }
	}

	private void lockCamera() {
		// Call the unlock() method (introduced in API level 5) if possible
		// Otherwise just stop the preview to unlock.
		try {
			try {
				final Method lock = mCamera.getClass().getDeclaredMethod("lock");
				lock.invoke(mCamera);

			}catch (final NoSuchMethodException m){
				// before API level 5, this seems to be the only way to unlock.
				//mCamera.stopPreview();
			}

			Log.d(TAG, "locked camera");
			isLocked = true;

		}catch (final InvocationTargetException ie){
			throw new RuntimeException(ie.getTargetException());

		} catch (final IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private void unlockCamera() {
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
			isLocked = false;

		}catch (final InvocationTargetException ie){
			throw new RuntimeException(ie);

		} catch (final IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public void startRecorder(){
		mHandler.sendEmptyMessage(MSG_START_RECORD);
	}

	private boolean isLocked;

	/**
	 * Call this after setOutputFilename(), but before startRecorder()
	 */
	public void prepareRecorder(){
		mHandler.sendEmptyMessage(MSG_PREPARE_RECORD);
	}
	private void prepareRecorderActual(){
		try {
			recorder.prepare();
		} catch (final IllegalStateException e) {
			alertFailSetup(e);
		} catch (final IOException e) {
			alertFailSetup(e);
		}
	}
	private void startRecorderActual(){
		try {
			recorder.start();
		} catch (final IllegalStateException e) {
			alertFailSetup(e);
		}
	}

	public void stopRecorder(){
		if (recorder != null){
			recorder.stop();
		}
	}

	public void alertFailSetup(Throwable e){
		closeCamera();
		Toast.makeText(getApplicationContext(), edu.mit.mel.locast.mobile.R.string.template_error_setup_fail, Toast.LENGTH_SHORT).show();
		e.printStackTrace();
		finish();
	}

	private File mFullOutputFile;
	/**
	 * @param filename A pathless filename for this recording.
	 */
	public void setOutputFilename(String filename){
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

		mFullOutputFile = new File(locastBase, filename +".3gp");
		try {
			recorder.setOutputFile(mFullOutputFile.getAbsolutePath());
		}catch (final IllegalStateException ise){
			Log.e(TAG, "Couldn't set output filename. Attempting to continue.", ise);
		}
	}

	public File getFullOutputFile(){
		return mFullOutputFile;
	}

	protected void initRecorder() {
		Log.d(TAG, "initializing recorder...");
		if (mCamera == null){
			Log.d(TAG, "Camera was null. Starting preview...");
			startPreview();
		}

		if (isLocked){
			unlockCamera();
		}
		try {
			if (recorder == null){
				recorder = new MediaRecorder();
			}else{
				recorder.reset();
			}
			recorder.setCamera(mCamera);
			recorder.setPreviewDisplay(mSurfaceHolder.getSurface());

			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

			recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			//recorder.setMaxDuration(5000); // XXX

			// set the recorder parameters from the settings detected in setCameraParameters()
			final Camera.Parameters params = mCamera.getParameters();
			final Size vidSize = params.getPreviewSize();
			recorder.setVideoSize(vidSize.width, vidSize.height);
			recorder.setVideoFrameRate(params.getPreviewFrameRate());

			recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);

			// this would be for setting the aspect ratio properly, but causes occasional white screens
			//final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera_view));
			//sv.setLayoutParams(new FrameLayout.LayoutParams((int)(sv.getHeight() * (720.0/480)), sv.getHeight()));

			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

			Log.d(TAG, "done setting recorder parameters");
			if (recorderStateHandler != null){
				recorderStateHandler.sendEmptyMessage(MSG_RECORDER_INITIALIZED);
			}
		} catch (final IllegalStateException e) {
			alertFailSetup(e);
			return;
		}
	}



}