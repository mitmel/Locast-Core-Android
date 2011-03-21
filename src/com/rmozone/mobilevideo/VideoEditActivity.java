package com.rmozone.mobilevideo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.android.music.TouchInterceptor;

import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.StreamUtils;
import edu.mit.mobile.android.locast.WebImageLoader;
import edu.mit.mobile.android.locast.data.Cast;

public class VideoEditActivity extends Activity {

	public static final String ACTION_EDIT_CASTS_IN_PROJECT= "com.rmozone.mobilevideo.ACTION_EDIT_CASTS_IN_PROJECT";
	public static final String ACTION_APPEND_CAST_TO_EDIT = "com.rmozone.mobilevideo.ACTION_APPEND_CAST_TO_EDIT";

	//contextual menu
	public static final int MENU_RETRIM = 0;
	public static final int MENU_DELETE= 1;
	
		
	TouchInterceptor touchlist;
	ArrayList<JSONObject> edit_information_list = new ArrayList<JSONObject>();
	ArrayAdapter<JSONObject> adapter;
	int project_id;

	private Uri project_uri;
	
	final static String TAG = "VideoEditActivity";
	
	public int index_of_video_we_are_retrimming = -1;
	WebImageLoader imgLoader;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		setContentView(R.layout.videoeditview);
		
		imgLoader = ((Application)getApplication()).getImageLoader();
		
		//find widget components
		this.touchlist = (TouchInterceptor) findViewById(R.id.videoedit_touchlist);
		
		//gather data from intent
		final Intent i = getIntent();
		
		if(ACTION_EDIT_CASTS_IN_PROJECT.equals(i.getAction())) {
			this.project_uri = i.getData();
		
			final String frag = this.project_uri.getPath(); // looks like: "/project/2"
			final String s_id= frag.substring(frag.lastIndexOf('/') + 1);
			this.project_id = Integer.parseInt(s_id);
		}
		else {
			//we're hard-coding for now
			this.project_id = 42;
		}
		
		final boolean didItWork = getEditSpecFromServer();

		// this should prevent crashes when the network cannot be reached.
		if (!didItWork){
			Toast.makeText(this, "Sorry, video editing requires an active network connection. Are you connected?", Toast.LENGTH_LONG).show();
			finish();
		}
		
		//create an adapter to start testing drag 'n' drop (what a drag!)
		this.adapter = new VideoEditAdapter(this,
				android.R.layout.simple_list_item_1,
				edit_information_list);
		
		this.touchlist.setAdapter(this.adapter);
		
		//this.touchlist.setOnItemLongClickListener(this);
		this.registerForContextMenu(this.touchlist);
		
		this.touchlist.setDragListener(mDragListener);
		this.touchlist.setDropListener(mDropListener);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, MENU_RETRIM, 0, "Retrim Cast");
		menu.add(0, MENU_DELETE, 0, "Delete Cast");
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final JSONObject click_item = this.adapter.getItem(info.position);
		
		switch( item.getItemId()) {
		case MENU_RETRIM: {
			final Intent i = new Intent();
			i.setClass(this, VideoTrimActivity.class);
			
			try {
				i.setData(Uri.parse(Cast.CONTENT_URI.toString() + "/" + click_item.getString("media-md5")));
			}
			catch (final JSONException e) {e.printStackTrace();}
			
			this.index_of_video_we_are_retrimming = info.position;
			this.startActivityForResult(i, VideoChooseActivity.STATUS_RETRIM_EXISTING_VIDEO);
			break;
		}
		case MENU_DELETE: {
			this.adapter.remove(click_item);
		}
		}
		
		return true;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.videoedit_menu, menu);
		return true;
	}
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.videoedit_menu_publish:
    		this.sendEditsToServer();
    		break;
    	case R.id.videoedit_menu_add:
    		//create intent to choose cast 
    		final Intent chooseVideo = new Intent(VideoChooseActivity.ACTION_CHOOSE_VIDEO_FROM_PROJECT);
    		chooseVideo.setData(getIntent().getData());
    		startActivityForResult(chooseVideo, VideoChooseActivity.STATUS_START_PICK_VIDEO); //TODO: proper actioncodes
    		break;
    	}
    	return false;
    }
    
    private boolean sendEditsToServer() {
    	//reconstitute to JSON from the edit array and then to a String
    	final String edits = new JSONArray(this.edit_information_list).toString();
    	
    	final HttpClient httpclient = new DefaultHttpClient();
    	final HttpPost httppost = new HttpPost("http://mel-pydev.mit.edu:8090/api/mobilevideo/edits/post");
    	
    	final List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
    	nameValuePairs.add(new BasicNameValuePair("edits", edits));
    	nameValuePairs.add(new BasicNameValuePair("project_id", "" + this.project_id));
    	
    	try {
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			httpclient.execute(httppost);
			Log.d(TAG, "Success POSTing (I think)!");
			return true;
    	}
    	catch (final ClientProtocolException e) {}
    	catch (final IOException e) {}
    	
    	return false; //something went sour ...

    }

	
	private class VideoEditAdapter extends ArrayAdapter<JSONObject> {

		public VideoEditAdapter(Context context, int textViewResourceId,
				List<JSONObject> objects) {
			super(context, textViewResourceId, objects);
		}
		
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			//we need to make a ViewGroup containing an R.id.icon
			final LinearLayout lv = new LinearLayout(getContext());

			final LinearLayout txt_ll = new LinearLayout(getContext());
			txt_ll.setOrientation(LinearLayout.VERTICAL);
			txt_ll.setLayoutParams(new LinearLayout.LayoutParams(
					LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT,
					1.0f));
			
			final TextView title = new TextView(getContext());
			title.setMaxLines(2);
			final TextView description = new TextView(getContext());
			description.setMaxLines(3);
			final TextView time_info = new TextView(getContext());
			
			URL thumb_url = null;
			
			try {
				title.setText(getItem(position).getString("title"));
				description.setText(getItem(position).getString("description"));
				
				final long start_time = getItem(position).getLong("media-start");
				final long duration = getItem(position).getLong("duration");
				final String media_md5 = getItem(position).getString("media-md5");
				
				time_info.setText(String.format(
						"%d-%dms (%5.2fs)",
						start_time,
						start_time + duration,
						duration / 1000.0f));
				
				thumb_url = new URL(String.format(
						"http://mel-pydev.mit.edu/movid/api/get/%s/video/120x90r1.0/%06d.jpg",
						media_md5,
						(start_time + duration/2) / 1000));
						
						
			}
			catch (final JSONException e) { e.printStackTrace(); }
			catch (final MalformedURLException e) { e.printStackTrace(); }
			
			title.setTextSize(18);
			
			txt_ll.addView(title);
			txt_ll.addView(description);
			txt_ll.addView(time_info);
			
			final ImageView icon = new ImageView(getContext());
			icon.setImageResource(android.R.drawable.ic_menu_compass); //TODO: proper drag icon
			icon.setMinimumWidth(80);
			icon.setId(android.R.id.icon); //allow TouchInterceptor to find us.
			
			//find thumbnails!
			final ImageView thumb = new ImageView(getContext());
			

			imgLoader.loadImage(thumb, thumb_url);
			//webImageToImageView(thumb_url, thumb, getContext());
			
			lv.addView(icon);
			lv.addView(txt_ll);
			lv.addView(thumb);
			
			return lv;
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		if(resultCode == Activity.RESULT_CANCELED) {
			return;
		}

		final String path = data.getData().getPath(); // looks like /content/0x765ABDF79/start/23/end/2356
		
		final String[] pieces = path.split("/");
		
		final String md5_to_add = pieces[2];
		final long start = Long.parseLong(pieces[4]);
		final long end = Long.parseLong(pieces[6]);
		
		if(requestCode == VideoChooseActivity.STATUS_RETRIM_EXISTING_VIDEO && this.index_of_video_we_are_retrimming >= 0) {
			final JSONObject video_to_edit = this.adapter.getItem(this.index_of_video_we_are_retrimming);
			try {
				video_to_edit.put("media-start", start);
				video_to_edit.put("duration", end-start);
				this.adapter.notifyDataSetChanged();
			} 
			catch (final JSONException e) {e.printStackTrace();}
			return;
		}
		
		final JSONObject video = this.getVideoInfoFromServer(md5_to_add);
		
		if(video != null) {
			try {
				video.put("media-start", start);
				video.put("duration", end-start);
			} catch (final JSONException e) { e.printStackTrace();}

			this.adapter.add(video);
		}
	}
	
	public JSONObject getVideoInfoFromServer(String md5) {
		//TODO: repeated code like crazy ... this is getting out of control
		
		final String json_url = "http://mel-pydev.mit.edu/movid/api/videoinfo/" + md5 + ".json";
		
		Log.d(TAG, "Getting videoinfo from videoserver " + json_url);
		
		final HttpClient httpclient = new DefaultHttpClient();
		final HttpGet httpget = new HttpGet(json_url);
		
		HttpResponse response;
		
		try {
			response = httpclient.execute(httpget);
		}
		catch (final ClientProtocolException e) {e.printStackTrace();	return null;}
		catch (final IOException e) {e.printStackTrace();return null;}
		
		if(response != null && response.getStatusLine().getStatusCode() == 200) {
			//we probably got something
			String info_json;
			try {
				info_json = StreamUtils.inputStreamToString(response.getEntity().getContent());
				Log.d(TAG, "Got some info: " + info_json);
				return new JSONObject(info_json); 
			}
			catch (final IllegalStateException e) { e.printStackTrace(); return null;}
			catch (final IOException e) { e.printStackTrace(); return null;}
			catch (final JSONException e) { e.printStackTrace();}
		}
		return null;
		
	}
	
	public boolean getEditSpecFromServer() {
		//before we really get serious about our "Intents," let's hard-code a URL to a server-hosted JSON file
		//(TODO!)
		final String json_url = "http://mel-pydev.mit.edu/movid/output/" + this.project_id + "/edits.json";
		
		Log.d(TAG, "Getting editspec from videoserver " + json_url);
		
		final HttpClient httpclient = new DefaultHttpClient();
		final HttpGet httpget = new HttpGet(json_url);
		
		HttpResponse response;
		
		try {
			response = httpclient.execute(httpget);
		}
		catch (final ClientProtocolException e) {e.printStackTrace();	return false;}
		catch (final IOException e) {e.printStackTrace();return false;}
		
		if(response != null && response.getStatusLine().getStatusCode() == 200) {
			//we probably got something
			String edits_json;
			try {
				edits_json = StreamUtils.inputStreamToString(response.getEntity().getContent());
				Log.d(TAG, "Got some edits back: " + edits_json);
			}
			catch (final IllegalStateException e) { e.printStackTrace(); return false;}
			catch (final IOException e) { e.printStackTrace(); return false;}
			
			return this.setFromJSON(edits_json);
		}
		
		return false;
	}
	
	public boolean setFromJSON(String edits_json) {
		try {
			final JSONArray editlist = new JSONArray(edits_json);
			
			edit_information_list = new ArrayList<JSONObject>();
			
			for(int i=0; i<editlist.length(); i++) {
				final JSONObject dictionary = editlist.getJSONObject(i);
				
				edit_information_list.add(dictionary);

			}
		}
		catch (final JSONException e) { e.printStackTrace(); return false;}
		
		return true;
	}

	private final TouchInterceptor.DragListener mDragListener = 
		new TouchInterceptor.DragListener() {

			public synchronized void drag(int from, int to) { 
				edit_information_list.add(Math.min(edit_information_list.size()-1, to), edit_information_list.remove(from));
				touchlist.invalidateViews();

			}
		};
		
	private final TouchInterceptor.DropListener mDropListener = 
		new TouchInterceptor.DropListener() {
			
			public void drop(int from, int to) {
				Log.w(TAG, "Dropped.");
				//we don't have anything to do anymore.
				//sample_items.add(to, sample_items.remove(from));
				touchlist.invalidateViews();
				
			}
		};
		
		public static void webImageToImageView(URL url, ImageView view, Context context) {
			//download image (based on http://en.androidwiki.com/wiki/Loading_images_from_a_remote_server)

			Log.d(TAG, "Trying to get thumbnail from " + url);
			
			
			//TODO: asynchronous? caching?
			/*final ImageCache imc = ImageCache.getInstance(context);
			try {
				view.setImageBitmap(imc.getImage(url));
			}
			catch (final ImageCacheException e1) { e1.printStackTrace();}
			*/
			/*
			HttpURLConnection conn;
			try {
				conn = (HttpURLConnection) url.openConnection();
				conn.connect();
				Bitmap bmImg = BitmapFactory.decodeStream(conn.getInputStream());
				view.setImageBitmap(bmImg);
			} catch (IOException e) {e.printStackTrace();}
			*/
		}
		
}
