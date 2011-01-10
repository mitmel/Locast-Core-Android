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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TabHost;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.casts.BrowseCastsActivity;
import edu.mit.mel.locast.mobile.casts.EditCastActivity;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.CastMedia;
import edu.mit.mel.locast.mobile.data.Comment;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.ShotList;
import edu.mit.mel.locast.mobile.data.Sync;
import edu.mit.mel.locast.mobile.data.Tag;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.projects.ListProjectsActivity;

/**
 * Main Menu. Also will handle pairing if there are no credentials stored.
 *
 * @author stevep
 *
 */
public class MainActivity extends TabActivity {

	private AndroidNetworkClient nc;
	// There's some strange quirk with the network client where it
	// caches credentials in a way that it probably shouldn't. This will
	// reset it after pairing. See https://mel-internal.mit.edu/trac/rai/ticket/432
	private final boolean needToResetNc = false;

	private AppUpdateChecker updateChecker;

    final static boolean DEBUG = true;

	private static final int
		ACTIVITY_RECORD_SOUND = 1,
		ACTIVITY_RECORD_VIDEO = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.nc = AndroidNetworkClient.getInstance(this);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);

        setContentView(R.layout.main);

		if (needToResetNc){
			nc = AndroidNetworkClient.getInstance(this);
		}
        if (!nc.isPaired()){
        	startActivity(new Intent(this, PairingActivity.class));
        	finish();
        	return;
        }

        final TabHost tabHost = getTabHost();

        final Resources r = getResources();
        tabHost.addTab(tabHost.newTabSpec("projects")
        		.setIndicator(getString(R.string.tab_projects), r.getDrawable(R.drawable.icon_projects))
        		.setContent(new Intent(Intent.ACTION_VIEW, Project.CONTENT_URI,
        				this, ListProjectsActivity.class)));

        tabHost.addTab(tabHost.newTabSpec("casts")
        		.setIndicator(getString(R.string.tab_casts), r.getDrawable(R.drawable.icon_casts))
        		.setContent(new Intent(Intent.ACTION_VIEW, Cast.CONTENT_URI,
        						this, BrowseCastsActivity.class)));

        updateChecker = new AppUpdateChecker(this, getString(R.string.app_update_url),
        		new AppUpdateChecker.OnUpdateDialog(this, getText(R.string.app_name)));

        updateChecker.checkForUpdates();
    }

	@Override
	protected void onPostCreate(Bundle icicle) {
		super.onPostCreate(icicle);
		// the icon is set here, due it needing to be called after setContentView()
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.app_icon);
	}

	@Override
	protected void onStart() {
	    super.onStart();

		if (needToResetNc){
			nc = AndroidNetworkClient.getInstance(this);
		}

	    if (nc.isPaired()){
	    	//((TextView)findViewById(R.id.header_subtitle)).setText("Logged in as " + nc.getUsername());

        }

	    new TestNetworkTask().execute();

	}

	public boolean myonOptionsItemSelected(MenuItem item) {
		Log.d("MainActivity", "onOptionsItemSelected");
		switch (item.getItemId()) {
			case R.id.settingsMenuItem: {
				final Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				break;
			}

			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
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

	public static void resetDB(final Context context){
		context.startService(new Intent(Sync.ACTION_CANCEL_SYNC, null, context, Sync.class));
		// reset DBs
		final ContentResolver cr = context.getContentResolver();
		cr.delete(Cast.CONTENT_URI, null, null);
		cr.delete(Project.CONTENT_URI, null, null);
		cr.delete(Comment.CONTENT_URI, null, null);
		cr.delete(Tag.CONTENT_URI, null, null);
		cr.delete(CastMedia.CONTENT_URI, null, null);
		cr.delete(ShotList.CONTENT_URI, null, null);
		Toast.makeText(context.getApplicationContext(), "Databases reset.", Toast.LENGTH_LONG).show();
	}

	public static void resetDBWithConfirmation(final Activity context){
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.app_name);
		builder.setMessage("This will erase all the Locast information stored on the device, aside from the video files themselves. Do you want to reset Locast?");
		builder.setPositiveButton("Reset databases", new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				// reset preferences
				/*final Editor e = prefs.edit();
				e.clear();
				e.commit();*/
				resetDB(context);

				context.finish();
			}
		});

		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	public void startSync(){

		startService(new Intent(Intent.ACTION_SYNC, Project.CONTENT_URI));
		startService(new Intent(Intent.ACTION_SYNC, Cast.CONTENT_URI));

	}

	private class TestNetworkTask extends AsyncTask<Void, Void, Boolean>{
		@Override
		protected Boolean doInBackground(Void... params) {
			return nc.isPaired() && nc.isConnectionWorking();
		}

		@Override
		protected void onPostExecute(Boolean hasNetwork) {
			 final Cursor content = managedQuery(Cast.CONTENT_URI, Cast.PROJECTION, null, null, null);
			    if (hasNetwork && content.getCount() == 0){
			    	Toast.makeText(getApplicationContext(), getString(R.string.sync_first), Toast.LENGTH_LONG).show();
			    	startSync();
			    }
			    content.close();
		}
	}
}