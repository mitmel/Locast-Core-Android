package edu.mit.mel.locast.mobile.widget;
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
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

public class RemoteTagsAdapter extends ArrayAdapter<String>{
	private final static long cacheAge = 60 * 1000;
	private long lastUpdated = 0;
	private boolean updating = false;

	public RemoteTagsAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}
	
    public void refreshTags(){
		if (!updating && (lastUpdated == 0 || (new Date().getTime() - lastUpdated > cacheAge))){
			new TagLoaderTask().execute();
		}
    }
	
	public class TagLoaderTask extends AsyncTask<Void, Integer, List<String>>{

		@Override
		protected void onPreExecute() {
			updating = true;
		}
		@Override
		protected List<String> doInBackground(Void... params) {
			
			try {
				final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(getContext());
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
