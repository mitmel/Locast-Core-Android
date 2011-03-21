package edu.mit.mobile.android.locast.test;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.TextSwitcher;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.templates.TemplateSetupError;
import edu.mit.mobile.android.locast.templates.VideoRecorder;

public class BasicVideoRecorder extends VideoRecorder implements OnClickListener, OnCheckedChangeListener {
	public static final String TAG = BasicVideoRecorder.class.getSimpleName();

	private final Handler mRecordStateHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, msg.toString());
			String status = "unknown";
			switch (msg.what) {
			case MSG_RECORDER_STARTED:
				status = "recorder started";
				break;
			case MSG_RECORDER_STOPPED:
				status = "recorder started";
				break;
			case MSG_PREVIEW_STOPPED:
				status = "preview stopped";
				break;

			case MSG_RECORDER_INITIALIZED:
				status = "recorder initialized";
				break;

			case MSG_RECORDER_SHUTDOWN:
				status = "recorder shutdown";
				break;

			default:
				break;
			}
			((TextSwitcher)findViewById(R.id.osd)).setText(status);
			findViewById(R.id.play).setEnabled(getFullOutputFile() != null);
		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.basic_video_recorder);
		final SurfaceView sv = (SurfaceView) findViewById(R.id.camera);

		initSurfaceHolder(sv.getHolder());

		findViewById(R.id.start).setOnClickListener(this);
		findViewById(R.id.stop).setOnClickListener(this);
		findViewById(R.id.play).setOnClickListener(this);
		final ToggleButton tb = (ToggleButton)findViewById(R.id.show_preview);
		tb.setOnCheckedChangeListener(this);
		setRecorderStateHandler(mRecordStateHandler);
	}

	@Override
	public void onClick(View v) {
		try {
		switch (v.getId()){
		case R.id.start:
			Log.d(TAG, "start button pressed");
			initRecorder();
			setOutputFilename("foo");
			prepareRecorder();
			startRecorder();
		break;

		case R.id.stop:
			Log.d(TAG, "stop button pressed");
			stopRecorder();

			break;

		case R.id.play:
			Log.d(TAG, "play button pressed");
			final Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(getFullOutputFile()), "video/3gpp");
			startActivity(intent);
			break;

		}
		}catch (final TemplateSetupError e){
			Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

		switch (buttonView.getId()){
		case R.id.show_preview:
			Log.d(TAG, "preview toggled");
			if (isChecked){
				startPreview();
			}else{
				stopPreview();
			}
			break;
		}
	}
}
