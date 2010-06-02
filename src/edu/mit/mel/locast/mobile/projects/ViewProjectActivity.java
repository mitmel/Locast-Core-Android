	package edu.mit.mel.locast.mobile.projects;
	
	import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import com.commonsware.cwac.thumbnail.ThumbnailAdapter;
import com.rmozone.mobilevideo.VideoEditActivity;

import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.JsonSyncableItem;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.widget.TagListView;
	
	public class ViewProjectActivity extends Activity 
		implements OnClickListener, OnItemClickListener, OnCreateContextMenuListener {
		private Button mJoinButton;
		private TagListView tagList;
		protected List<Long> casts = new Vector<Long>();
		protected Set<String> members;
		
		private final static int MENU_ITEM_VIEW_CAST = 0,
								 MENU_ITEM_REMOVE_CAST = 1;
		
		private BaseAdapter castAdapter;
		private Cursor c;
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.projectdetails);
	        
	        //to be added: on-click listener for joining
	        mJoinButton = (Button) findViewById(R.id.project_join);
	        mJoinButton.setOnClickListener(this);
	        
	        tagList = ((TagListView)findViewById(R.id.tags));
	        
	        final Gallery g = (Gallery) findViewById(R.id.gallery);
	        
	        // this defines what images need to be loaded. URLs are placed in the ImageView tag
	        final int[] IMAGE_IDS = {R.id.thumbnail};
	        castAdapter = new ThumbnailAdapter(this, new ImageAdapter(this), ((Application)getApplication()).getImageCache(), IMAGE_IDS);
	        g.setAdapter(castAdapter);
	        g.setOnItemClickListener(this);
	        g.setOnCreateContextMenuListener(this);
	        
	        /////////// intent handling //////////////
	        final String action = getIntent().getAction();
	        
	        if (Intent.ACTION_VIEW.equals(action)){
	        	final Uri projectUri = getIntent().getData();
	        	c = managedQuery(projectUri, Project.PROJECTION, null, null, null);
	        	
	        	loadFirstFromCursor(projectUri, c);
	        	
	        	final ContentResolver cr = getContentResolver();
	        	cr.registerContentObserver(getIntent().getData(), false, new ContentObserver(new Handler()) {
	    			@Override
	    			public void onChange(boolean selfChange) {
	    				super.onChange(selfChange);
	    				if (!c.isClosed()){
		    				c.requery();
		    				loadFirstFromCursor(projectUri, c);
	    				}
	    			}
	    			@Override
	    			public boolean deliverSelfNotifications() {
	    				return true;
	    			}
	    		});
	        }
	    }
		
		protected void loadFirstFromCursor(Uri projectUri, Cursor c){
        	if (c.moveToFirst()){
        		loadFromCursor(projectUri, c);
        	}else{
        		Toast.makeText(this, getString(R.string.error_no_project, projectUri.toString()), Toast.LENGTH_LONG).show();
        		finish();
        	}
		}
		
		protected void loadFromCursor(Uri projectUri, Cursor c){
			((TextView)findViewById(R.id.item_title)).setText(c.getString(c.getColumnIndex(Project.TITLE)));
			((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Project.DESCRIPTION)));
			
			Calendar fromDate = null, toDate = null;
			if (! c.isNull(c.getColumnIndex(Project.START_DATE))){
				fromDate = Calendar.getInstance();
				fromDate.setTimeInMillis(c.getLong(c.getColumnIndex(Project.START_DATE)));
			}
			if (! c.isNull(c.getColumnIndex(Project.END_DATE))){
				toDate = Calendar.getInstance();
				toDate.setTimeInMillis(c.getLong(c.getColumnIndex(Project.END_DATE)));
			}
			final DateFormat df = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);
			final String dateString = ((fromDate != null) ? df.format(fromDate.getTime()) : "")
				+ (( fromDate != null && toDate != null) ? " - " : "")
				+ ((toDate != null) ? df.format(toDate.getTime()) : "");
			((TextView)findViewById(R.id.date)).setText(dateString);
			
			tagList.addTags(Project.getTags(getContentResolver(), projectUri));
			casts = Project.getListLong(c.getColumnIndex(Project.CASTS), c);
			if (casts.size() == 0){
				((TextView)findViewById(R.id.cast_notice)).setText(R.string.project_no_casts);
			}else{
				((TextView)findViewById(R.id.cast_notice)).setText("");
			}
			castAdapter.notifyDataSetChanged();
	
			members = new TreeSet<String>(Project.getList(c.getColumnIndex(Project.MEMBERS), c));
			final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(this);
			if (members.contains(nc.getUsername())){
				mJoinButton.setText(R.string.project_leave);
			}else{
				mJoinButton.setText(R.string.project_join);
			}
			((TextView)findViewById(R.id.people)).setText(Project.getMemberList(getApplicationContext(), c));
		}
		
		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
	        final MenuInflater inflater = getMenuInflater();
	        inflater.inflate(R.menu.projectsdetails_menu, menu);
	
	        if (c != null){
	        	final MenuItem editItem = menu.findItem(R.id.project_edit);
	        	editItem.setEnabled(Project.canEdit(c));
	        }
	        return true;
	    }
		
		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch (item.getItemId()) {
				case R.id.addCastItem: {
					startActivity(new Intent(EditProjectActivity.ACTION_ADD_CAST,
							getIntent().getData()));
					break;
				}
				case R.id.project_edit: {
					startActivity(new Intent(Intent.ACTION_EDIT, 
							getIntent().getData()));
					break;
				}
				case R.id.projectdetailmenu_videoedit: {
					//HOOK TO MOBILEVIDEO
					
					//get public id
					//String[] projection = {Project._ID,Project.PUBLIC_ID};
					//final Uri uri = getIntent().getData();
					final Cursor c = managedQuery(getIntent().getData(), Project.PROJECTION, null, null, null);
					//Cursor c = getContentResolver().query(uri, Project.PROJECTION, null, null, null);
					if (c.moveToFirst() ) {
						final long pubId = c.getLong(c.getColumnIndex(JsonSyncableItem.PUBLIC_ID));
						startActivity(new Intent(VideoEditActivity.ACTION_EDIT_CASTS_IN_PROJECT,
								ContentUris.withAppendedId(Project.CONTENT_URI, pubId))); // using public id
					}
					else {
						Toast.makeText(this, "Please sync to the server before editing this project", 3000); 
					}
					
					
					break;
				}
			}
			return super.onOptionsItemSelected(item);
		}
	    
		@Override
		public void onCreateContextMenu(ContextMenu menu, View v,
				ContextMenuInfo menuInfo) {
			menu.add(Menu.NONE, MENU_ITEM_VIEW_CAST,   Menu.NONE, R.string.view_cast);
			menu.add(Menu.NONE, MENU_ITEM_REMOVE_CAST, Menu.NONE, R.string.remove_cast);
			
		}
		
		@Override
		public boolean onContextItemSelected(MenuItem item) {
			final AdapterView.AdapterContextMenuInfo info;
			switch (item.getItemId()){
			case MENU_ITEM_VIEW_CAST:
				info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
				startActivity(new Intent(Intent.ACTION_VIEW, 
						ContentUris.withAppendedId(Cast.CONTENT_URI, info.id)));
				break;
				
			case MENU_ITEM_REMOVE_CAST:
				info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
				casts.remove(info.position);
				updateCasts();
				break;
				
			default:
				return false;
			}
			return true;
		}
		
	    public class ImageAdapter extends BaseAdapter {
	        int mGalleryItemBackground;
	        final LayoutInflater inflater = getLayoutInflater();
	        
	        public ImageAdapter(Context c) {
	            final TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
	            mGalleryItemBackground = a.getResourceId(
	                    R.styleable.Gallery1_android_galleryItemBackground, 0);
	            a.recycle();
	        }
	
	        public int getCount() {
	            return casts.size();
	        }
	
	        public Object getItem(int position) {
	            return position;
	        }
	
	        public long getItemId(int position) {
	            return casts.get(position);
	        }
	
	        public View getView(int position, View convertView, ViewGroup parent) {
	            
	        	ImageView i;
	            if (convertView != null){
	            	i = (ImageView) convertView;
	            }else {
	            	i = (ImageView)inflater.inflate(R.layout.thumbimage, parent, false);
	                
	                // The preferred Gallery item background
	                i.setBackgroundResource(mGalleryItemBackground);
	            }
	            final ContentResolver cr = getContentResolver();
	            final Cursor c = cr.query(ContentUris.withAppendedId(Cast.CONTENT_URI, getItemId(position)), Cast.PROJECTION, null, null, null);
	            if (c.moveToFirst()){
	            	
	            	i.setTag(c.getString(c.getColumnIndex(Cast.THUMBNAIL_URI)));
	            	i.setImageResource(R.drawable.cast_placeholder);
	            }
	            c.close();
	            
	            return i;
	        }
	    }
	    
	    private void updateCasts(){
			final ContentValues cv = new ContentValues();
			Project.putList(Project.CASTS, cv, casts);
			getContentResolver().update(getIntent().getData(), cv, null, null);
	    }
	
		public void onClick(View v) {
			switch (v.getId()){
			case R.id.project_join:
				startActivity(new Intent(EditProjectActivity.ACTION_TOGGLE_MEMBERSHIP, 
						getIntent().getData()));
				break;
				
			}
			
		}
	
		public void onItemClick(AdapterView<?> adapter, View v, int position, long itemId) {
			switch (adapter.getId()){
			case R.id.gallery:
				startActivity(new Intent(Intent.ACTION_VIEW, 
						ContentUris.withAppendedId(Cast.CONTENT_URI, itemId)));
			}		 
		}
	}
	

