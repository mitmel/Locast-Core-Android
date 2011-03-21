package com.rmozone.mobilevideo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.StreamUtils;
import edu.mit.mobile.android.locast.data.Cast;

/*
 * Allows selection of a video clip from all available medias.
 * 
 * VideoEdit -> VideoChoose -> VideoTrim -> VideoEdit
 */
public class VideoChooseActivity extends Activity implements OnItemClickListener {
	public static final String ACTION_CHOOSE_VIDEO_FROM_PROJECT = "com.rmozone.mobilevideo.ACTION_CHOOSE_VIDEO_FROM_PROJECT";
	
	public static final int STATUS_START_PICK_VIDEO = 0;
	public static final int STATUS_RETURNING_TRIMMED_VIDEO = 1;
	public static final int STATUS_START_TRIMMING_VIDEO = 2;
	public static final int STATUS_RETRIM_EXISTING_VIDEO = 3;
	
	GridView grid;
	static final String TAG = "VideoChoose";
	ArrayList<JSONObject> medialist;
	
	int project_id;  //TODO: wire with intents
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.videochooseview);
		
		grid = (GridView) findViewById(R.id.videochoose_grid);
		
		// get project ID from intent
		this.project_id = Integer.parseInt(getIntent().getData().getPath().substring(10)); //it works.
		
		// get list of available medias
		if(!this.getMediaListFromServer()) {
			//freak out!
			Toast.makeText(this, "Failed to connect to video server", 5000);
		}
		
		final VideoThumbAdapter adapter = new VideoThumbAdapter(this);
		
		grid.setAdapter(adapter); //TODO: will need to modify to add some text...
		
		//set callbacks
		grid.setOnItemClickListener(this);
	}
	
	public boolean getMediaListFromServer() {
		final String json_url = "http://mel-pydev.mit.edu/movid/output/" + this.project_id + "/media.json";
		
		Log.d(TAG, "Trying to get mediajson from " + json_url);
		
		final HttpClient httpclient = new DefaultHttpClient(); //TODO: combine codes with something smarter that takes in auth data?
		final HttpGet httpget = new HttpGet(json_url);
		
		HttpResponse response;
		
		try {
			response = httpclient.execute(httpget);
		}
		catch (final ClientProtocolException e) { e.printStackTrace(); return false;}
		catch (final IOException e) { e.printStackTrace(); return false;}

		String media_json;
		if(response != null && response.getStatusLine().getStatusCode() == 200) {
			try {
				media_json = StreamUtils.inputStreamToString(response.getEntity().getContent());
			}
			catch (final IllegalStateException e) {e.printStackTrace(); return false;}
			catch (final IOException e) {e.printStackTrace(); return false;}
			
			return this.setFromJSON(media_json); 
		}
		return false;
		
	}
	
	private boolean setFromJSON(String media_json) {
		this.medialist = new ArrayList<JSONObject>();
		try {
			final JSONArray medialistjson = new JSONArray(media_json);
			for(int i=0; i<medialistjson.length(); i++) {
				this.medialist.add(medialistjson.getJSONObject(i));
			}
		}
		catch (final JSONException e) { e.printStackTrace(); return false;}
		
		return true;
	}
	
	public class VideoThumbAdapter extends BaseAdapter {
		//based on http://developer.android.com/guide/tutorials/views/hello-gridview.html
		private final Context mContext;
		
		public VideoThumbAdapter(Context c) {
			mContext = c;
		}
		
		public int getCount() {
			return medialist.size();
		}
		
		public JSONObject getItem(int position) {
			return medialist.get(position);
		}
		
		public long getItemId(int position) {
			return position;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			Log.d(TAG, "Getting Item #" + position);
			
			final LinearLayout thumbview = new LinearLayout(mContext);
			
			//thumbview.setLayoutParams(new LayoutParams(
			//		LayoutParams.FILL_PARENT,
			//		LayoutParams.FILL_PARENT));
			
			thumbview.setOrientation(LinearLayout.VERTICAL);
			final ImageView imageView = new ImageView(mContext);
			
			imageView.setLayoutParams(new LayoutParams(
					LayoutParams.FILL_PARENT,
					LayoutParams.WRAP_CONTENT));
			
			final TextView text = new TextView(mContext);
			
			//TODO: deal with convertView ...
			
			//get thumbnail
			//TODO: ImageCache
			try {
				text.setText(getItem(position).getString("title"));
				
				final URL thumbnail_url = new URL("http://mel-pydev.mit.edu/movid/api/get/" + getItem(position).getString("media-md5") + "/video/120x90r1.0/000001.jpg");
				VideoEditActivity.webImageToImageView(thumbnail_url, imageView, mContext);
			}
			catch (final JSONException e) { e.printStackTrace();}
			catch (final MalformedURLException e) { e.printStackTrace();}

			
			imageView.setLayoutParams(new GridView.LayoutParams(100, 100));
			imageView.setPadding(4, 4, 4, 0);
			
			

			thumbview.addView(imageView);
			thumbview.addView(text);
			
			return thumbview;
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		final JSONObject o = (JSONObject) parent.getItemAtPosition(position);
		
		//go to trim activity ...
		try {
			final String md5 = o.getString("media-md5");
			final Intent i = new Intent();
			i.setClass(this, VideoTrimActivity.class);
			//i.setAction(VideoTrimActivity.ACTION_TRIM_CAST);
			i.setData(Uri.parse(Cast.CONTENT_URI.toString() + "/"+ md5));
			
			this.startActivityForResult(i, STATUS_START_TRIMMING_VIDEO);
			//this.startActivity(i);
		}
		catch (final JSONException e) {e.printStackTrace();}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == STATUS_RETURNING_TRIMMED_VIDEO) {
			//pass on what we're given
			Log.d(TAG, "Passing through VideoChoose back to VideoEdit");
			this.setResult(STATUS_RETURNING_TRIMMED_VIDEO, data);
			finish();
		}
	}
}
