package com.rmozone.mobilevideo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.VideoView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.rmozone.mobilevideo.AnnotationDBKey.Annotation;

import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.StreamUtils;

public class AnnotationActivity extends Activity implements OnTouchListener, OnClickListener, OnSeekBarChangeListener, OnItemClickListener, OnItemLongClickListener {
	public static final String ACTION_ANNOTATE_CAST_FROM_MEDIA_URI = "com.rmozone.mobilevideo.ACTION_ANNOTATE_CAST_FROM_MEDIA_URI";
	public static final String ACTION_ANNOTATE_CAST_FROM_LOCAST_ID = "ACTION_ANNOTATE_CAST_FROM_LOCAST_ID";
	
	final String TAG = "AnnotationActivity";
	
	Cursor c;

	VideoView video;
	SeekBar seek;
	LinearLayout controls;
	
	ListView annotations;
	String video_md5;
	
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//make fullscreen (http://www.androidsnippets.org/snippets/27/)
		requestWindowFeature(Window.FEATURE_NO_TITLE);  
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
				 WindowManager.LayoutParams.FLAG_FULLSCREEN);  		
		
		setContentView(R.layout.annotationview);
		
		//find widget components
		this.video = (VideoView) findViewById(R.id.VideoView);
		this.seek = (SeekBar) findViewById(R.id.SeekBar);
		
		this.controls = (LinearLayout) findViewById(R.id.annotation_Line01);
		
		this.annotations = (ListView) findViewById(R.id.annotation_list);
		
		//bind controls to actions
		findViewById(R.id.annotation_Done).setOnClickListener(this);
		findViewById(R.id.annotation_New).setOnClickListener(this);
		findViewById(R.id.annotation_Play).setOnClickListener(this);
		
		this.seek.setOnSeekBarChangeListener(this);
						
		final Intent i = getIntent();
        Uri data = i.getData();
        final String action = i.getAction();
        //final String type = i.getType();
        
        if(data != null) {

        	//track down annotations for this video & set adapter
        	
        	if(ACTION_ANNOTATE_CAST_FROM_LOCAST_ID.equals(action)) {
        		// figure out md5 from server ...
        		Log.d(TAG, "from a locast id...");
        		
        		final String[] pieces = data.getPath().split("/"); // the last two are like /id/43
        		final int c_id = Integer.parseInt(pieces[pieces.length-1]);
        		
        		video_md5 = this.castIdToMd5(c_id);
        		
        		//remove the last two items from the URI so that we can play the video again
        		data = Uri.parse(data.toString().substring(0, data.toString().indexOf("/id/")));
        	}
        	else {
        		//compute md5
        		video_md5 = MD5Sum.checksum(data, getApplicationContext());
        	}

    		System.out.println("video md5sum is: " + video_md5);
    		
    		AnnotationActivity.getAnnotationsFromServer(video_md5, getContentResolver());
    		
    		c = Annotation.md5ToCursor(this, video_md5);
    		final ListAdapter adapter = Annotation.getAnnotationAdapter(this, c);
    		
    		this.annotations.setAdapter(adapter);
    		
    		//register events for clicking on items
    		this.annotations.setOnItemClickListener(this);
    		this.annotations.setOnItemLongClickListener(this);
    		
    		this.video.setVideoURI(data);
    		
    		this.video.setOnPreparedListener(new OnPreparedListener() {

				public void onPrepared(MediaPlayer mp) {
					//hide controls when we start playing
					video.start();  //make sure we're playing
					controls.setVisibility(View.INVISIBLE);
				}
    		});
    		
    		this.video.setOnCompletionListener(new OnCompletionListener() {
				public void onCompletion(MediaPlayer mp) {
					//show controls at end of video
					seek.setProgress(seek.getMax());
					controls.setVisibility(View.VISIBLE);
				}
			});
    		
        	//make a callback to play/pause the video on-tap
        	this.video.setOnTouchListener(this);     
        }

	}
	
    private String castIdToMd5(int cId) {
		final String url = "http://mel-pydev.mit.edu:8090/api/mobilevideo/id2md5/" + cId;
    	
		final HttpClient httpclient = new DefaultHttpClient();
		final HttpGet httpget = new HttpGet(url);
		
		HttpResponse response;
		
		try {
			response = httpclient.execute(httpget);
		}
		catch (final ClientProtocolException e) {e.printStackTrace();	return null;}
		catch (final IOException e) {e.printStackTrace();return null;}
		
		if(response != null && response.getStatusLine().getStatusCode() == 200) {
			//we probably got something
			String video_md5;
			try {
				video_md5 = StreamUtils.inputStreamToString(response.getEntity().getContent());
				Log.d(TAG, "Got some md5 from server: " + video_md5);
				return video_md5;
			}
			catch (final IllegalStateException e) { e.printStackTrace(); return null;}
			catch (final IOException e) { e.printStackTrace(); return null;}
		}
		return "";

	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	final MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.annotation_menu, menu);
    	return true;
     }
    
    public static boolean resetDBFromJSON(String json_annotations, String video_md5, ContentResolver cr) {
    	
    	//wipe the DB for this video
    	cr.delete(Annotation.CONTENT_URI,
    			Annotation.VIDEO_UID + "='" + video_md5 + "'",
    			null);
    	
    	//put in these new datas
    	JSONArray annotations;
    	try {
			annotations = new JSONArray(json_annotations);
			
			for(int i=0; i<annotations.length(); i++) {
				final JSONArray ann = annotations.getJSONArray(i);
				
				final ContentValues values = new ContentValues();
				values.put(Annotation.VIDEO_UID, video_md5);
				values.put(Annotation.ANNOTATION_TIME, ann.getInt(0));
				values.put(Annotation.ANNOTATION, ann.getString(1));

				cr.insert(Annotation.CONTENT_URI, values);
			}

		} catch (final JSONException e) {
			// JSON interpretation failed.
			e.printStackTrace();
			return false;
		}
    	return true;
    	

    }
    

    
    public static boolean getAnnotationsFromServer(String video_md5, ContentResolver cr) {
    	final HttpClient httpclient = new DefaultHttpClient();
    	final HttpGet httpget = new HttpGet("http://mel-pydev.mit.edu/movid/api/get/" + video_md5 + "/annotations.json");
    	
    	HttpResponse response;
    	
    	try {
			response = httpclient.execute(httpget);
		} catch (final ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		if(response != null && response.getStatusLine().getStatusCode() == 200) {
			//things seem ok.
			String new_annotations;
			try {
				new_annotations = StreamUtils.inputStreamToString(response.getEntity().getContent());
			} catch (final IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
			//Log.d(TAG, "So ... we have our response");
			resetDBFromJSON(new_annotations, video_md5, cr);
			return true;
		} else
			return false;
		

		
		//TODO: wipe DB & reset w/new vals
    	
    }
    
    public boolean sendAnnotationsToServer() {
    	final HttpClient httpclient = new DefaultHttpClient();
    	final HttpPost httppost = new HttpPost("http://mel-pydev.mit.edu:8090/api/mobilevideo/annotations/post");
    	
    	final List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
    	nameValuePairs.add(new BasicNameValuePair("annotations", annotationsToJSON(this.c).toString())); //"[[0, \"START\"], [25435, \"END\"]]"));
    	nameValuePairs.add(new BasicNameValuePair("md5", video_md5));
    	
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
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.annotation_menu_sync:
    		Log.w(TAG, "Trying to sync data");
    		
    		// 1. Send current annotations
    		if(this.sendAnnotationsToServer()) {
    			// 2. Get the merged version from the server
    			AnnotationActivity.getAnnotationsFromServer(video_md5, getContentResolver());
    		}
    		
    		break;
    	}
    	return false;
    }
	

	
	public boolean onTouch(View v, MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_UP) {
			this.playpause();
		}
		return true;
	}
	
	public void playpause() {
		if(this.video.isPlaying()) {
			this.video.pause();
			
			//update seekbar
	    	this.seek.setMax(this.video.getDuration());    	
			this.seek.setProgress(this.video.getCurrentPosition());
			

			
			this.controls.setVisibility(View.VISIBLE);			
		}
		else {
			this.video.start();
			this.controls.setVisibility(View.INVISIBLE);

		}
	}

	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.annotation_Done:
			this.sendAnnotationsToServer();
			finish();
			break;
			
		case R.id.annotation_Play:
			this.playpause();
			break;
			
		case R.id.annotation_New:
			//create a blank, new annotation at the current location
			this.doEditAnnotation(this.video.getCurrentPosition(), "", -1);
			break;
		}
	}

	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {}

	public void onStartTrackingTouch(SeekBar seekBar) {	}

	public void onStopTrackingTouch(SeekBar seekBar) {
		this.seekVideoTo(this.seek.getProgress());
	}
	
	private void seekVideoTo(int pos) {
		/*
		 * experimentally, it seems the best way to nudge a video to refresh 
		 * is to start playing, seek half a second back, and then pause
		 * half a second later.
		 */
		
		
		this.video.start();
		
		final int dt = Math.min(500, pos);
		this.video.seekTo(pos - dt);
		
		//schedule pause
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				video.pause();
			}
		}, dt);

		//update seek progress
		this.seek.setProgress(pos);
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id){
		//on click, seek to the location of the annotation
		
		final Cursor c = (Cursor) parent.getItemAtPosition(position);
		
		final int time = c.getInt(c.getColumnIndex(Annotation.ANNOTATION_TIME));
		
		this.seekVideoTo(time);
	}

	public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
			long id) {

		final Cursor c = (Cursor) parent.getItemAtPosition(position);

		final int _id = c.getInt(c.getColumnIndex(Annotation._ID));
		final int time = c.getInt(c.getColumnIndex(Annotation.ANNOTATION_TIME));
		final String annotation = c.getString(c.getColumnIndex(Annotation.ANNOTATION));
		
		this.doEditAnnotation(time, annotation, _id);
		return true;
	}
	
	public void doEditAnnotation(final int time, String annotation, final int _id) {
		
		// AlertDialog
		// based on: http://www.androidsnippets.org/snippets/20/
		
		   final AlertDialog.Builder alert = new AlertDialog.Builder(this);  
		   alert.setTitle("Edit Annotation");  
		   alert.setMessage("Setting annotation at " + time);  

		   // Set an EditText view to get user input   
		   final EditText input = new EditText(this);  
		   input.setText(annotation);
		   alert.setView(input);  
		      
		   alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {  
			   public void onClick(DialogInterface dialog, int whichButton) {  
				   final String newAnnotation= input.getText().toString();
				   
					//update item based on new info
					final ContentValues values = new ContentValues();
					values.put(Annotation.VIDEO_UID, video_md5);
					values.put(Annotation.ANNOTATION_TIME, time);
					values.put(Annotation.ANNOTATION, newAnnotation);
					
					if(_id > 0) {
						Log.d(TAG, "Editing an existing annotation");
						getContentResolver().update(Annotation.CONTENT_URI,
								values,
								Annotation._ID + "=" + _id,
								null);
					}
					else {
						Log.d(TAG, "Creating a new annotation");
						getContentResolver().insert(Annotation.CONTENT_URI, values);
					}
			   }  
		   });  
		   alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {  
			   public void onClick(DialogInterface dialog, int whichButton) {  
				   // do nothing ...  
			   }  
		   });  
		  alert.show();  
		
	}

	/*
     * Grab a cursor and wrap up the database into a JSON string. 
     */
    public static JSONArray annotationsToJSON(Cursor c) {
    	//cursor

    	c.moveToFirst();
    	
		final JSONArray annotations = new JSONArray();
		
		while(!c.isAfterLast()) {
			final int time = c.getInt(c.getColumnIndex(Annotation.ANNOTATION_TIME));
			final String ann = c.getString(c.getColumnIndex(Annotation.ANNOTATION));
			
			final JSONArray pairing = new JSONArray();
			pairing.put(time);
			pairing.put(ann);
			
			annotations.put(pairing);
			c.moveToNext();
		}
		
    	return annotations;
    }

}