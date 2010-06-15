package edu.mit.mel.locast.mobile.templates;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mobile.android.json.JSONArrayAdapter;

public class TemplateActivity extends Activity implements OnClickListener {
	public final static String ACTION_RECORD_TEMPLATED_VIDEO = "edu.mit.mobile.android.locast.ACTION_RECORD_TEMPLATED_VIDEO";

	private Camera camera;
	private SurfaceHolder surfaceHolder;
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

		setContentView(R.layout.template_main);

		final SurfaceView sv = ((SurfaceView)findViewById(R.id.camera_view));
		progressBar = (ProgressBar)findViewById(R.id.progress);
		
		surfaceHolder = sv.getHolder();
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		sv.setOnClickListener(this);

		surfaceHolder.addCallback(cameraSHListener);
		recorder = new MediaRecorder();
		
		lv = (ListView)findViewById(R.id.instructions_list);
		findViewById(R.id.list_overlay).setOnClickListener(new OnClickListener() { 
			public void onClick(View v) {
				// TODO Auto-generated method stub
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
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						h.sendEmptyMessage(0);
					}
				});
				t.start();
			}
		});

		try {
			templateAdapter = new TemplateAdapter(this, new URI("http://mobile-server.mit.edu/~stevep/test.json"));
			lv.setAdapter(templateAdapter);
			((ListView)findViewById(R.id.instructions_list_full)).setAdapter(templateAdapter);
			lv.setEnabled(false);
			
		} catch (final URISyntaxException e) {
			e.printStackTrace();
		}
	}

	private final SurfaceHolderCallback cameraSHListener = new SurfaceHolderCallback();
	private class SurfaceHolderCallback implements SurfaceHolder.Callback {
		public static final int MSG_UNLOCK_CAMERA = 0;
		
		private final Handler surfaceHandler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what){
				case MSG_UNLOCK_CAMERA:
					Log.d("template", "unlocking camera");
					camera.unlock();
					break;
				}
			};
		};
		public void unlockCamera(){
			surfaceHandler.sendEmptyMessage(MSG_UNLOCK_CAMERA);
		}
		
		public void surfaceDestroyed(SurfaceHolder holder) {

			camera.stopPreview();
			camera.release();   

		}

		public void surfaceCreated(SurfaceHolder holder) {
			camera = Camera.open();
			try {

				camera.setPreviewDisplay(surfaceHolder);
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			camera.startPreview();
			camera.unlock();
			Log.d("template", "unlocked camera");
			initRecorder();

		}
	};
	private int videoCount = 0; 
	private void initRecorder(){
		Log.d("template", "initializing recorder...");
		try {

			recorder.setCamera(camera);
			recorder.setPreviewDisplay(surfaceHolder.getSurface());
			
			recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
			recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			//recorder.setMaxDuration(5000); // XXX
			
			/*recorder.setVideoSize(320, 240);
			recorder.setVideoFrameRate(15);*/
			
			recorder.setVideoSize(720, 480); // N1-specific
			recorder.setVideoFrameRate(1000);
			
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
			recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
			
			final File storage = Environment.getExternalStorageDirectory();
			
			recorder.setOutputFile(storage + "/locast/" + "video_"+ videoCount++ +".3gp");
			
			//camera.unlock();
			
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
				try {
					final JSONObject jo = (JSONObject)adapter.getItem(section);
					totalLength += jo.getInt("time");
				}catch (final JSONException e){
					continue;
				}
			}

			listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_PROGRESS_MAX, totalLength, 0));

			int curTime = 0;
			int segmentedTime = 0;
			JSONObject jo;
			int segmentTime = 0;
			
			for (int section = 0; section < count; section++){
				listHandler.sendMessage(Message.obtain(listHandler, MSG_SET_SECTION, section, 0));
				// don't pause on the first one
				if (section > 0){
					pause();
				}
				listHandler.sendMessage(Message.obtain(listHandler, MSG_START_SECTION, section, 0));
				jo = (JSONObject)adapter.getItem(section);
				segmentTime = jo.optInt("time");
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

	private class TemplateAdapter extends JSONArrayAdapter {
		private final LayoutInflater inflater;
		private final NumberFormat timeFormat  = NumberFormat.getInstance();

		public TemplateAdapter(Context context, URI templateList) {
			super(context, templateList);
			inflater = (LayoutInflater)context.getSystemService(LAYOUT_INFLATER_SERVICE);
			timeFormat.setMinimumFractionDigits(1);
			timeFormat.setMaximumFractionDigits(1);
		}

		public void setItemProperty(int position, String key, Object value){
			final JSONObject jo = (JSONObject) getItem(position);

			try {
				jo.putOpt(key, value);
				this.notifyDataSetChanged();
			} catch (final JSONException e) {
				e.printStackTrace();
			}
		}

		public void setItemProperty(int position, String key, int value){

			final JSONObject jo = (JSONObject) getItem(position);

			try {
				jo.putOpt(key, value);
				this.notifyDataSetInvalidated();
			} catch (final JSONException e) {
				e.printStackTrace();
			}
		}


		public View getView(int position, View convertView, ViewGroup parent) {

			final JSONObject jo = (JSONObject) getItem(position);
			if (convertView == null){
				convertView = inflater.inflate(R.layout.template_item, null);
			}
			final TextView text = (TextView)convertView.findViewById(android.R.id.text1);
			text.setText(jo.optString("text"));

			final TextView countdown = (TextView)convertView.findViewById(R.id.time_remaining);
			final int time = jo.optInt("remainingtime", jo.optInt("time"));
			countdown.setText(timeFormat.format(time/1000.0) + "s");

			return convertView;
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
		MSG_END_SECTION = 5;

	private class ListHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch(msg.what){
			case MSG_SET_SECTION:
				lv.setSelectionFromTop(msg.arg1, 0);
				
				break;

			case MSG_UPDATE_TIME:
				templateAdapter.setItemProperty(msg.arg1, "remainingtime", msg.arg2);
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
			}
		}
	}
}