package edu.mit.mel.locast.mobile;
/*
 * Copyright (C) 2010 MIT Mobile Experience Lab
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
import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.TabHost;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.casts.BrowseCastsActivity;
import edu.mit.mel.locast.mobile.casts.EditCastActivity;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Comment;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.Tag;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.projects.ListProjectsActivity;

/**
 * Main Menu. Also will handle pairing if there are no credentials stored.
 * 
 * @author stevep
 *
 */
public class MainActivity extends TabActivity implements OnClickListener {
	
	private AndroidNetworkClient nc;
	// There's some strange quirk with the network client where it 
	// caches credentials in a way that it probably shouldn't. This will
	// reset it after pairing. See https://mel-internal.mit.edu/trac/rai/ticket/432
	private final boolean needToResetNc = false; 

    private SharedPreferences prefs;
    final static boolean DEBUG = true;
	
	private static final int 
		ACTIVITY_RECORD_SOUND = 1, 
		ACTIVITY_RECORD_VIDEO = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        this.nc = AndroidNetworkClient.getInstance(this);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        
        setContentView(R.layout.main);
        
        final TabHost tabHost = getTabHost();
        
        tabHost.addTab(tabHost.newTabSpec("projects")
        		.setIndicator(getString(R.string.tab_projects)).setContent(new Intent(this, ListProjectsActivity.class)));
        
        tabHost.addTab(tabHost.newTabSpec("casts")
        		.setIndicator(getString(R.string.tab_casts)).setContent(new Intent(this, BrowseCastsActivity.class)));
    }
    
	@Override
	protected void onPostCreate(Bundle icicle) {
		super.onPostCreate(icicle);
		// the icon is set here, due it needing to be called after setContentView()
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon);
	}
	
	@Override
	protected void onStart() {
	    super.onStart();
		if (needToResetNc){
			nc = new AndroidNetworkClient(this);
		}
        if (!nc.isPaired()){
        	startActivity(new Intent(this, PairingActivity.class));
        	finish();
        	return;
        }
        
	    if (nc.isPaired()){
	    	//((TextView)findViewById(R.id.header_subtitle)).setText("Logged in as " + nc.getUsername());
			
        }
	    
	    new TestNetworkTask().execute();

	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.homescreen_menu, menu);
        if (DEBUG){
        	menu.findItem(R.id.reset).setVisible(true);
        }
        return true;
    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.settingsMenuItem: {
				final Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				break;
			}
			case R.id.aboutMenuItem: {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Locast Civic Media");
				builder.setMessage("This is Locast Civic Media, a democratic Media Platform developed by the Mobile Experience Lab at the MIT.");
				//builder.setPositiveButton(R.string.button_open_browser, mAboutListener);
				//builder.setNegativeButton(R.string.button_cancel, null);
				builder.show();
				break;
			}
			case R.id.upgrade: {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://locast.mit.edu/civic/static/LocastMobileAndroid.apk")));
				break;
			}
			
			case R.id.quit:
				android.os.Process.killProcess(android.os.Process.myPid());
				break;
				
			case R.id.reset: {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Locast Civic Media");
				builder.setMessage("This will erase all the Locast information stored on the device, aside from the video files themselves. Do you want to reset Locast?");
				builder.setPositiveButton("Reset databases", new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int which) {
						// reset preferences
						/*final Editor e = prefs.edit();
						e.clear();
						e.commit();*/
						
						// reset DBs
						final ContentResolver cr = getApplication().getContentResolver();
						cr.delete(Cast.CONTENT_URI, null, null);
						cr.delete(Project.CONTENT_URI, null, null);
						cr.delete(Comment.CONTENT_URI, null, null);
						cr.delete(Tag.CONTENT_URI, null, null);
						Toast.makeText(getApplicationContext(), "Databases reset.", Toast.LENGTH_LONG).show();
						android.os.Process.killProcess(android.os.Process.myPid());
					}
				});
				
				builder.setNegativeButton(android.R.string.cancel, null);
				builder.show();
				break;
			}
			
			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}

	public void onClick(View v) {
		final Intent intent;
		
		switch (v.getId()) {
			
	
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		switch (requestCode){
		
		case ACTIVITY_RECORD_SOUND:
		case ACTIVITY_RECORD_VIDEO:
			
			switch (resultCode){
			
			case RESULT_OK:
				startActivity(new Intent(
						EditCastActivity.ACTION_CAST_FROM_MEDIA_URI,
						data.getData()));
				break;
				
			case RESULT_CANCELED:
				Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show();
				break;
			} // switch resultCode
			break;
			
		} // switch requestCode
	}

	public void startSync(){
		startService(new Intent(Intent.ACTION_SYNC, Cast.CONTENT_URI));
		startService(new Intent(Intent.ACTION_SYNC, Project.CONTENT_URI));
	}
	
	private class TestNetworkTask extends AsyncTask<Void, Void, Boolean>{
		@Override
		protected Boolean doInBackground(Void... params) {
			return nc.isPaired() && nc.isConnectionWorking();
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			 final Cursor content = managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, null, null, null);
			    if (content.getCount() == 0){
			    	Toast.makeText(getApplicationContext(), getString(R.string.sync_first), Toast.LENGTH_LONG);
			    	startSync();
			    }
			    content.close();
		}
	}
	
	
}