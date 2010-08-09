package edu.mit.mel.locast.mobile.templates;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.MediaController;
import android.widget.VideoView;
import edu.mit.mel.locast.mobile.R;

public class TemplatePlayer extends Activity {
	public static final String TAG = TemplatePlayer.class.getSimpleName();
	private VideoView mVideoView;
	private MediaController mMc;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.template_player);

		final Intent intent = getIntent();

		mVideoView = (VideoView)findViewById(R.id.videoview);

		final Uri data = intent.getData();
		if (data != null){
			Log.d(TAG, "Playing: " + data);
			if ("file".equals(data.getScheme())){
				mVideoView.setVideoPath(data.getPath());
			}
		}

		mMc = new MediaController(this);
		mMc.setMediaPlayer(mVideoView);
		mVideoView.setMediaController(mMc);
		mVideoView.requestFocus();
		mVideoView.start();
	}
}
