package com.rmozone.mobilevideo;

import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout.LayoutParams;

import com.rmozone.mobilevideo.AnnotationDBKey.Annotation;

import edu.mit.mel.locast.mobile.R;

public class VideoTrimActivity extends Activity implements OnClickListener, OnItemClickListener {
	public static final String ACTION_TRIM_CAST = "com.rmozone.mobilevideo.ACTION_TRIM_CAST";
	
	final static String TAG = "VideoTrimActivity";
	
	public static final int MENU_SET_IN = 0;
	public static final int MENU_SET_OUT = 1;

	ListView annotations;
	String video_md5;
	AnnotationThumbAdapter adapter;
	
	ToggleButton start, end;
	
	long start_time, end_time;
	
	Cursor c;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		setContentView(R.layout.videotrimview);
		
		this.annotations = (ListView) findViewById(R.id.videotrim_list);
		
		// bind controls, words, and thoughts to actions (danger!)
		start = (ToggleButton) findViewById(R.id.videotrim_start);
		start.setOnClickListener(this);
		
		end = (ToggleButton) findViewById(R.id.videotrim_end);
		end.setOnClickListener(this);
		findViewById(R.id.videotrim_done).setOnClickListener(this);
		
		// link up to the annotation data
		video_md5 = getIntent().getData().getPath().substring(9); // ...
		
		// build up an adapter and other data tools that may come in handy
		
		//sync w/annotation server
		AnnotationActivity.getAnnotationsFromServer(video_md5, getContentResolver());
		
		c = Annotation.md5ToCursor(this, video_md5);
		adapter = new AnnotationThumbAdapter(this, c);
		this.annotations.setAdapter(adapter);
		this.annotations.setOnItemClickListener(this);
		this.registerForContextMenu(this.annotations);
		
		//initialize start & end
		//TODO: take in current values w/complext intent
		this.start_time = 0;
		this.end_time = 1000;
		if(adapter.getCount() > 0) {
			try {
				this.end_time = adapter.getItem(adapter.getCount() - 1).getLong(0);
			} catch (JSONException e) { e.printStackTrace(); }
		}
		
		this.updateVisibilities();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, MENU_SET_IN, 0, "Start here");
		menu.add(0, MENU_SET_OUT, 0, "End here");
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
		switch(item.getItemId()) {
		case MENU_SET_IN: {
			this.setStart(info.position);
			break;
		}
		case MENU_SET_OUT: {
			this.setEnd(info.position);
			break;
		}
			
		}
		
		return true;
	}
	
	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.videotrim_done:
			
			final Intent i = new Intent();
			final Uri uri = Uri.parse(getIntent().getDataString() + 
					"/start/" + this.start_time + "/end/" + this.end_time
					);
			i.setData(uri);
			
			this.setResult(VideoChooseActivity.STATUS_RETURNING_TRIMMED_VIDEO, i);
			
			finish();
			break;
			
		case R.id.videotrim_start:
			end.setChecked(false);
			break;
			
		case R.id.videotrim_end:
			start.setChecked(false);
			break;
			
		}
	}
	
	public void setStart(int position) {
		Log.d(TAG, "Set start to " + position);
		
		try {
			JSONArray new_start = this.adapter.getItem(position);
			if(new_start.getLong(0) < this.end_time) {
				this.start_time = new_start.getLong(0);
			}
		}
		catch (JSONException e) {e.printStackTrace();}
		this.updateVisibilities();
	}
	
	public void setEnd(int position) {
		Log.d(TAG, "Set end to " + position);
		
		try {
			JSONArray new_end = this.adapter.getItem(position);
			if(new_end.getLong(0) > this.start_time) {
				this.end_time = new_end.getLong(0);
			}
		}
		catch (JSONException e) {e.printStackTrace();}
		this.updateVisibilities();
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		
		if(start.isChecked()) {
			this.setStart(position);
		}
		else if(end.isChecked()) {
			this.setEnd(position);
		}
		
		start.setChecked(false);
		end.setChecked(false);
	}
	
	public void updateVisibilities() {
		//update visibility
		for(int i=0; i<this.adapter.getCount(); i++) {
			JSONArray entry = this.adapter.getItem(i);
			try {
				entry.put(2, entry.getLong(0) >= this.start_time &&
						entry.getLong(0) <= this.end_time ? true : false);
			}
			catch (JSONException e) { e.printStackTrace(); }
		}
		this.adapter.notifyDataSetChanged();
	}
	
	/*
	 * We will make a simple adapter for annotations that will display
	 * a thumbnail and the text of the annotation. 
	 */
	public class AnnotationThumbAdapter extends BaseAdapter {

		private final Context mContext;
		private final JSONArray mItems;
		
		public AnnotationThumbAdapter(Context ctx, Cursor cur) {
			mContext = ctx;
			
			mItems = AnnotationActivity.annotationsToJSON(cur);
			
			Log.d(TAG, "created thumbadapter");
		}
		
		public int getCount() {
			return mItems.length();
		}

		public JSONArray getItem(int position) {
			try {
				return mItems.getJSONArray(position);
			}
			catch (final JSONException e) { e.printStackTrace();}
			return null;
		}

		public long getItemId(int position) {
			return position; //this still feels like a trick question.
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			
			//TODO: convertView ...
			
			final LinearLayout ll = new LinearLayout(mContext);

			
			final LinearLayout ll_text = new LinearLayout(mContext);
			ll_text.setOrientation(LinearLayout.VERTICAL);
			ll_text.setLayoutParams(new LinearLayout.LayoutParams(
					LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT,
					1.0f));

			final TextView title = new TextView(mContext);
			final TextView timecode_info = new TextView(mContext);
			
			try {
				title.setText(getItem(position).getString(1));
				timecode_info.setText("" + getItem(position).getLong(0) + "ms"); //TODO: formatting
				
				ll.setBackgroundColor(getItem(position).getBoolean(2) ? 0xff800080 : 0xff808080);
			}
			catch (final JSONException e1) { e1.printStackTrace(); }
			
			title.setTextSize(20);

			ll_text.addView(title);
			ll_text.addView(timecode_info);
			
			ll.addView(ll_text);
			
			final ImageView thumb = new ImageView(mContext);
			
			URL thumbnail_url;
			try {
				thumbnail_url = new URL(String.format(
						"http://mel-pydev.mit.edu/movid/api/get/%s/video/120x90r1.0/%06d.jpg",
						video_md5,
						Math.max(1, getItem(position).getLong(0) / 1000)));
			}
			catch (final MalformedURLException e) {e.printStackTrace(); return ll;}
			catch (final JSONException e) {e.printStackTrace(); return ll;}
			
			Log.d(TAG, "Trying to get annotation thumb at " + thumbnail_url);
			VideoEditActivity.webImageToImageView(thumbnail_url, thumb, mContext);
			ll.addView(thumb);
			
			return ll;
		}
	}


}
