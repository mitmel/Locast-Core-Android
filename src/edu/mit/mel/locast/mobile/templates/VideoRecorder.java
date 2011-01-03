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

/**
 * @author steve
 *
 */
/**
 * @author steve
 *
 */
public abstract class VideoRecorder extends Activity {

	private static final String TAG = VideoRecorder.class.getSimpleName();
	private Camera mCamera;
	private SurfaceHolder mSurfaceHolder;
	private boolean mIsPreviewing = false;
	private boolean mShouldPreview = true;
	private MediaRecorder mRecorder = null;

	private static final int
		_MSG_INIT_RECORDER  = 1,
		_MSG_START_RECORD   = 2,
		_MSG_PREPARE_RECORD = 3;

	private final Handler mInternalHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
			case _MSG_INIT_RECORDER:
				initRecorder();
				break;

			case _MSG_START_RECORD:
				startRecorderActual();
				break;

			case _MSG_PREPARE_RECORD:
				prepareRecorderActual();
				break;
			}
		}
	};

	private Handler mRecorderStateHandler;

	public final static int MSG_RECORDER_INITIALIZED 	= 100,
							MSG_RECORDER_SHUTDOWN 		= 101,
							MSG_RECORDER_STARTED 		= 102,
							MSG_PREVIEW_STOPPED			= 103;

	public void setRecorderStateHandler(Handler recorderStateHandler) {
		this.mRecorderStateHandler = recorderStateHandler;
	}

	private final SurfaceHolder.Callback mCameraSHListener = new SurfaceHolder.Callback() {

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
				Log.d(TAG, "surface changed set preview display");
				mInternalHandler.sendEmptyMessage(_MSG_INIT_RECORDER);
			}
		}
	};

	public void initSurfaceHolder(SurfaceHolder holder){
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		holder.addCallback(mCameraSHListener);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!mIsPreviewing && mShouldPreview){
			actualStartPreview();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		actualStopPreview();
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
			params.setPreviewSize(720, 480);
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

	/**
	 * Stops the camera preview. Any further calls to the recorder will fail.
	 */
	public void stopPreview(){
		mShouldPreview = false;
		actualStopPreview();
	}

	/**
	 * Starts the camera preview on the given surface.
	 */
	public void startPreview(){
		mShouldPreview = true;
		actualStartPreview();
	}

	/**
	 * Starts the camera preview on the given surface.
	 */
	private void actualStartPreview() {
		if (mIsPreviewing){
			// already rolling...
			return;
		}
		if (mCamera == null){
			Log.d(TAG, "opening camera");
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
			Log.d(TAG, "preview is up and running");
	    } catch (final Throwable ex) {
	    	alertFailSetup(ex);
	    	return;
	    }
	    if (mSurfaceHolder != null){
	    	unlockCamera();
	    }
	}

	/**
	 * Stops the camera preview. Any further calls to the recorder will fail.
	 */
	private void actualStopPreview() {
		if (mCamera == null){
			return;
		}
		if (mRecorder != null){
			mRecorder.release();
			// release unlocks the camera
			mRecorder = null;
		}
		Log.d(TAG, "closing camera");

		// need to lock the camera to release it(!)
		lockCamera();
		mCamera.release();
		Log.d(TAG, "preview stopped");
		mIsPreviewing = false;
		mCamera = null;

		Log.d(TAG, "Camera has been shut down");
		mRecorderStateHandler.sendEmptyMessage(MSG_PREVIEW_STOPPED);
	}

	private void setPreviewDisplay(SurfaceHolder holder) {
	    try {
	        mCamera.setPreviewDisplay(holder);
	    } catch (final Throwable ex) {
	        actualStopPreview();
	        throw new RuntimeException("setPreviewDisplay failed", ex);
	    }
	}

	/**
	 * Call the lock() method (introduced in API level 5) if possible.
	 */
	private void lockCamera() {
		try {
			try {
				final Method lock = mCamera.getClass().getDeclaredMethod("lock");
				lock.invoke(mCamera);

			}catch (final NoSuchMethodException m){
				// no such method in api < 5
			}

			Log.d(TAG, "locked camera");
			mIsLocked = true;

		}catch (final InvocationTargetException ie){
			throw new RuntimeException(ie.getTargetException());

		} catch (final IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Call the unlock() method (introduced in API level 5) if possible
	 * Otherwise just stop the preview to unlock.
	 */
	private void unlockCamera() {
		try {
			try {
				final Method unlock = mCamera.getClass().getDeclaredMethod("unlock");
				unlock.invoke(mCamera);

			}catch (final NoSuchMethodException m){
				// before API level 5, this seems to be the only way to unlock.
				mCamera.stopPreview();
			}

			Log.d(TAG, "unlocked camera");
			mIsLocked = false;

		}catch (final InvocationTargetException ie){
			throw new RuntimeException(ie);

		} catch (final IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public void startRecorder(){
		mInternalHandler.sendEmptyMessage(_MSG_START_RECORD);
	}

	private boolean mIsLocked;

	/**
	 * Call this after setOutputFilename(), but before startRecorder()
	 */
	public void prepareRecorder(){
		mInternalHandler.sendEmptyMessage(_MSG_PREPARE_RECORD);
	}

	private void prepareRecorderActual(){
		try {
			mRecorder.prepare();
		} catch (final IllegalStateException e) {
			alertFailSetup(e);
		} catch (final IOException e) {
			alertFailSetup(e);
		}
	}

	private void startRecorderActual(){
		try {
			mRecorder.start();
			mRecorderStateHandler.sendEmptyMessage(MSG_RECORDER_STARTED);
		} catch (final IllegalStateException e) {
			alertFailSetup(e);
		}
	}

	public void stopRecorder(){
		if (mRecorder != null){
			mRecorder.stop();
		}
	}

	public void alertFailSetup(Throwable e){
		actualStopPreview();
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
			mRecorder.setOutputFile(mFullOutputFile.getAbsolutePath());
		}catch (final IllegalStateException ise){
			Log.e(TAG, "Couldn't set output filename. Attempting to continue.", ise);
		}
	}

	public File getFullOutputFile(){
		return mFullOutputFile;
	}

	/**
	 * Call this from start or from preview being stopped.
	 */
	protected void initRecorder() {
		Log.d(TAG, "initializing recorder...");
		if (mCamera == null){
			Log.d(TAG, "Camera was null. Starting preview...");
			actualStartPreview();
		}

		if (mIsLocked){
			unlockCamera();
		}
		try {
			if (mRecorder == null){
				mRecorder = new MediaRecorder();
			}else{
				mRecorder.reset();
			}
			mRecorder.setCamera(mCamera);
			mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

			mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

			mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

			// set the recorder parameters from the settings detected in setCameraParameters()
			final Camera.Parameters params = mCamera.getParameters();
			final Size vidSize = params.getPreviewSize();
			mRecorder.setVideoSize(vidSize.width, vidSize.height);
			mRecorder.setVideoFrameRate(params.getPreviewFrameRate());

			// this would be for setting the aspect ratio properly, but causes occasional white screens
			//final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera_view));
			//sv.setLayoutParams(new FrameLayout.LayoutParams((int)(sv.getHeight() * (720.0/480)), sv.getHeight()));

			mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

			if (Build.MODEL.equals("Milestone")){
				mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

			}else if (Build.MODEL.equals("Nexus One")) {
				mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

			}else{ // for all other devices, fall back on a well-known default.
				mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
			}


			Log.d(TAG, "done setting recorder parameters");
			if (mRecorderStateHandler != null){
				mRecorderStateHandler.sendEmptyMessage(MSG_RECORDER_INITIALIZED);
			}
		} catch (final IllegalStateException e) {
			alertFailSetup(e);
			return;
		}
	}



}