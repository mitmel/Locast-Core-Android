package edu.mit.mobile.android.locast.widget;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import edu.mit.mobile.android.locast.net.NetworkClient;

public class RemoteTagsAdapter extends ArrayAdapter<String>{
	private final static long cacheAge = 60 * 1000;
	private long lastUpdated = 0;
	private boolean updating = false;

	final ConnectivityManager cm;
    private final NetworkClient mNc;

    public RemoteTagsAdapter(Context context, NetworkClient nc, int textViewResourceId) {
		super(context, textViewResourceId);
    	cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mNc = nc;
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
                final List<String> tags = mNc.getTagsList();
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
