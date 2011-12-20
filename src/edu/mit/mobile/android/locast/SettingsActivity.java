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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.ver2.R;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener, OnPreferenceChangeListener {

	private static final int REQUEST_RESET_DB = 0;
	private static final String TAG = SettingsActivity.class.getSimpleName();
	private String newUrl;

	private PreferenceScreen preferenceScreen;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		addPreferencesFromResource(R.xml.preferences);
		preferenceScreen = getPreferenceScreen();
	}

	@Override
	protected void onResume() {
		super.onResume();
		preferenceScreen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		preferenceScreen.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	private void setUrl(String newUrl){
		final Editor editor = preferenceScreen.getSharedPreferences().edit();
		editor.putString(NetworkClient.PREF_SERVER_URL, newUrl);
		editor.commit();
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode){
		case REQUEST_RESET_DB:
			if (resultCode == RESULT_OK){
				setUrl(newUrl);
				finish();
			}else if (resultCode == RESULT_CANCELED){
				// reset the URL to the currently entered one
				final ListPreference pref = (ListPreference) preferenceScreen.findPreference(NetworkClient.PREF_LOCAST_SITE);
				if (pref != null){

					pref.setValue(preferenceScreen.getSharedPreferences().getString(NetworkClient.PREF_SERVER_URL, null));
				}
			}
			break;

			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(NetworkClient.PREF_LOCAST_SITE)){

			final String newUrl = sharedPreferences.getString(NetworkClient.PREF_LOCAST_SITE, null);
			// only do this if something's actually changed.
			if (newUrl != null && !newUrl.equals(sharedPreferences.getString(NetworkClient.PREF_SERVER_URL, null))){
				setUrl(newUrl);
				// TODO this needs to ensure that the prefereces are up to date
			}
		}
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		final String key = preference.getKey();
		if (key.equals(NetworkClient.PREF_SERVER_URL)) {
			String baseurl = (String) newValue;
			if (!baseurl.endsWith("/")) {
				if (Constants.DEBUG) {
					Log.w(TAG, "Baseurl in preferences (" + baseurl
							+ ") didn't end in a slash, so we added one.");
				}
				baseurl = baseurl + "/";
			}
		}
		return true;
	}
}
