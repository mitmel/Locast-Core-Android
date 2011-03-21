package edu.mit.mobile.android.locast;
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
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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

import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Comment;
import edu.mit.mobile.android.locast.data.Project;
import edu.mit.mobile.android.locast.data.Tag;
import edu.mit.mobile.android.locast.net.AndroidNetworkClient;

public class PairingActivity extends Activity implements OnClickListener, OnEditorActionListener {

	private AndroidNetworkClient nc;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.nc = AndroidNetworkClient.getInstance(this);

		setContentView(R.layout.pairing);

		findViewById(R.id.ScanQRCodeButton).setOnClickListener(this);
		findViewById(R.id.PairingButton).setOnClickListener(this);
		((EditText)findViewById(R.id.PairingText)).setOnEditorActionListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.homescreen_menu, menu);
        if (MainActivity.DEBUG){
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

			case R.id.reset: {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.app_name);
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
						Toast.makeText(getApplicationContext(), R.string.notice_databases_reset, Toast.LENGTH_LONG).show();
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
		switch (v.getId()) {

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
				finish();
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
			return true;
		}
		return false;
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode){

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

}
