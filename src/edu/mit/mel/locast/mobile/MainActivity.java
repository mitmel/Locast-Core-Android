package edu.mit.mel.locast.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import edu.mit.mel.locast.mobile.casts.BrowseCastsActivity;
import edu.mit.mel.locast.mobile.casts.EditCastActivity;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Comment;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.Tag;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

/**
 * Main Menu. Also will handle pairing if there are no credentials stored.
 * 
 * @author stevep
 *
 */
public class MainActivity extends Activity implements OnClickListener, OnEditorActionListener {
	
	AndroidNetworkClient nc;
	// There's some strange quirk with the network client where it 
	// caches credentials in a way that it probably shouldn't. This will
	// reset it after pairing. See https://mel-internal.mit.edu/trac/rai/ticket/432
	boolean needToResetNc = false; 

    SharedPreferences prefs;
    final static boolean DEBUG = true;
    
	final static int INTENT_SCAN_QRCODE = 1;
	
	private static final int 
		ACTIVITY_RECORD_SOUND = 1, 
		ACTIVITY_RECORD_VIDEO = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        this.nc = AndroidNetworkClient.getInstance(this);
        

    }
    
	@Override
	protected void onStart() {
		if (needToResetNc){
			nc = new AndroidNetworkClient(this);
		}
        if (!nc.isPaired()){
    		setContentView(R.layout.pairing);
    		findViewById(R.id.ScanQRCodeButton).setOnClickListener(this);
    		findViewById(R.id.PairingButton).setOnClickListener(this);
    		((EditText)findViewById(R.id.PairingText)).setOnEditorActionListener(this);
    		needToResetNc = true;

        }else{
	        setContentView(R.layout.main);
	        findViewById(R.id.browse_button).setOnClickListener(this);
	        findViewById(R.id.projects_button).setOnClickListener(this);
	
	        findViewById(R.id.recordvideo_button).setOnClickListener(this);
	        findViewById(R.id.recordaudio_button).setOnClickListener(this);
	        findViewById(R.id.sync_button).setOnClickListener(this);
	        //findViewById(R.id.notifications_button).setOnClickListener(this);
        }
        
	    if (nc.isPaired()){
	    	((TextView)findViewById(R.id.header_subtitle)).setText("Logged in as " + nc.getUsername());
			
        }
	    
	    new TestNetworkTask().execute();
	    super.onStart();
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
		Intent intent;
		
		switch (v.getId()) {
		
		case R.id.projects_button: 
			startActivity(new Intent(Intent.ACTION_VIEW, Project.CONTENT_URI));
			break;
			
		case R.id.recordvideo_button: 
			//Toast.makeText(this, "Recording video...", Toast.LENGTH_SHORT).show();
			intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
			startActivityForResult(intent, ACTIVITY_RECORD_VIDEO);
			break;
			
		case R.id.recordaudio_button: 
			//http://www.openintents.org/en/node/114
			intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
			startActivityForResult(intent, ACTIVITY_RECORD_SOUND);
			//
            //Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            //intent.setType("audio/*");
            //startActivity(Intent.createChooser(intent, "Select music"));
			
			break;
		case R.id.browse_button:
			startActivity(new Intent(this, BrowseCastsActivity.class));
			break;
		case R.id.sync_button:
			startSync();
			break;
			/*
		case R.id.sync_cancel_button:
			startService(new Intent(Sync.ACTION_CANCEL_SYNC, Uri.EMPTY));
			break;*/
			
		case R.id.ScanQRCodeButton:
			IntentIntegrator.initiateScan(this);
			break;

		case R.id.PairingButton:

			pair(((EditText) findViewById(R.id.PairingText)).getText()
					.toString());

			break;
	
		}
	}
	
	private void pair(String code) {
		try {
			nc.pairDevice(code);
			if (nc.isPaired()) {
				startActivity(new Intent(this, MainActivity.class));
			}
		} catch (final Exception e) {
			e.printStackTrace();
			Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
		}
	}

	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		switch (v.getId()){
		case R.id.PairingText:
			pair(((EditText) findViewById(R.id.PairingText)).getText()
					.toString());
			break;
		}
		return false;
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
			
		case IntentIntegrator.REQUEST_CODE:
			final IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
			if (scanResult != null){
				final String format = scanResult.getFormatName();
				// Handle successful scan
				if (format == null){
					// no barcode scanner installed. It'll pop up a install request.
				}else if (format.equals("QR_CODE")) {
					pair(scanResult.getContents());
				} else {
					Toast.makeText(this,
							"Scanned barcode does not seem to be a QRCode",
							Toast.LENGTH_LONG).show();				
				}

			}else{
				switch (requestCode) {
				// other intents here
		
				} // switch requestCode
			}
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