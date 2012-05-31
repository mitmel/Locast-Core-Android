package edu.mit.mobile.android.locast.widget;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
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
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.net.NetworkClient;

public class RemoteTagsAdapter extends ArrayAdapter<String>{
	private final static long cacheAge = 60 * 1000;
	private long lastUpdated = 0;
	private boolean updating = false;

	final ConnectivityManager cm;

	public RemoteTagsAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
    	cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

    public void refreshTags(){

    	final NetworkInfo activeNet = cm.getActiveNetworkInfo();
    	final boolean hasNetConnection = activeNet != null && activeNet.isConnected();
		if (hasNetConnection && !updating && (lastUpdated == 0 || (new Date().getTime() - lastUpdated > cacheAge))){
			new TagLoaderTask().execute();
		}
    }

	private class TagLoaderTask extends AsyncTask<Void, Integer, List<String>>{

		@Override
		protected void onPreExecute() {
			updating = true;
		}
		@Override
		protected List<String> doInBackground(Void... params) {

			try {
				final Context context = getContext();
				final NetworkClient nc = NetworkClient.getInstance(context,
						Authenticator.getFirstAccount(context));
				final List<String> tags =  nc.getTagsList();
				lastUpdated = new Date().getTime();
				return tags;

			} catch (final Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(List<String> result) {
			if (result != null){
				// bulk load the items into the array
				RemoteTagsAdapter.this.setNotifyOnChange(false);
				RemoteTagsAdapter.this.clear();
				for (final String tag: result){
					RemoteTagsAdapter.this.add(tag);
				}
				RemoteTagsAdapter.this.notifyDataSetChanged();
				RemoteTagsAdapter.this.setNotifyOnChange(true);
			}
			updating = false;
		}
	}

}
