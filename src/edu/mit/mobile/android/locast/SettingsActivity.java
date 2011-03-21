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

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import edu.mit.mobile.android.locast.net.AndroidNetworkClient;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener, OnPreferenceChangeListener {

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

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(AndroidNetworkClient.PREF_LOCAST_SITE)){

			final String newUrl = sharedPreferences.getString(AndroidNetworkClient.PREF_LOCAST_SITE, null);
			// only do this if something's actually changed.
			if (newUrl != null && !newUrl.equals(sharedPreferences.getString(AndroidNetworkClient.PREF_SERVER_URL, null))){
				MainActivity.resetDB(this);
				final Editor editor = sharedPreferences.edit();
				editor.putString(AndroidNetworkClient.PREF_USERNAME, "");
				editor.putString(AndroidNetworkClient.PREF_PASSWORD, "");
				editor.putString(AndroidNetworkClient.PREF_SERVER_URL, newUrl);
				editor.commit();
				finish();
			}
		}
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		// TODO Auto-generated method stub
		return false;
	}
}
