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
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.ViewSwitcher;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.CastMedia;
import edu.mit.mel.locast.mobile.data.MediaProvider;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.ShotList;

public class TemplatePlayer extends Activity implements OnCompletionListener {
	public static final String TAG = TemplatePlayer.class.getSimpleName();

	// stateful
	private int mCurrentCastVideoPosition;

	// stateless
	private VideoView mVideoView;
	private MediaController mMc;
	private ViewSwitcher osdSwitcher;
	private Cursor mCastMediaCursor = null;
	private Cursor castCursor = null;
	private Cursor mShotListCursor = null;
	private Uri mCurrentCastVideo = null;


	public static final int
		MSG_STATIC_HIDE = 0,
		MSG_STATIC_SHOW = 1;

	private static final String
		RUNTIME_STATE_CURRENT_CAST_VIDEO = "edu.mit.mobile.android.locast.CAST_VIDEO",
		RUNTIME_STATE_CURRENT_PLAYBACK_TIME = "edu.mit.mobile.android.locast.CURRENT_PLAYBACK_TIME";

	private final Runnable startAnim = new Runnable() {

		@Override
		public void run() {
			final ImageView noise = (ImageView)findViewById(R.id.noise);
			((AnimationDrawable)noise.getDrawable()).start();
		}
	};

	private final Handler uiHandler = new Handler(){
		@Override
		public void handleMessage(android.os.Message msg) {
			switch (msg.what){
				case MSG_STATIC_SHOW:{
					findViewById(R.id.staticview).setVisibility(View.VISIBLE);
					final ImageView noise = (ImageView)findViewById(R.id.noise);
					noise.post(startAnim);
				}break;

				case MSG_STATIC_HIDE:{
					findViewById(R.id.staticview).setVisibility(View.GONE);
					onCompletion(null);
				}break;
			}
		};
	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.template_player);

		mVideoView = (VideoView)findViewById(R.id.videoview);
		osdSwitcher = (ViewSwitcher)findViewById(R.id.osd_switcher);

		mMc = new MediaController(this);

        final View anchorView = mVideoView.getParent() instanceof View ?
                (View)mVideoView.getParent() : mVideoView;

		mMc.setMediaPlayer(mVideoView);
		mMc.setAnchorView(anchorView);
		mMc.setEnabled(false);

		mVideoView.setOnPreparedListener(new OnPreparedListener() {

			public void onPrepared(MediaPlayer mp) {
				mMc.setEnabled(true);
			}
		});

		mVideoView.setOnCompletionListener(this);

		new LoadCastTask().execute(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, mCurrentCastVideoPosition);
		outState.putInt(RUNTIME_STATE_CURRENT_PLAYBACK_TIME, mVideoView.getCurrentPosition());

	}

	@Override
	protected void onPause() {
		mVideoView.stopPlayback();
		super.onPause();
		uiHandler.removeMessages(MSG_STATIC_HIDE);
	}

	private boolean playCastVideo(){
		if (mCastMediaCursor != null && (mCastMediaCursor.isAfterLast() || mCastMediaCursor.isBeforeFirst())){
			return false;
		}
		if (mShotListCursor != null){
			mShotListCursor.moveToPosition(mCastMediaCursor.getPosition());
		}
		setVideoFromCursor();
		if (mCurrentCastVideo == null){
			uiHandler.sendMessage(uiHandler.obtainMessage(MSG_STATIC_SHOW));

			uiHandler.sendMessageDelayed(uiHandler.obtainMessage(MSG_STATIC_HIDE), 3000);

		}else{
			mVideoView.start();
		}
		return true;
	}

	private void nextOrFinish(){
		if (mCastMediaCursor != null && ! mCastMediaCursor.isLast()){
			mCastMediaCursor.moveToNext();
			playCastVideo();

		}else{ // done!
			// TODO do something when finished.
			mMc.show(0);
		}
	}

	private void loadShotList(){
		final Uri project = Cast.getProjectUri(castCursor);
		if (project != null){
			mShotListCursor = managedQuery(Project.getShotListUri(project), ShotList.PROJECTION, null, null, null);
			if (mShotListCursor.getCount() <= 0){
				// TODO need to handle the case where a project doesn't have a shotlist.
				mShotListCursor = null;
				Log.i(TAG, "no shot list");
			}
		}
	}

	private void setVideoFromCursor(){
		if (mCastMediaCursor.isClosed() || mCastMediaCursor.isBeforeFirst() || mCastMediaCursor.isAfterLast()){
			return;
		}
		mCurrentCastVideoPosition = mCastMediaCursor.getPosition();
		MediaProvider.dumpCursorToLog(mCastMediaCursor, CastMedia.PROJECTION);
		final int localUriCol = mCastMediaCursor.getColumnIndex(CastMedia._LOCAL_URI);

		if (mCastMediaCursor.isNull(localUriCol)){
			mVideoView.setVideoURI(null);
			mCurrentCastVideo = null;
		}else{
			final Uri localUri = Cast.parseMaybeUri(mCastMediaCursor.getString(localUriCol));
			mVideoView.setVideoURI(localUri);
			mCurrentCastVideo = localUri;
		}

		if (mShotListCursor != null){
			setOsdText(mShotListCursor.getInt(mShotListCursor.getColumnIndex(ShotList._LIST_IDX)) + 1 + ". "
					+ mShotListCursor.getString(mShotListCursor.getColumnIndex(ShotList._DIRECTION)));
		}
	}

	private void setOsdText(String osdText){
		final int current = osdSwitcher.getDisplayedChild();
		final int other = current == 0 ? 1 : 0;
		((TextView)osdSwitcher.getChildAt(other)).setText(osdText);
		osdSwitcher.setDisplayedChild(other);

	}

	public void onCompletion(MediaPlayer mp) {
		nextOrFinish();

	}

	private class LoadCastTask extends AsyncTask<Bundle, Void, Bundle>{

		@Override
		protected Bundle doInBackground(Bundle... params) {
			final Bundle savedInstanceState = params[0];
			final Intent intent = getIntent();
			final Uri data = intent.getData();

			if (data != null){
				final String type = getContentResolver().getType(data);
				Log.d(TAG, "Playing: " + data);

				if (MediaProvider.TYPE_PROJECT_CAST_ITEM.equals(type)){
					Log.d(TAG, "It's a cast!");
					castCursor = managedQuery(data, Cast.PROJECTION, null, null, null);
					castCursor.moveToFirst();
					mCastMediaCursor = managedQuery(Cast.getCastMediaUri(data), CastMedia.PROJECTION, null, null, null);
					if (savedInstanceState != null){
						mCastMediaCursor.moveToPosition(savedInstanceState.getInt(RUNTIME_STATE_CURRENT_CAST_VIDEO, 0));
					}

					loadShotList();
				}else{
					Log.e(TAG, "I don't know how to handle something of type: "+type);
					finish();
				}
			}

			return savedInstanceState;
		}

		@Override
		protected void onPostExecute(Bundle result) {
			final Intent intent = getIntent();
			if (Intent.ACTION_VIEW.equals(intent.getAction())){
				if (result != null){
					playCastVideo();
					mVideoView.seekTo(result.getInt(RUNTIME_STATE_CURRENT_PLAYBACK_TIME, 0));

				}else{
					nextOrFinish();
				}
			}
		}
	}
}
