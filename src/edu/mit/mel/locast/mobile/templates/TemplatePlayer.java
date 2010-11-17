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
	private VideoView mVideoView;
	private MediaController mMc;
	private ViewSwitcher osdSwitcher;
	private Cursor castMediaCursor = null;
	private Cursor castCursor = null;
	private Cursor shotListCursor = null;
	private Uri currentCastVideo = null;

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

		final Intent intent = getIntent();

		mVideoView = (VideoView)findViewById(R.id.videoview);
		osdSwitcher = (ViewSwitcher)findViewById(R.id.osd_switcher);

		final Uri data = intent.getData();
		if (data != null){
			final String type = getContentResolver().getType(data);
			Log.d(TAG, "Playing: " + data);

			if ("file".equals(data.getScheme())){
				Log.d(TAG, "It's a file!");
				mVideoView.setVideoPath(data.getPath());
				castMediaCursor = null;

			}else if (MediaProvider.TYPE_CAST_ITEM.equals(type)){
				Log.d(TAG, "It's a cast!");
				castCursor = managedQuery(data, Cast.PROJECTION, null, null, null);
				castCursor.moveToFirst();
				castMediaCursor = managedQuery(Cast.getCastMediaUri(data), CastMedia.PROJECTION, null, null, null);
				//castMediaCursor.moveToFirst();

				//MediaProvider.dumpCursorToLog(castCursor, Cast.PROJECTION);
				//loadCastMedia(Cast.getCastMediaUri(data));
				loadShotList();
			}else{
				Log.e(TAG, "I don't know how to handle something of type: "+type);
				finish();
			}
		}


		mMc = new MediaController(this);

        final View anchorView = mVideoView.getParent() instanceof View ?
                (View)mVideoView.getParent() : mVideoView;

		mMc.setMediaPlayer(mVideoView);
		mMc.setAnchorView(anchorView);
		mMc.setEnabled(false);

		//mVideoView.setMediaController(mMc);
		mVideoView.setOnPreparedListener(new OnPreparedListener() {

			public void onPrepared(MediaPlayer mp) {
				mMc.setEnabled(true);
			}
		});

		mVideoView.setOnCompletionListener(this);

		//mVideoView.requestFocus();
		//mVideoView.start();
		nextOrFinish();
	}

	@Override
	protected void onPause() {
		super.onPause();
		uiHandler.removeMessages(MSG_STATIC_HIDE);
	}

	private void nextOrFinish(){
		if (castMediaCursor != null && ! castMediaCursor.isLast()){
			castMediaCursor.moveToNext();
			if (shotListCursor != null){
				shotListCursor.moveToNext();
			}
			setVideoFromCursor();
			if (currentCastVideo == null){
				//final Toast t = Toast.makeText(this, "No video for this segment", Toast.LENGTH_LONG);
				//findViewById(R.id.staticview).setVisibility(View.VISIBLE);
				//final ImageView noise = (ImageView)findViewById(R.id.noise);

				// AnimationDrawables needs to be fully loaded before they can be started.
				/*noise.post(new Runnable() {
					@Override
					public void run() {
						((AnimationDrawable)noise.getDrawable()).start();
					}
				});*/
				uiHandler.sendMessage(uiHandler.obtainMessage(MSG_STATIC_SHOW));

				uiHandler.sendMessageDelayed(uiHandler.obtainMessage(MSG_STATIC_HIDE), 3000);

			}else{
				mVideoView.start();
			}

		}else{ // done!
			// TODO do something
			mMc.show(0);
		}
	}

	private void loadShotList(){


		final Uri project = Cast.getProjectUri(castCursor);
		if (project != null){
			shotListCursor = managedQuery(Project.getShotListUri(project), ShotList.PROJECTION, null, null, null);
			if (shotListCursor.getCount() > 0){
				//shotListCursor.moveToFirst();
				//MediaProvider.dumpCursorToLog(shotListCursor, ShotList.PROJECTION);

				// XXX left off getting the casts to play even if there's missing video
			}else{
				shotListCursor = null;
				Log.i(TAG, "no shot list");
			}
		}
	}

	private void setVideoFromCursor(){
		if (castMediaCursor.isClosed() || castMediaCursor.isBeforeFirst() || castMediaCursor.isAfterLast()){
			return;
		}
		MediaProvider.dumpCursorToLog(castMediaCursor, CastMedia.PROJECTION);
		final int localUriIdx = castMediaCursor.getColumnIndex(CastMedia._LOCAL_URI);

		if (castMediaCursor.isNull(localUriIdx)){
			Log.e(TAG, "no cast video for this particular shot");
			// TODO no video here. Do something else?
			mVideoView.setVideoURI(null);
			currentCastVideo = null;
		}else{
			final Uri localUri = Cast.parseMaybeUri(castMediaCursor.getString(localUriIdx));
			mVideoView.setVideoURI(localUri);
			currentCastVideo = localUri;
		}

		if (shotListCursor != null){
			setOsdText(shotListCursor.getInt(shotListCursor.getColumnIndex(ShotList._LIST_IDX)) + 1 + ". "
					+ shotListCursor.getString(shotListCursor.getColumnIndex(ShotList._DIRECTION)));
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


}
