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

import com.android.camera.CameraHardwareException;
import com.android.camera.CameraHolder;

/**
 * <p>An easy-to-use video recorder. You should extend this class and follow the simple setup instructions below.</p>
 *
 * <p>You will need a SurfaceView to display the camera viewfinder. In your onCreate() you should call:
 *
 * <pre class="prettyprint">
 *     initSurfaceHolder(mSurfaceView.getHolder());
 * </pre>
 *
 * where mSurfaceView is your SurfaceView.</p>
 *
 * <p>That's it! Now you can start recording by calling the methods in the following way.
 * First, you need to tell it what to save as:
 *
 *<pre class="prettyprint">
 *     setOutputFilename("example");
 *</pre></p>
 * <p>Once the filename is set you can query for the full path using:
 * <pre>
 *     getFullOutputFile()
 * </pre>
 *
 * Next you need to prepare the recorder:
 *
 *<pre class="prettyprint">
 *     prepareRecorder();
 *</pre></p>
 *
 * <p>This might take a small amount of time, so if you want the code to start immediately when recording, you may wish to call this ASAP.
 * Finally, possibly on your shutter button, you'll want to do:</p>
 *
 *<pre class="prettyprint">
 *     startRecorder();
 *</pre>
 *
 * <p>to start the recording. Of course,
 * <pre class="prettyprint">
 *     stopRecorder();
 * </pre>
 * will stop the recording.</p>
 *
 * Make sure to call the super of onPause() and onResume() so that video previews are handled properly.
 *
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
							MSG_RECORDER_STOPPED        = 103,
							MSG_PREVIEW_STOPPED			= 104
							;

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
			if (holder.getSurface() == null){
				Log.d(TAG, "holder.getSurface() == null");
				return;
			}

	        if (mPausing) {
	            // We're pausing, the screen is off and we already stopped
	            // video recording. We don't want to start the camera again
	            // in this case in order to conserve power.
	            // The fact that surfaceChanged is called _after_ an onPause appears
	            // to be legitimate since in that case the lockscreen always returns
	            // to portrait orientation possibly triggering the notification.
	            return;
	        }

			if (mCamera == null){
				Log.d(TAG, "camera was null in surfaceChanged");
				return;
			}

			if (holder.isCreating()){
				setPreviewDisplay(holder);
				Log.d(TAG, "surface changed set preview display");
				//mRecorderStateHandler.sendEmptyMessage(MSG_PREVIEW_STOPPED);
				mInternalHandler.sendEmptyMessage(_MSG_INIT_RECORDER);
			}else{
				stopRecorder();
				restartPreview();
			}
		}
	};

	public void initSurfaceHolder(SurfaceHolder holder){
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		holder.addCallback(mCameraSHListener);
	}

	private boolean mPausing;

	@Override
	protected void onResume() {
		super.onResume();
		mPausing = false;

		if (!mIsPreviewing && mShouldPreview){
			if (!restartPreview()){
				return;
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		mPausing = true;

		stopRecorder();
		if (mCamera != null){
			Log.d(TAG, "camera unlock");
			mCamera.unlock();
		}

		closeCamera();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	private Size mCameraPreviewSize;
	private int mCameraFrameRate;

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

		mCameraPreviewSize = params.getPreviewSize();
		mCameraFrameRate = params.getPreviewFrameRate();

		mCamera.setParameters(params);
	}

	/**
	 * Override this if you wish to change the media format.
	 *
	 * @return the MediaRecorder.OutputFormat that you wish to use.
	 */
	protected int getOutputFormat(){
		if (Build.MODEL.equals("Nexus One") || Build.MODEL.equals("Milestone")){
			return MediaRecorder.OutputFormat.MPEG_4;
		}
		return MediaRecorder.OutputFormat.THREE_GPP;
	}

	protected String getOutputFileExtension(){
		if (Build.MODEL.equals("Nexus One") || Build.MODEL.equals("Milestone")){
			return "mp4";
		}
		return "3gp";
	}

	/**
	 * Override this if you wish to change the audio encoder.
	 *
	 * @return the MediaRecorder.AudioEncoder that you wish to use.
	 */
	protected int getAudioEncoder(){
		return MediaRecorder.AudioEncoder.AMR_NB;
	}

	/**
	 * Override this if you wish to change the video encoder.
	 *
	 * @return the MediaRecorder.VideoEncoder that you wish to use.
	 */
	protected int getVideoEncoder(){
		int encoder;
		if (Build.MODEL.equals("Milestone")){
			encoder = MediaRecorder.VideoEncoder.H264;

		}else if (Build.MODEL.equals("Nexus One")) {
			encoder = MediaRecorder.VideoEncoder.H264;


		}else{ // for all other devices, fall back on a well-known default.
			encoder = MediaRecorder.VideoEncoder.DEFAULT;
		}
		return encoder;
	}

	/**
	 * Stops the camera preview. Any further calls to the recorder will fail.
	 */
	public void stopPreview(){
		mShouldPreview = false;
		releaseMediaRecorder();
		if (mCamera != null){
			Log.d(TAG, "camera unlock");
			mCamera.unlock();
		}
		closeCamera();
	}

	/**
	 * Starts the camera preview on the given surface.
	 */
	public void startPreview(){
		mShouldPreview = true;
		try {
			actualStartPreview();
		}catch (final Throwable e){
			alertFailSetup(e);
		}
	}

	/**
	 * Starts the camera preview on the given surface.
	 */
	private void actualStartPreview() throws CameraHardwareException {
		if (mCamera == null){
			Log.d(TAG, "opening camera");
			mCamera = CameraHolder.instance().open();
		}

		if (mIsPreviewing){
			mCamera.stopPreview();
			mIsPreviewing = false;
		}

		setPreviewDisplay(mSurfaceHolder);
		setCameraParameters();

	    try {
			mCamera.startPreview();
			mIsPreviewing = true;
			Log.d(TAG, "preview is up and running");
	    } catch (final Throwable ex) {
	    	alertFailSetup(ex);
	    	return;
	    }
	}

	private boolean restartPreview(){
		try {
			actualStartPreview();

		}catch (final CameraHardwareException e){
			alertFailSetup(e);
			return false;
		}
		return true;
	}

	/**
	 * Stops the camera preview. Any further calls to the recorder will fail.
	 */
	private void closeCamera() {
		Log.d(TAG, "closeCamera");
		if (mCamera == null){
			return;
		}
		Log.d(TAG, "closing camera");

		// need to lock the camera to release it(!)
		Log.d(TAG, "camera lock");
		mCamera.lock();
		CameraHolder.instance().release();
		Log.d(TAG, "preview stopped");
		mIsPreviewing = false;
		mCamera = null;

		Log.d(TAG, "Camera has been shut down");
		mRecorderStateHandler.sendEmptyMessage(MSG_PREVIEW_STOPPED);
	}

	private void releaseMediaRecorder(){
		Log.d(TAG, "releasing media recorder.");
		if (mRecorder != null){
			mRecorder.reset();
			mRecorder.release();
			mRecorder = null;
			mRecorderStateHandler.sendEmptyMessage(MSG_RECORDER_SHUTDOWN);
		}

		if (mCamera != null){
			// Take back the camera object control from media recorder.
			Log.d(TAG, "camera lock");
			mCamera.lock();
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

	public void startRecorder(){
		mInternalHandler.sendEmptyMessage(_MSG_START_RECORD);
	}

	/**
	 * Call this after setOutputFilename(), but before startRecorder()
	 */
	public void prepareRecorder(){
		mInternalHandler.sendEmptyMessage(_MSG_PREPARE_RECORD);
	}

	private void prepareRecorderActual(){
		try {
			mRecorder.prepare();
		} catch (final IOException e) {
			releaseMediaRecorder();
			alertFailSetup(e);
		}
	}

	private boolean mMediaRecorderRecording = false;

	private void startRecorderActual(){
		try {
			mRecorder.start();
			mMediaRecorderRecording = true;

			mRecorderStateHandler.sendEmptyMessage(MSG_RECORDER_STARTED);
		} catch (final IllegalStateException e) {
			releaseMediaRecorder();
			alertFailSetup(e);
			return;
		}
	}

	public void stopRecorder(){
		if (mMediaRecorderRecording){
			if (mRecorder == null){
				throw new RuntimeException("mRecorder is null");
			}
			try {
				mRecorder.stop();
			}catch (final RuntimeException e){
				Log.e(TAG, "stop failed: " + e.getMessage());
			}
			mMediaRecorderRecording = false;
			mRecorderStateHandler.sendEmptyMessage(MSG_RECORDER_STOPPED);
		}

		releaseMediaRecorder();
	}

	public void alertFailSetup(Throwable e){
		closeCamera();
		Toast.makeText(getApplicationContext(), edu.mit.mel.locast.mobile.R.string.template_error_setup_fail, Toast.LENGTH_SHORT).show();
		e.printStackTrace();
		finish();
	}

	private File mFullOutputFile;
	/**
	 * @param filename A pathless, extensionless filename for this recording.
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

		mFullOutputFile = new File(locastBase, filename +"."+getOutputFileExtension());
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
			Log.d(TAG, "Camera was null on initRecorder");
			return;
		}

		if (mSurfaceHolder == null){
            Log.v(TAG, "Surface holder is null. Wait for surface changed.");
            return;
		}
		if (mRecorder != null){
			Log.d(TAG, "already initialized");
			return;
		}

		try {
			mRecorder = new MediaRecorder();

			Log.d(TAG, "camera unlock");
			mCamera.unlock();
			mRecorder.setCamera(mCamera);

			mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

			mRecorder.setOutputFormat(getOutputFormat());

			// set the recorder parameters from the settings detected in setCameraParameters()
			final Camera.Parameters params = mCamera.getParameters();
			Size vidSize = params.getPreviewSize();
			if (vidSize == null){
				Log.w(TAG, "got null when asking camera for preview size. Using cached values instead.");
				vidSize = mCameraPreviewSize;
				mRecorder.setVideoFrameRate(mCameraFrameRate);
			}else{
				mRecorder.setVideoFrameRate(params.getPreviewFrameRate());
			}
			mRecorder.setVideoSize(vidSize.width, vidSize.height);


			// this would be for setting the aspect ratio properly, but causes occasional white screens
			//final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera_view));
			//sv.setLayoutParams(new FrameLayout.LayoutParams((int)(sv.getHeight() * (720.0/480)), sv.getHeight()));

			mRecorder.setAudioEncoder(getAudioEncoder());
			mRecorder.setVideoEncoder(getVideoEncoder());

			mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

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